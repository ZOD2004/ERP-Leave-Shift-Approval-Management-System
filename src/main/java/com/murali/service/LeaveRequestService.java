package com.murali.service;

import com.murali.entity.*;
import com.murali.exception.PastDateException;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final DurationEngineService durationEngineService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveApprovalRuleService ruleService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceSyncService attendanceSyncService;

    private final ApprovalRoutingService approvalRoutingService;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final List<String> BACKDATED_ALLOWED_CODES = List.of("EMG-001", "SL-001");

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, DurationEngineService durationEngineService, LeaveBalanceService leaveBalanceService, LeaveApprovalRuleService ruleService, EmployeeRepository employeeRepository, AttendanceSyncService attendanceSyncService, ApprovalRoutingService approvalRoutingService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.durationEngineService = durationEngineService;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.attendanceSyncService = attendanceSyncService;
        this.approvalRoutingService = approvalRoutingService;
    }

    @Transactional
    public void submitLeaveRequest(Employee detachedEmployee, LeaveType leaveType,
                                           LocalDate startDate, LocalDate endDate,
                                           String reason, Integer currentYear) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        List<String> activeStatuses = List.of(STATUS_PENDING, STATUS_APPROVED);
        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(
                employee.getId(), startDate, endDate, activeStatuses
        );

        if (hasOverlap) {
            throw new IllegalArgumentException(
                    "Validation Failed: You already have a pending or approved leave request that overlaps with these dates."
            );
        }

        BigDecimal duration = durationEngineService.calculateNetLeaveDays(
                startDate, endDate, employee, leaveType, false
        );

        if (duration.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Calculated leave duration must be greater than 0 days.");
        }

        if (startDate.isBefore(LocalDate.now())) {
            if (!BACKDATED_ALLOWED_CODES.contains(leaveType.getCode().toUpperCase())) {
                throw new PastDateException(
                        "Validation Failed: Back-dating is only permitted for Sick or Emergency leaves."
                );
            }
        }

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employee.getId(), currentYear);

        LeaveBalance currentBalance = null;

        for (LeaveBalance balance : balances) {
            if (balance.getLeaveType().getId().equals(leaveType.getId())) {
                currentBalance = balance;
                break;
            }
        }

        BigDecimal effectiveBalance = leaveBalanceService.getEffectiveBalance(currentBalance);
        boolean isNegativeBalance = duration.compareTo(effectiveBalance) > 0;

        if (isNegativeBalance) {
            String code = leaveType.getCode();
            if (!code.equals("SL-001") && !code.equals("EMG-001")) {
                throw new IllegalArgumentException(
                        "Insufficient balance. You only have " + effectiveBalance + " days available. " +
                                "Negative balances are only permitted for Sick or Emergency leaves."
                );
            }
            reason = "[WARNING: NEGATIVE BALANCE REQUEST] - " + reason;
        }

        List<LeaveApprovalRule> applicableRules = ruleService.getApplicableRules(leaveType.getId(), duration);

        if (applicableRules.isEmpty()) {
            throw new IllegalStateException("System Configuration Error: No approval rules found for this leave type and duration.");
        }

        if (isNegativeBalance) {
            reason = "[WARNING: NEGATIVE BALANCE REQUEST] - " + reason;
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDurationDays(duration);
        request.setReason(reason);
        request.setStatus(STATUS_PENDING);
        request.setCurrentLevel(1);

        LeaveRequest savedRequest = leaveRequestRepository.save(request);


        leaveBalanceService.holdPendingBalance(employee, leaveType, duration, currentYear, savedRequest.getId());

        approvalRoutingService.generateApprovalWorkflow(savedRequest, applicableRules, isNegativeBalance);

    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getLeaveHistoryForEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingRequestsOrderByDate(int limit) {
        return leaveRequestRepository.findPendingRequests(STATUS_PENDING, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long countPendingRequests() {
        return leaveRequestRepository.countByStatus(STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public long getActiveLeavesCountForDate(LocalDate date) {
        return leaveRequestRepository.countActiveLeavesForDate(date);
    }

    @Transactional
    public void cancelLeaveRequest(Long leaveRequestId, Long requestingEmployeeId, Integer currentYear) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found"));

        // Security check
        if (!request.getEmployee().getId().equals(requestingEmployeeId)) {
            throw new SecurityException("You do not have permission to cancel this leave.");
        }

        String currentStatus = request.getStatus();

        if (currentStatus.equals(STATUS_REJECTED) || currentStatus.equals(STATUS_CANCELLED)) {
            throw new IllegalStateException("This request is already " + currentStatus);
        }

        if (currentStatus.equals(STATUS_PENDING)) {
            // 1. Mark request as cancelled
            request.setStatus(STATUS_CANCELLED);
            // 2. Release the pending days back to the balance
            leaveBalanceService.releasePendingHold(
                    request.getEmployee(), request.getLeaveType(), request.getDurationDays(), currentYear, request.getId()
            );
            // 3. Mark the Manager/HR inbox items as cancelled
            approvalRoutingService.cancelPendingApprovals(request.getId());
        }
        else if (currentStatus.equals(STATUS_APPROVED)) {
            // 1. Mark request as cancelled
            request.setStatus(STATUS_CANCELLED);
            // 2. Refund the actually deducted days
            leaveBalanceService.rollbackDeduction(
                    request.getEmployee(), request.getLeaveType(), request.getDurationDays(), request.getId(), currentYear
            );
            // 3. Trigger Attendance Reversal
            attendanceSyncService.revertLeaveFromAttendance(request);
        }

        leaveRequestRepository.save(request);
    }
}