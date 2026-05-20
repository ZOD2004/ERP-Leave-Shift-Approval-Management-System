package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.AttendanceCorrection;
import com.murali.entity.LeaveType;
import com.murali.entity.User;
import com.murali.repository.AttendanceCorrectionRepository;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.UserRepository;
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

    /**
     * 1. Auto-Trigger (Called by AttendanceCronJobService)
     */
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
    }

    /**
     * 2. Manager Resolution (Called by the Vaadin UI)
     */
    @Transactional
    public void resolveCorrection(Long correctionId, String action, LocalDateTime manualCheckOutTime, String comments, Long actingUserId) {
        AttendanceCorrection correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new IllegalArgumentException("Correction record not found"));

        if (!correction.getApprover().getId().equals(actingUserId)) {
            throw new SecurityException("You are not authorized to resolve this anomaly.");
        }

        Attendance attendance = correction.getAttendance();

        if ("APPROVED".equalsIgnoreCase(action)) {
            if (manualCheckOutTime == null) {
                throw new IllegalArgumentException("A manual check-out time must be provided for approval.");
            }

            // 1. Update Attendance Status and Time
            attendance.setCheckOut(manualCheckOutTime);
            attendance.setStatus("PRESENT");

            // Note: The 0.5 penalty remains in the LeaveBalance ledger as per strict company policy.

            correction.setResolvedCheckOutTime(manualCheckOutTime);
            correction.setStatus("APPROVED");

        } else if ("REJECTED".equalsIgnoreCase(action)) {

            // 1. Update Attendance Status to completely absent
            attendance.setStatus("ABSENT");

            // 2. Deduct the remaining 0.5 days to make it a full 1.0 day penalty
            LeaveType casualLeave = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Casual Leave", "CL-001").getFirst();
            leaveBalanceService.deduct(
                    attendance.getEmployee(),
                    casualLeave,
                    BigDecimal.valueOf(0.5),
                    null,
                    attendance.getAttendanceDate().getYear()
            );

            correction.setStatus("REJECTED");
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
    public List<AttendanceCorrection> getAllPendingCorrectionsGlobally() {
        return correctionRepository.findAllPendingCorrectionsGlobally();
    }

    @Transactional(readOnly = true)
    public long getGlobalPendingCorrectionsCount() {
        return correctionRepository.countByStatus("PENDING");
    }
}