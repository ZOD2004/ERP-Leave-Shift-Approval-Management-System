package com.murali.service;


import com.murali.entity.LeaveRequest;
import com.murali.repository.LeaveBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final LeaveRequestService leaveRequestService;
    private final AttendanceSyncService attendanceSyncService;
    private final EmployeeService employeeService;
    private final LeaveBalanceRepository leaveBalanceRepository;

    public DashboardService(LeaveRequestService leaveRequestService,
                            AttendanceSyncService attendanceSyncService,
                            EmployeeService employeeService, LeaveBalanceRepository leaveBalanceRepository) {
        this.leaveRequestService = leaveRequestService;
        this.attendanceSyncService = attendanceSyncService;
        this.employeeService = employeeService;
        this.leaveBalanceRepository = leaveBalanceRepository;
    }

    public String getTodayAbsenceCount() {
        // Assume leaveRequestService has a method to find active leaves for a date
        return String.valueOf(leaveRequestService.getActiveLeavesCountForDate(java.time.LocalDate.now()));
    }

    public String getTotalPendingCount() {
        return String.valueOf(leaveRequestService.countPendingRequests());
    }



    public List<LeaveRequest> getOldestPendingRequests(int limit) {
        return leaveRequestService.getPendingRequestsOrderByDate(limit);
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

}
