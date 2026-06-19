package com.murali.service;

import com.murali.entity.*;
import com.murali.repository.LeaveBalanceRepository;
import com.murali.repository.LeaveBalanceTransactionRepository;
import com.murali.repository.LeaveRequestRepository;
import com.murali.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceTransactionRepository transactionRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogService auditLoggingService;
    private final LeaveRequestRepository leaveRequestRepository;

    public static final String ALLOCATION = "ALLOCATION";
    public static final String PENDING_HOLD = "PENDING_HOLD";
    public static final String HOLD_RELEASE = "HOLD_RELEASE";
    public static final String LEAVE_DEDUCT = "LEAVE_DEDUCT";
    public static final String LEAVE_REFUND = "LEAVE_REFUND";
    public static final String UNPAID_LEAVE_CODE = "UPL-001";

    @Transactional(readOnly = true)
    public List<LeaveBalance> getBalancesForEmployee(Long employeeId, Integer year) {
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year);
    }

    public BigDecimal getEffectiveBalance(LeaveBalance balance) {
        if (balance == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal allocated = balance.getTotalEntitled() != null ? balance.getTotalEntitled() : BigDecimal.ZERO;
        BigDecimal used = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        BigDecimal pending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;

        return allocated.subtract(used).subtract(pending);
    }

    @Transactional
    public void initializeBalancesForEmployee(Employee employee, Integer year, Set<LeaveType> selectedLeaves) {
        java.util.Collection<LeaveType> targetLeaveTypes = (selectedLeaves == null || selectedLeaves.isEmpty()) ? leaveTypeRepository.findAll() : selectedLeaves;

        Set<Long> targetLeaveTypeIds = targetLeaveTypes.stream().map(LeaveType::getId).collect(java.util.stream.Collectors.toSet());

        List<LeaveBalance> existingBalances = leaveBalanceRepository.findByEmployeeIdAndYear(employee.getId(), year);
        Set<Long> existingLeaveTypeIds = existingBalances.stream().map(b -> b.getLeaveType().getId()).collect(java.util.stream.Collectors.toSet());

        List<LeaveBalance> balancesToRemove = new java.util.ArrayList<>();
        for (LeaveBalance balance : existingBalances) {
            if (!targetLeaveTypeIds.contains(balance.getLeaveType().getId())) {

                boolean hasUsed = balance.getUsed() != null && balance.getUsed().compareTo(java.math.BigDecimal.ZERO) > 0;
                boolean hasPending = balance.getPendingDays() != null && balance.getPendingDays().compareTo(java.math.BigDecimal.ZERO) > 0;

                if (hasUsed || hasPending) {
                    throw new IllegalStateException("Cannot remove '" + balance.getLeaveType().getName() + "' because the employee has already used or requested days from it.");
                }
                balancesToRemove.add(balance);
            }
        }

        for (LeaveBalance balanceToRemove : balancesToRemove) {
            String oldState = formatAuditState(balanceToRemove);
            leaveBalanceRepository.delete(balanceToRemove);
            auditLoggingService.saveAuditLog(balanceToRemove.getId(), "DELETED", "leave_balances", oldState, null);
            log.info("Removed unselected leave balance for Employee ID: {}, LeaveType: {}", employee.getId(), balanceToRemove.getLeaveType().getCode());
        }

       for (LeaveType targetType : targetLeaveTypes) {
            if (!existingLeaveTypeIds.contains(targetType.getId())) {
                getOrCreateBalance(employee, targetType, year);
            }
        }

        log.info("Successfully synced leave balances for Employee ID: {} for year {}", employee.getId(), year);
    }

    @Transactional
    public void holdPendingBalance(LeaveRequest request, BigDecimal customNetDaysToHold) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;

        balance.setPendingDays(currentPending.add(customNetDaysToHold));

        leaveBalanceRepository.save(balance);

        recordTransaction(request.getEmployee(), request.getLeaveType(), PENDING_HOLD, customNetDaysToHold, request.getId(), "Pending hold placed for leave request");

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void deduct(LeaveRequest request, BigDecimal actualDaysToDeduct) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(actualDaysToDeduct));

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(actualDaysToDeduct);

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Pending days dropped below zero for request ID: " + request.getId());
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        recordTransaction(request.getEmployee(), request.getLeaveType(), LEAVE_DEDUCT, actualDaysToDeduct, request.getId(), "Leave approved and deducted");

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    public void deduct(LeaveRequest request) {
        deduct(request, request.getDurationDays());
    }

    @Transactional
    public void rollbackDeduction(LeaveRequest request, BigDecimal actualDaysToRefund) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        BigDecimal newUsed = currentUsed.subtract(actualDaysToRefund);

        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Cannot refund more days than used.");
        }
        balance.setUsed(newUsed);
        leaveBalanceRepository.save(balance);

        recordTransaction(request.getEmployee(), request.getLeaveType(), LEAVE_REFUND, actualDaysToRefund, request.getId(), "Leave cancelled and refunded");

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    public void rollbackDeduction(LeaveRequest request) {
        rollbackDeduction(request, request.getDurationDays());
    }

    @Transactional
    public void releasePendingHold(LeaveRequest request, BigDecimal amountToRelease) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(amountToRelease);

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Attempting to release more pending days than exist.");
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        recordTransaction(request.getEmployee(), request.getLeaveType(), HOLD_RELEASE, amountToRelease, request.getId(), "Pending hold released");

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    public void releasePendingHold(LeaveRequest request) {
        releasePendingHold(request, request.getDurationDays());
    }

    @Transactional
    public void deductPenalty(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, String description) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);
        BigDecimal availableBalance = getEffectiveBalance(balance);

        if (availableBalance.compareTo(duration) >= 0) {
            executeDirectPenalty(balance, duration, description);
        } else {
            BigDecimal shortfall = duration.subtract(availableBalance);

            if (availableBalance.compareTo(BigDecimal.ZERO) > 0) {
                executeDirectPenalty(balance, availableBalance, description + " (Partial exhaustion)");
            }

            LeaveType unpaidLeaveType = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Unpaid Leave", UNPAID_LEAVE_CODE).stream().findFirst().orElseThrow(() -> new IllegalStateException("Unpaid Leave type (" + UNPAID_LEAVE_CODE + ") must be configured in the database."));

            LeaveBalance unpaidBalance = getOrCreateBalance(employee, unpaidLeaveType, year);
            executeDirectPenalty(unpaidBalance, shortfall, description + " (Spillover to Unpaid Leave due to empty balance)");
        }
    }

    private void executeDirectPenalty(LeaveBalance balance, BigDecimal duration, String description) {
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(duration));

        leaveBalanceRepository.save(balance);
        recordTransaction(balance.getEmployee(), balance.getLeaveType(), LEAVE_DEDUCT, duration, null, description);

        log.info("Deducted penalty of {} days for Employee ID: {}, LeaveType: {}", duration, balance.getEmployee().getId(), balance.getLeaveType().getCode());
        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    private LeaveBalance getOrCreateBalance(Employee employee, LeaveType leaveType, Integer year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year).orElseGet(() -> {
            LeaveBalance newBalance = new LeaveBalance();
            newBalance.setEmployee(employee);
            newBalance.setLeaveType(leaveType);
            newBalance.setYear(year);

            newBalance.setTotalEntitled(BigDecimal.valueOf(leaveType.getMaxDaysPerYear()));
            newBalance.setUsed(BigDecimal.ZERO);
            newBalance.setPendingDays(BigDecimal.ZERO);

            LeaveBalance savedBalance = leaveBalanceRepository.save(newBalance);

            recordTransaction(employee, leaveType, ALLOCATION, savedBalance.getTotalEntitled(), null, "Initial balance allocated for year " + year);
            log.info("Created missing balance record for Employee ID: {}, LeaveType: {}", employee.getId(), leaveType.getCode());

            auditLoggingService.saveAuditLog(savedBalance.getId(), "CREATED", "leave_balances", null, formatAuditState(savedBalance));
            return savedBalance;
        });
    }

    private void recordTransaction(Employee employee, LeaveType leaveType, String type, BigDecimal days, Long referenceId, String description) {
        LeaveBalanceTransaction transaction = new LeaveBalanceTransaction();
        transaction.setEmployee(employee);
        transaction.setLeaveType(leaveType);
        transaction.setTransactionType(type);
        transaction.setDays(days);
        transaction.setReferenceId(referenceId);
        transaction.setDescription(description);
        transactionRepository.save(transaction);
    }

    private String formatAuditState(LeaveBalance balance) {
        return String.format("{ \"totalEntitled\": %s, \"used\": %s, \"pendingDays\": %s }", balance.getTotalEntitled(), balance.getUsed(), balance.getPendingDays());
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceTransaction> findAllWithDetails() {
        return transactionRepository.findAllWithDetails();
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateApprovedMergedDays(LeaveRequest request) {
        if (request.getMergedLeaves() == null || request.getMergedLeaves().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return request.getMergedLeaves().stream().filter(mergedReq -> "APPROVED".equals(mergedReq.getStatus())).map(LeaveRequest::getDurationDays).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateHeldAmount(LeaveRequest request) {
        BigDecimal approvedDuration = calculateApprovedMergedDays(request);
        BigDecimal held = request.getDurationDays().subtract(approvedDuration);

        return held.compareTo(BigDecimal.ZERO) > 0 ? held : BigDecimal.ZERO;
    }
}