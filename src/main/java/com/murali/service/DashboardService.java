package com.murali.service;


import com.murali.entity.LeaveRequest;
import com.murali.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final LeaveRequestService leaveRequestService;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveApprovalRepository leaveApprovalRepository;

    public String getTodayAbsenceCount() {
        return String.valueOf(leaveRequestService.getActiveLeavesCountForDate(LocalDate.now()));
    }

    public String getTotalPendingCount() {
        return String.valueOf(leaveRequestService.countPendingRequests());
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getGlobalLeaveUtilization(int year) {
        List<Object[]> result = leaveBalanceRepository.getGlobalLeaveUtilization(year);
        Map<String, BigDecimal> utilization = new HashMap<>();

        if (result != null && !result.isEmpty() && result.get(0).length == 2) {
            utilization.put("total", new BigDecimal(result.get(0)[0].toString()));
            utilization.put("used", new BigDecimal(result.get(0)[1].toString()));
        } else {
            utilization.put("total", BigDecimal.ZERO);
            utilization.put("used", BigDecimal.ZERO);
        }
        return utilization;
    }

    public long getMissingPunchesCount(LocalDate start, LocalDate end) {
        return attendanceRepository.countMissingPunches(start, end);
    }

    public long getMissingPunchesCount() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        return getMissingPunchesCount(startOfMonth, endOfMonth);
    }

    public long getEscalatedApprovalsCount() {
       return leaveApprovalRepository.countPendingEscalations();
    }
}