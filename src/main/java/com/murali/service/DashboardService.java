package com.murali.service;


import com.murali.entity.LeaveRequest;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.LeaveBalanceRepository;
import com.murali.repository.LeaveRequestRepository;
import com.murali.repository.ShiftAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final LeaveRequestService leaveRequestService;
    private final AttendanceSyncService attendanceSyncService;
    private final EmployeeService employeeService;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    public DashboardService(LeaveRequestService leaveRequestService,
                            AttendanceSyncService attendanceSyncService,
                            EmployeeService employeeService, LeaveBalanceRepository leaveBalanceRepository, ShiftAssignmentRepository shiftAssignmentRepository, AttendanceRepository attendanceRepository, LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestService = leaveRequestService;
        this.attendanceSyncService = attendanceSyncService;
        this.employeeService = employeeService;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public String getTodayAbsenceCount() {
        return String.valueOf(leaveRequestService.getActiveLeavesCountForDate(java.time.LocalDate.now()));
    }

    public String getTotalPendingCount() {
        return String.valueOf(leaveRequestService.countPendingRequests());
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getGlobalLeaveUtilization(int year) {
        List<Object[]> result = leaveBalanceRepository.getGlobalLeaveUtilization(year);
        Map<String, BigDecimal> utilization = new java.util.HashMap<>();

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

    public long getEscalatedApprovalsCount(LocalDate start, LocalDate end) {
        return leaveRequestRepository.countEscalatedApprovals(start, end);
    }

    public long getManualOverridesCount(LocalDate start, LocalDate end) {
        return shiftAssignmentRepository.countByOverrideAppliedTrue(start, end);
    }
    public long getMissingPunchesCount() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        return getMissingPunchesCount(startOfMonth, endOfMonth);
    }

    public long getEscalatedApprovalsCount() {
        LocalDate startOfYear = LocalDate.now().withDayOfYear(1);
        LocalDate endOfYear = LocalDate.now().withDayOfYear(LocalDate.now().lengthOfYear());
        return getEscalatedApprovalsCount(startOfYear, endOfYear);
    }

    public long getManualOverridesCount() {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        return getManualOverridesCount(startOfMonth, endOfMonth);
    }

    public long getNegativeBalancesCount() {
        return leaveBalanceRepository.countNegativeBalances(LocalDate.now().getYear());
    }
}
