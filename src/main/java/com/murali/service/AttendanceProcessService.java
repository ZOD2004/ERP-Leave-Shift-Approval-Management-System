package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.ShiftAssignment;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceProcessService {

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;

    private static final int GRACE_PERIOD_MINUTES = 15;

    @Transactional
    public Attendance processDailyPunch(Long employeeId, LocalDateTime punchTime, boolean isCheckIn) {
        LocalDate today = punchTime.toLocalDate();

        ShiftAssignment assignment = shiftAssignmentRepository
                .findByEmployeeIdAndAssignmentDate(employeeId, today)
                .orElseThrow(() -> new IllegalArgumentException("No shift assigned for employee ID " + employeeId + " on " + today));

        Attendance attendance = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employeeId, today)
                .orElseGet(() -> {
                    Attendance newRecord = new Attendance();
                    newRecord.setEmployee(employeeRepository.getReferenceById(employeeId));
                    newRecord.setShiftAssignment(assignment);
                    newRecord.setAttendanceDate(today);
                    return newRecord;
                });

        if (isCheckIn) {
            attendance.setCheckIn(punchTime);

            LocalTime effectiveStart = assignment.getEffectiveStartTime();
            LocalTime actualStart = punchTime.toLocalTime();

            if (actualStart.isAfter(effectiveStart.plusMinutes(GRACE_PERIOD_MINUTES))) {
                attendance.setStatus("LATE");
                log.info("Employee {} checked in late at {} against effective start {}", employeeId, actualStart, effectiveStart);
            } else {
                attendance.setStatus("PRESENT");
            }
        } else {
            if (attendance.getCheckIn() == null) {
                log.warn("Clock-out anomaly: Employee {} attempted to check out without a prior check-in.", employeeId);
            }
            attendance.setCheckOut(punchTime);
        }

        return attendanceRepository.save(attendance);
    }
    public Optional<Attendance> getTodayAttendance(Long employeeId) {
        return attendanceRepository.findByEmployee_IdAndAttendanceDate(employeeId, LocalDate.now());
    }
    public List<Attendance> getEmployeeAttendanceHistory(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return attendanceRepository.findAttendanceHistoryByEmployee(employeeId, startDate, endDate);
    }
}