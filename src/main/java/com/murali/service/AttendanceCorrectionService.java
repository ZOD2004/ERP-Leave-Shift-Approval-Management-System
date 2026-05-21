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
    private final UserRepository userRepository;


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

    private User resolveManagerForEmployee(com.murali.entity.Employee employee) {
        if (employee.getManager() != null && employee.getManager().getUser() != null) {
            return employee.getManager().getUser();
        }
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