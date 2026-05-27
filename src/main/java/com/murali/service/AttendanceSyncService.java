package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.LeaveRequest;
import com.murali.entity.ShiftAssignment;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSyncService {

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;

    private final AuditLogService auditLoggingService;

    public static final String STATUS_ON_LEAVE = "ON_LEAVE";
    public static final String STATUS_HALF_DAY_LEAVE = "HALF_DAY_LEAVE";
    public static final String STATUS_PRESENT = "PRESENT";
    public static final String STATUS_LATE = "LATE";

    private static final int GRACE_PERIOD_MINUTES = 15;

    @Transactional
    public void syncLeaveRecords(LeaveRequest request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        Long employeeId = request.getEmployee().getId();

        Map<LocalDate, Attendance> attendanceMap = attendanceRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(Attendance::getAttendanceDate, a -> a));

        List<ShiftAssignment> assignments = shiftAssignmentRepository
                .findByEmployeeIdInAndAssignmentDateBetween(List.of(employeeId), startDate, endDate);

        Map<LocalDate, ShiftAssignment> assignmentMap = assignments.stream()
                .collect(Collectors.toMap(ShiftAssignment::getAssignmentDate, a -> a));

        List<Attendance> recordsToSave = new ArrayList<>();

        // Memory map to hold the old states before we save the batch
        Map<LocalDate, String> oldStateByDate = new HashMap<>();

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (!assignmentMap.containsKey(currentDate)) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            Attendance attendance = attendanceMap.getOrDefault(currentDate, new Attendance());
            boolean isNewRecord = (attendance.getId() == null);

            // Capture old state (null if it's a new record)
            String oldState = isNewRecord ? null : String.format("{ \"status\": \"%s\" }", attendance.getStatus());
            oldStateByDate.put(currentDate, oldState);

            if (isNewRecord) {
                attendance.setEmployee(request.getEmployee());
                attendance.setShiftAssignment(assignmentMap.get(currentDate));
                attendance.setAttendanceDate(currentDate);
            }

            if (request.getDurationDays().compareTo(new BigDecimal("0.5")) == 0) {
                attendance.setStatus(STATUS_HALF_DAY_LEAVE);
            } else {
                attendance.setStatus(STATUS_ON_LEAVE);
            }

            recordsToSave.add(attendance);
            currentDate = currentDate.plusDays(1);
        }

        if (!recordsToSave.isEmpty()) {
            // 1. Perform the batch save
            List<Attendance> savedRecords = attendanceRepository.saveAll(recordsToSave);

            // 2. Loop through the saved entities (which now have valid IDs) to generate audit logs
            for (Attendance saved : savedRecords) {
                String oldState = oldStateByDate.get(saved.getAttendanceDate());
                String newState = String.format("{ \"status\": \"%s\" }", saved.getStatus());
                String action = (oldState == null) ? "CREATED" : "UPDATED";

                auditLoggingService.saveAuditLog(saved.getId(), action, "attendance", oldState, newState);
            }
        }
    }

    @Transactional
    public void revertLeaveFromAttendance(LeaveRequest request) {
        Long employeeId = request.getEmployee().getId();
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        List<Attendance> existingRecords = attendanceRepository
                .findByEmployeeIdAndAttendanceDateBetween(employeeId, startDate, endDate);

        List<Attendance> recordsToDelete = new ArrayList<>();
        List<Attendance> recordsToUpdate = new ArrayList<>();

        // Memory map to hold old states for updates
        Map<Long, String> oldStatesById = new HashMap<>();

        for (Attendance attendance : existingRecords) {
            String currentStatus = attendance.getStatus();

            if (STATUS_ON_LEAVE.equals(currentStatus) || STATUS_HALF_DAY_LEAVE.equals(currentStatus)) {

                if (attendance.getCheckIn() == null && attendance.getCheckOut() == null) {
                    recordsToDelete.add(attendance);
                } else {
                    // Capture old state before mutating the object
                    oldStatesById.put(attendance.getId(), String.format("{ \"status\": \"%s\" }", attendance.getStatus()));

                    if (attendance.getShiftAssignment() != null && attendance.getCheckIn() != null) {
                        LocalTime effectiveStart = attendance.getShiftAssignment().getEffectiveStartTime();
                        LocalTime actualStart = attendance.getCheckIn().toLocalTime();

                        boolean isLate = actualStart.isAfter(effectiveStart.plusMinutes(GRACE_PERIOD_MINUTES));

                        attendance.setIsLate(isLate);
                        attendance.setStatus(isLate ? STATUS_LATE : STATUS_PRESENT);
                    } else {
                        attendance.setStatus(STATUS_PRESENT);
                    }
                    recordsToUpdate.add(attendance);
                }
            }
        }

        // Process Deletions
        if (!recordsToDelete.isEmpty()) {
            for (Attendance toDelete : recordsToDelete) {
                String oldState = String.format("{ \"status\": \"%s\" }", toDelete.getStatus());
                // For deletions, new state is null
                auditLoggingService.saveAuditLog(toDelete.getId(), "DELETED", "attendance", oldState, null);
            }
            attendanceRepository.deleteAll(recordsToDelete);
        }

        // Process Updates
        if (!recordsToUpdate.isEmpty()) {
            List<Attendance> savedUpdates = attendanceRepository.saveAll(recordsToUpdate);
            for (Attendance saved : savedUpdates) {
                String oldState = oldStatesById.get(saved.getId());
                String newState = String.format("{ \"status\": \"%s\", \"isLate\": %b }", saved.getStatus(), saved.getIsLate());
                auditLoggingService.saveAuditLog(saved.getId(), "UPDATED", "attendance", oldState, newState);
            }
        }
    }
}