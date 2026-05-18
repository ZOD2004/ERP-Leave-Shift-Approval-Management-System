package com.murali.service;


import com.murali.entity.LeaveRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DashboardService {

    private final LeaveRequestService leaveRequestService;
    private final AttendanceSyncService attendanceSyncService;
    private final EmployeeService employeeService;

    public DashboardService(LeaveRequestService leaveRequestService,
                            AttendanceSyncService attendanceSyncService,
                            EmployeeService employeeService) {
        this.leaveRequestService = leaveRequestService;
        this.attendanceSyncService = attendanceSyncService;
        this.employeeService = employeeService;
    }

    public String getTodayAbsenceCount() {
        // Assume leaveRequestService has a method to find active leaves for a date
        return String.valueOf(leaveRequestService.getActiveLeavesCountForDate(java.time.LocalDate.now()));
    }

    public String getTotalPendingCount() {
        return String.valueOf(leaveRequestService.countPendingRequests());
    }

    public String getMonthlyUtilization() {
        // Logic to calculate % of used leave vs total pool
        return "62";
    }

    public String getAttendanceRate() {
        // Assume attendanceSyncService calculates organizational attendance
        return "94.2";
    }

    public List<LeaveRequest> getOldestPendingRequests(int limit) {
        return leaveRequestService.getPendingRequestsOrderByDate(limit);
    }
}
