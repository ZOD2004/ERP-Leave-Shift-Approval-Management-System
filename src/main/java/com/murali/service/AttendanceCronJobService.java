package com.murali.service;

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
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRotationPolicyRepository policyRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final LeaveBalanceService leaveBalanceService;
    private final ShiftRotationService shiftRotationService;
    private final AttendanceProcessService attendanceProcessService;

    private LocalDateTime lastRunTime;
    private String lastRunStatus = "WAITING";

    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void rollingShiftSweeper() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        List<LocalDate> datesToCheck = List.of(today, yesterday);

        try {
            this.lastRunStatus = "RUNNING";
            log.info("Starting Rolling Shift Sweeper at {}", now);

            List<Employee> activeEmployees = employeeRepository.findByActiveTrue();
            List<Long> empIds = activeEmployees.stream().map(Employee::getId).toList();

            List<ShiftAssignment> assignments = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(empIds, yesterday, today);
            List<ShiftRotationPolicy> policies = policyRepository.findAllWithEmployee();
            List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(empIds, "APPROVED", yesterday, today);
            List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(yesterday, today);

            List<Attendance> attendances = new ArrayList<>();
            for (LocalDate date : datesToCheck) {
                attendances.addAll(attendanceRepository.findByEmployeeIdInAndAttendanceDate(empIds, date));
            }

            Map<Long, Map<LocalDate, ShiftAssignment>> assignmentMap = buildAssignmentMap(assignments, datesToCheck);
            Map<Long, Map<LocalDate, Attendance>> attendanceMap = buildAttendanceMap(attendances);
            Map<Long, List<LeaveRequest>> leaveMap = leaves.stream().collect(Collectors.groupingBy(lr -> lr.getEmployee().getId()));
            Map<Long, ShiftRotationPolicy> policyMap = policies.stream().collect(Collectors.toMap(p -> p.getEmployee().getId(), p -> p, (p1, p2) -> p1));

            for (LocalDate targetDate : datesToCheck) {
                for (Employee emp : activeEmployees) {
                    Long empId = emp.getId();
                    ShiftAssignment assignment = assignmentMap.getOrDefault(empId, Collections.emptyMap()).get(targetDate);
                    Attendance attendance = attendanceMap.getOrDefault(empId, Collections.emptyMap()).get(targetDate);
                    List<LeaveRequest> empLeaves = leaveMap.getOrDefault(empId, Collections.emptyList());

                    if (assignment != null) {
                        processWorkingDay(emp, assignment, targetDate, attendance, empLeaves, now);
                    } else {
                        processNonWorkingDay(emp, targetDate, attendance, holidays, policyMap.get(empId));
                    }
                }
            }

            this.lastRunStatus = "SUCCESS";
            this.lastRunTime = LocalDateTime.now();
            log.info("Rolling Sweeper completed successfully.");

        } catch (Exception e) {
            this.lastRunStatus = "FAILED";
            this.lastRunTime = LocalDateTime.now();
            log.error("Rolling Sweeper failed.", e);
        }
    }

    private void processWorkingDay(Employee emp, ShiftAssignment assignment, LocalDate targetDate, Attendance attendance, List<LeaveRequest> empLeaves, LocalDateTime now) {
        Shift shift = assignment.getShift();
        LocalDateTime shiftEndDT = (shift.getCrossesMidnight() != null && shift.getCrossesMidnight()) ? targetDate.plusDays(1).atTime(shift.getEndTime()) : targetDate.atTime(shift.getEndTime());

        LocalDateTime triggerTime = shiftEndDT.plusHours(4);

        if (now.isAfter(triggerTime) && now.isBefore(triggerTime.plusHours(24))) {

            if (attendance != null && attendance.getFirstCheckIn() != null) {
                if (AttendanceStatus.WORKING.equals(attendance.getStatus()) || AttendanceStatus.PARTIAL_DAY.equals(attendance.getStatus())) {
                    attendanceCorrectionService.evaluateAndRouteAnomaly(attendance);
                }
            } else {
                LeaveRequest leave = getActiveLeaveForDate(empLeaves, targetDate);
                LeaveSession session = getSessionForDate(leave, targetDate);

                AttendanceStatus exactStatus;
                double penaltyDays = 0;
                String penaltyDesc = "";

                if (leave != null && session == LeaveSession.FULL_DAY) {
                    exactStatus = AttendanceStatus.ON_LEAVE;
                    penaltyDesc="emp on full day leave";
                } else if (leave != null && session != LeaveSession.FULL_DAY) {
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
                    attRecord.setShiftAssignment(assignment);
                    attRecord.setAttendanceDate(targetDate);
                    attRecord.setStatus(exactStatus);

                    if (penaltyDays > 0) {
                        deductPenalty(emp, "Unpaid Leave", penaltyDays, targetDate.getYear(), penaltyDesc);
                    }
                    attendanceRepository.save(attRecord);
                }
            }
        }
    }

    private void processNonWorkingDay(Employee emp, LocalDate targetDate, Attendance attendance, List<LocalDate> holidays, ShiftRotationPolicy policy) {

        if (attendance == null || attendance.getFirstCheckIn() == null) {
            AttendanceStatus exactStatus = determineNonWorkingStatus(emp, targetDate, holidays, policy);

            Attendance attRecord = (attendance != null) ? attendance : new Attendance();
            attRecord.setEmployee(emp);
            attRecord.setShiftAssignment(null);
            attRecord.setAttendanceDate(targetDate);

            if (!exactStatus.equals(attRecord.getStatus())) {
                attRecord.setStatus(exactStatus);
                attendanceRepository.save(attRecord);
            }
        }
    }

    private AttendanceStatus determineNonWorkingStatus(Employee employee, LocalDate targetDate, List<LocalDate> holidays, ShiftRotationPolicy policy) {
        if (holidays.contains(targetDate)) {
            return AttendanceStatus.PUBLIC_HOLIDAY;
        }

        if (policy != null) {
            if (policy.getGeneratedUntil() != null && !targetDate.isAfter(policy.getGeneratedUntil())) {
                return AttendanceStatus.OFF_DAY;
            } else {
                int totalCycleDays = shiftRotationService.calculateCycleDays(policy);
                RotationSequence seq = shiftRotationService.calculateSequenceForDate(policy, targetDate, totalCycleDays);
                if (seq == null || seq.getSegmentType() == RotationSegmentType.OFF) {
                    return AttendanceStatus.OFF_DAY;
                }
            }
        } else if (employee.getDefaultShift() != null) {
            if (employee.getDefaultShiftGeneratedUntil() != null && !targetDate.isAfter(employee.getDefaultShiftGeneratedUntil())) {
                return AttendanceStatus.OFF_DAY;
            } else {
                String dayOfWeek = targetDate.getDayOfWeek().name();
                boolean isWorkingDay = employee.getDefaultShift().getWorkingDays().stream().anyMatch(wd -> wd.name().equalsIgnoreCase(dayOfWeek));
                if (!isWorkingDay) {
                    return AttendanceStatus.OFF_DAY;
                }
            }
        } else {
            DayOfWeek day = targetDate.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                return AttendanceStatus.OFF_DAY;
            }
        }
        return AttendanceStatus.OFF_DAY;
    }

    private void deductPenalty(Employee employee, String leaveTypeName, double days, int year, String desc) {
        LeaveType type = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(leaveTypeName, "UPL-001").stream().findFirst().orElseThrow(() -> new IllegalStateException("Leave type not found!"));
        leaveBalanceService.deductPenalty(employee, type, java.math.BigDecimal.valueOf(days), year, desc);
    }

    private Map<Long, Map<LocalDate, ShiftAssignment>> buildAssignmentMap(List<ShiftAssignment> assignments, List<LocalDate> targetDates) {
        Map<Long, Map<LocalDate, ShiftAssignment>> map = new HashMap<>();
        for (ShiftAssignment sa : assignments) {
            map.computeIfAbsent(sa.getEmployee().getId(), k -> new HashMap<>());
            for (LocalDate date : targetDates) {
                if (!date.isBefore(sa.getStartDate()) && !date.isAfter(sa.getEndDate())) {
                    map.get(sa.getEmployee().getId()).put(date, sa);
                }
            }
        }
        return map;
    }

    private Map<Long, Map<LocalDate, Attendance>> buildAttendanceMap(List<Attendance> attendances) {
        Map<Long, Map<LocalDate, Attendance>> map = new HashMap<>();
        for (Attendance a : attendances) {
            map.computeIfAbsent(a.getEmployee().getId(), k -> new HashMap<>()).put(a.getAttendanceDate(), a);
        }
        return map;
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;
        if (targetDate.equals(request.getStartDate()) && targetDate.equals(request.getEndDate()))
            return request.getStartSession();
        if (targetDate.equals(request.getStartDate())) return request.getStartSession();
        if (targetDate.equals(request.getEndDate())) return request.getEndSession();
        return LeaveSession.FULL_DAY;
    }

    private LeaveRequest getActiveLeaveForDate(List<LeaveRequest> leaves, LocalDate targetDate) {
        if (leaves == null || leaves.isEmpty()) return null;
        for (LeaveRequest leave : leaves) {
            if (!targetDate.isBefore(leave.getStartDate()) && !targetDate.isAfter(leave.getEndDate())) {
                return leave;
            }
        }
        return null;
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }
}