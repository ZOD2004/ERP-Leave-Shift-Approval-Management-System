package com.murali.service;

import com.murali.dto.DailyExpectedShift;
import com.murali.entity.*;
import com.murali.entity.enums.AttendanceStatus;
import com.murali.entity.enums.LeaveSession;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCronJobService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final LeaveBalanceService leaveBalanceService;
    private final ScheduleCalculationService scheduleCalculationService;

    private LocalDateTime lastRunTime;
    private String lastRunStatus = "WAITING";

    @Scheduled(cron = "0 16 12 * * ?")
    @Transactional
    public void attendanceRunner() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        List<LocalDate> datesToCheck = List.of(yesterday, today);

        try {
            this.lastRunStatus = "RUNNING";
            log.info("Starting Batch Schedule Sweeper at {}", now);

            List<Employee> activeEmployees = employeeRepository.findByActiveTrue();
            List<Long> empIds = activeEmployees.stream().map(Employee::getId).toList();

            Map<Long, List<DailyExpectedShift>> expectedSchedules = scheduleCalculationService.calculateBatchShifts(activeEmployees, yesterday, today);

            List<Attendance> attendances = new ArrayList<>();
            for (LocalDate date : datesToCheck) {
                attendances.addAll(attendanceRepository.findByEmployeeIdInAndAttendanceDate(empIds, date));
            }
            Map<Long, Map<LocalDate, Attendance>> attendanceMap = buildAttendanceMap(attendances);

            for (Employee emp : activeEmployees) {
                Long empId = emp.getId();
                List<DailyExpectedShift> expectations = expectedSchedules.getOrDefault(empId, Collections.emptyList());

                for (DailyExpectedShift expected : expectations) {
                    Attendance attendance = attendanceMap.getOrDefault(empId, Collections.emptyMap()).get(expected.getTargetDate());

                    if (expected.isWorkingDay()) {
                        processWorkingDay(emp, expected, attendance, now);
                    } else {
                        processNonWorkingDay(emp, expected, attendance);
                    }
                }
            }

            this.lastRunStatus = "SUCCESS";
            this.lastRunTime = LocalDateTime.now();
            log.info("Batch Schedule Sweeper completed successfully.");

        } catch (Exception e) {
            this.lastRunStatus = "FAILED";
            this.lastRunTime = LocalDateTime.now();
            log.error("Batch Schedule Sweeper failed.", e);
        }
    }

    private void processWorkingDay(Employee emp, DailyExpectedShift expected, Attendance attendance, LocalDateTime now) {
        Shift shift = expected.getExpectedShift();
        LocalDate targetDate = expected.getTargetDate();

        LocalDateTime shiftEndDT = (shift.getCrossesMidnight() != null && shift.getCrossesMidnight()) ? targetDate.plusDays(1).atTime(shift.getEndTime()) : targetDate.atTime(shift.getEndTime());

        if (now.isAfter(shiftEndDT) && now.isBefore(shiftEndDT.plusHours(24))) {

            if (attendance != null && attendance.getFirstCheckIn() != null) {
                if (AttendanceStatus.WORKING.equals(attendance.getStatus()) || AttendanceStatus.PARTIAL_DAY.equals(attendance.getStatus())) {
                    attendanceCorrectionService.evaluateAndRouteAnomaly(attendance);
                }
            } else {
                AttendanceStatus exactStatus;
                double penaltyDays = 0;
                String penaltyDesc = "";

                if (expected.getActiveLeave() != null && expected.getLeaveSession() != LeaveSession.FULL_DAY) {
                    exactStatus = AttendanceStatus.HALF_DAY_ABSENT;
                    penaltyDays = 0.5;
                    penaltyDesc = "Missed shift on half-day leave";
                } else {
                    exactStatus = AttendanceStatus.ABSENT;
                    penaltyDays = 1.0;
                    penaltyDesc = "Absent without notice";
                }

                if (attendance == null || !exactStatus.equals(attendance.getStatus())) {
                    Attendance attRecord = (attendance != null) ? attendance : new Attendance();
                    attRecord.setEmployee(emp);
                    attRecord.setAttendanceDate(targetDate);
                    attRecord.setStatus(exactStatus);

                    attRecord.setExpectedShiftId(shift.getId());
                    attRecord.setExpectedShiftName(shift.getName());
                    attRecord.setExpectedStartTime(shift.getStartTime());
                    attRecord.setExpectedEndTime(shift.getEndTime());
                    attRecord.setExpectedWorkMinutes(shift.getRequiredWorkTime());
                    attRecord.setCrossesMidnight(shift.getCrossesMidnight());
                    attRecord.setIsManualOverride(expected.isManualOverride());

                    if (penaltyDays > 0) {
                        deductPenalty(emp, "Unpaid Leave", penaltyDays, targetDate.getYear(), penaltyDesc);
                    }
                    attendanceRepository.save(attRecord);
                }
            }
        }
    }

    private void processNonWorkingDay(Employee emp, DailyExpectedShift expected, Attendance attendance) {
        if (attendance == null || attendance.getFirstCheckIn() == null) {

            AttendanceStatus exactStatus = AttendanceStatus.OFF_DAY;
            if (expected.isHoliday()) exactStatus = AttendanceStatus.PUBLIC_HOLIDAY;
            if (expected.getActiveLeave() != null && expected.getLeaveSession() == LeaveSession.FULL_DAY) {
                exactStatus = AttendanceStatus.ON_LEAVE;
            }

            Attendance attRecord = (attendance != null) ? attendance : new Attendance();
            attRecord.setEmployee(emp);
            attRecord.setAttendanceDate(expected.getTargetDate());

            if (!exactStatus.equals(attRecord.getStatus())) {
                attRecord.setStatus(exactStatus);
                attendanceRepository.save(attRecord);
            }
        }
    }

    private void deductPenalty(Employee employee, String leaveTypeName, double days, int year, String desc) {
        LeaveType type = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(leaveTypeName, "UPL-001").stream().findFirst().orElseThrow(() -> new IllegalStateException("Leave type not found!"));
        leaveBalanceService.deductPenalty(employee, type, java.math.BigDecimal.valueOf(days), year, desc);
    }

    private Map<Long, Map<LocalDate, Attendance>> buildAttendanceMap(List<Attendance> attendances) {
        Map<Long, Map<LocalDate, Attendance>> map = new HashMap<>();
        for (Attendance a : attendances) {
            map.computeIfAbsent(a.getEmployee().getId(), k -> new HashMap<>()).put(a.getAttendanceDate(), a);
        }
        return map;
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }
}