package com.murali.service;

import com.murali.entity.*;
import com.murali.repository.*;
import com.murali.util.SecurityService;
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
    private final TimeLogRepository timeLogRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityService securityService;

    // INJECTED TO ENABLE CENTRALIZED TIMELINE RECALCULATION
    private final AttendanceProcessService attendanceProcessService;
    private final LeaveRequestRepository leaveRequestRepository;

    @Transactional
    public void evaluateAndRouteAnomaly(Attendance attendance) {
        LeaveRequest leaveRequest = leaveRequestRepository.findApprovedLeaveForEmployeeOnDate(
                attendance.getEmployee().getId(), attendance.getAttendanceDate()
        ).orElse(null);

        // FIRE THE ENGINE: Let the centralized process calculate minutes and evaluate the status
        attendanceProcessService.recalculateTimeline(attendance, attendance.getShiftAssignment().getShift(), leaveRequest);

        // Check if the engine natively resolved it (e.g., they actually met the required hours)
        String currentStatus = attendance.getStatus() != null ? attendance.getStatus().toString() : "";
        if ("PRESENT".equals(currentStatus) || "HALF_DAY_LEAVE".equals(currentStatus)) {
            log.info("Missing punch ignored for Employee {}. Worked {} mins, met requirement.",
                    attendance.getEmployee().getId(), attendance.getTotalWorkedMinutes());
        } else {
            autoCreateCorrection(attendance);
        }
    }

    @Transactional
    public void autoCreateCorrection(Attendance attendance) {
        User approver = resolveManagerForEmployee(attendance.getEmployee());

        attendance.setStatus("PENDING_RESOLUTION");
        attendanceRepository.save(attendance);

        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendance(attendance);
        correction.setApprover(approver);
        correction.setStatus("PENDING");

        correctionRepository.save(correction);
        log.info("Auto-triggered Attendance Correction for employee {}, routed to manager {}",
                attendance.getEmployee().getId(), approver.getUsername());

        String newState = String.format("{ \"status\": \"PENDING\", \"attendanceId\": %d, \"approverId\": %d }",
                attendance.getId(), approver.getId());

        auditLogService.saveAuditLog(correction.getId(), "CREATE", "AttendanceCorrection", null, newState);
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
        String safeComments = (comments != null) ? comments.replace("\"", "\\\"") : "";

        LeaveRequest leaveRequest = leaveRequestRepository.findApprovedLeaveForEmployeeOnDate(
                attendance.getEmployee().getId(), attendance.getAttendanceDate()
        ).orElse(null);

        if ("APPROVED".equalsIgnoreCase(action)) {
            if (manualCheckOutTime == null) {
                throw new IllegalArgumentException("A manual check-out time must be provided for approval.");
            }

            TimeLog manualOut = new TimeLog();
            manualOut.setAttendance(attendance);
            manualOut.setPunchTime(manualCheckOutTime);
            manualOut.setPunchType("OUT");
            manualOut.setSource("MANAGER_OVERRIDE");
            timeLogRepository.save(manualOut);

            // FIRE THE ENGINE: Let it calculate the final metrics natively now that the missing punch exists
            attendanceProcessService.recalculateTimeline(attendance, attendance.getShiftAssignment().getShift(), leaveRequest);

            correction.setResolvedCheckOutTime(manualCheckOutTime);
            correction.setStatus("APPROVED");

            String oldState = String.format("{ \"status\": \"%s\", \"checkOut\": null }", oldStatus);
            String newState = String.format("{ \"status\": \"APPROVED\", \"checkOut\": \"%s\", \"comments\": \"%s\" }",
                    manualCheckOutTime.toString(), safeComments);

            auditLogService.saveAuditLog(correction.getId(), "UPDATE", "AttendanceCorrection", oldState, newState);

        } else if ("REJECTED".equalsIgnoreCase(action)) {

            // FIRE THE ENGINE: Ensure timeline totals are strictly updated before applying penalty overrides
            attendanceProcessService.recalculateTimeline(attendance, attendance.getShiftAssignment().getShift(), leaveRequest);

            // Override the engine's standard status with the penalty statuses
            if (attendance.getTotalWorkedMinutes() > 0) {
                attendance.setStatus("PRESENT_PENALIZED");
            } else {
                attendance.setStatus("ABSENT");
            }

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

            auditLogService.saveAuditLog(correction.getId(), "UPDATE", "AttendanceCorrection", oldState, newState);

        } else {
            throw new IllegalArgumentException("Invalid action.");
        }

        correction.setManagerComments(comments);
        attendanceRepository.save(attendance);
        correctionRepository.save(correction);
    }

    private User resolveManagerForEmployee(Employee employee) {
        if (employee.getManager() != null && employee.getManager().getUser() != null) {
            return employee.getManager().getUser();
        }

        List<User> hrAdmins = userRepository.findByRoleName("ROLE_HR_ADMIN");
        if (hrAdmins.isEmpty()) {
            throw new IllegalStateException("No Manager or HR Admin available to route anomaly.");
        }
        return hrAdmins.getFirst();
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
}