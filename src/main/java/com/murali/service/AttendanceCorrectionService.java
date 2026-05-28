package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.AttendanceCorrection;
import com.murali.entity.LeaveType;
import com.murali.entity.User;
import com.murali.entity.AuditLog;
import com.murali.repository.AttendanceCorrectionRepository;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.UserRepository;
import com.murali.repository.AuditLogRepository;
import com.murali.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionService {

    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;

    // Injected for Audit Logging
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;


    @Transactional
    public void autoCreateCorrection(Attendance attendance) {
        User approver = resolveManagerForEmployee(attendance.getEmployee());

        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendance(attendance);
        correction.setApprover(approver);
        correction.setStatus("PENDING");

        correctionRepository.save(correction);
        log.info("Auto-triggered Attendance Correction for employee {}, routed to manager {}",
                attendance.getEmployee().getId(), approver.getUsername());

        // Audit Log: Creation event (No old state)
        String newState = String.format("{ \"status\": \"PENDING\", \"attendanceId\": %d, \"approverId\": %d }",
                attendance.getId(), approver.getId());
        saveAuditLog(correction.getId(), "CREATED", "attendance_corrections", null, newState);
    }

    @Transactional
    public void resolveCorrection(Long correctionId, String action, LocalDateTime manualCheckOutTime, String comments, Long actingUserId) {
        AttendanceCorrection correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new IllegalArgumentException("Correction record not found"));

        if (!correction.getApprover().getId().equals(actingUserId)) {
            throw new SecurityException("You are not authorized to resolve this anomaly.");
        }

        Attendance attendance = correction.getAttendance();

        String oldStatus = correction.getStatus();
        String safeComments = (comments != null) ? comments.replace("\"", "\\\"") : ""; // Escape quotes for JSON

        if ("APPROVED".equalsIgnoreCase(action)) {
            if (manualCheckOutTime == null) {
                throw new IllegalArgumentException("A manual check-out time must be provided for approval.");
            }

            // 1. Update Attendance Status and Time
            attendance.setCheckOut(manualCheckOutTime);
            attendance.setStatus("PRESENT");

            correction.setResolvedCheckOutTime(manualCheckOutTime);
            correction.setStatus("APPROVED");

            String oldState = String.format("{ \"status\": \"%s\", \"checkOut\": null }", oldStatus);
            String newState = String.format("{ \"status\": \"APPROVED\", \"checkOut\": \"%s\", \"comments\": \"%s\" }",
                    manualCheckOutTime.toString(), safeComments);

            saveAuditLog(correction.getId(), "APPROVED", "attendance_corrections", oldState, newState);

        } else if ("REJECTED".equalsIgnoreCase(action)) {
            attendance.setStatus("ABSENT");

            LeaveType halfDayLeave = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Half Day Leave", "HDL-001").getFirst();
            leaveBalanceService.deductPenalty(
                    attendance.getEmployee(),
                    halfDayLeave,
                    BigDecimal.valueOf(0.5),
                    attendance.getAttendanceDate().getYear(),
                    "Missing Check-out Rejected by Manager"
            );

            correction.setStatus("REJECTED");

            String oldState = String.format("{ \"status\": \"%s\" }", oldStatus);
            String newState = String.format("{ \"status\": \"REJECTED\", \"comments\": \"%s\" }", safeComments);

            saveAuditLog(correction.getId(), "REJECTED", "attendance_corrections", oldState, newState);

        } else {
            throw new IllegalArgumentException("Invalid action.");
        }

        correction.setManagerComments(comments);

        attendanceRepository.save(attendance);
        correctionRepository.save(correction);
    }

    private User resolveManagerForEmployee(com.murali.entity.Employee employee) {
        if (employee.getManager() != null && employee.getManager().getUser() != null) {
            return employee.getManager().getUser();
        }
        // Fallback to HR Admin if no manager exists
        return userRepository.findFirstByRoleName("ROLE_HR_ADMIN")
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No Manager or HR Admin available to route anomaly."));
    }

    @Transactional(readOnly = true)
    public List<AttendanceCorrection> getPendingCorrectionsForApprover(Long approverId) {
        return correctionRepository.findPendingCorrectionsForManager(approverId);
    }

    @Transactional(readOnly = true)
    public List<AttendanceCorrection> getAllPendingCorrectionsGlobally() {
        return correctionRepository.findAllPendingCorrectionsGlobally();
    }

    @Transactional(readOnly = true)
    public long getGlobalPendingCorrectionsCount() {
        return correctionRepository.countByStatus("PENDING");
    }

    // --- Private Audit Log Helper ---
    private void saveAuditLog(Long recordId, String action, String entityName, String oldState, String newState) {
        try {
            String performedBy = "SYSTEM";

            if (securityService.getPrincipal() != null) {
                String username = securityService.getPrincipal().getUsername();
                String role = "USER";

                if (securityService.getAuthentication() != null && !securityService.getAuthentication().getAuthorities().isEmpty()) {
                    role = securityService.getAuthentication().getAuthorities().iterator().next().getAuthority();
                }
                performedBy = username + " (" + role + ")";
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setRecordId(recordId);
            auditLog.setAction(action);
            auditLog.setEntityName(entityName);
            auditLog.setPerformedBy(performedBy);
            auditLog.setOldState(oldState);
            auditLog.setNewState(newState);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save database audit log for record {}: {}", recordId, e.getMessage());
        }
    }
}