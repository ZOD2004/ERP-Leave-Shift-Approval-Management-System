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

    private final ApprovalRoutingService approvalRoutingService;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final List<String> BACKDATED_ALLOWED_CODES = List.of("EMG-001", "SL-001");

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, DurationEngineService durationEngineService, LeaveBalanceService leaveBalanceService, LeaveApprovalRuleService ruleService, EmployeeRepository employeeRepository, ApprovalRoutingService approvalRoutingService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.durationEngineService = durationEngineService;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.approvalRoutingService = approvalRoutingService;
    }

    @Transactional
    public LeaveRequest submitLeaveRequest(Employee detachedEmployee, LeaveType leaveType,
                                           LocalDate startDate, LocalDate endDate,
                                           String reason, Integer currentYear) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

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
            String code = leaveType.getCode().toUpperCase();
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

        return savedRequest;
    }

    /**
     * Requirement: Fetch Leave History for an Employee
     */
    @Transactional(readOnly = true)
    public List<LeaveRequest> getLeaveHistoryForEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingRequestsOrderByDate(int limit) {
        // PageRequest.of(pageNumber, pageSize) acts as our dynamic LIMIT clause.
        // We want the 0th page, and 'limit' amount of items.
        return leaveRequestRepository.findPendingRequests(STATUS_PENDING, PageRequest.of(0, limit));
    }

    /**
     * Dashboard KPI: Get the total number of requests currently awaiting approval across the company.
     */
    @Transactional(readOnly = true)
    public long countPendingRequests() {
        return leaveRequestRepository.countByStatus(STATUS_PENDING);
    }

    /**
     * Dashboard KPI: See how many people are officially off-work today.
     *
     * @param date The date to check (usually LocalDate.now())
     */
    @Transactional(readOnly = true)
    public long getActiveLeavesCountForDate(LocalDate date) {
        return leaveRequestRepository.countActiveLeavesForDate(date);
    }
}