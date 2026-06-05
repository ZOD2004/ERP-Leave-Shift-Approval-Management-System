package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveBalanceTransaction;
import com.murali.entity.LeaveType;
import com.murali.repository.LeaveBalanceRepository;
import com.murali.repository.LeaveBalanceTransactionRepository;
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
        java.util.Collection<LeaveType> typesToInitialize = (selectedLeaves == null || selectedLeaves.isEmpty())
                ? leaveTypeRepository.findAll()
                : selectedLeaves;

        for (LeaveType leaveType : typesToInitialize) {
            getOrCreateBalance(employee, leaveType, year);
        }

        log.info("Initialized specific leave balances for Employee ID: {} for year {}", employee.getId(), year);
    }

    @Transactional
    public void holdPendingBalance(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, Long referenceId) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);
        String oldState = formatAuditState(balance);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        balance.setPendingDays(currentPending.add(duration));

        leaveBalanceRepository.save(balance);
        recordTransaction(employee, leaveType, PENDING_HOLD, duration, referenceId, "Pending hold placed for new leave request");

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void deduct(Employee employee, LeaveType leaveType, BigDecimal duration, Long leaveRequestId, Integer year) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(duration));

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(duration);

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Pending days dropped below zero for request ID: " + leaveRequestId);
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, LEAVE_DEDUCT, duration, leaveRequestId, "Leave approved and deducted from balance");
        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void rollbackDeduction(Employee employee, LeaveType leaveType, BigDecimal duration, Long originalLeaveRequestId, Integer year) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        BigDecimal newUsed = currentUsed.subtract(duration);

        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Cannot refund more days than used.");
        }
        balance.setUsed(newUsed);
        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, LEAVE_REFUND, duration, originalLeaveRequestId, "Leave cancelled and days refunded");
        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void releasePendingHold(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, Long referenceId) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);
        String oldState = formatAuditState(balance);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(duration);

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Attempting to release more pending days than exist.");
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, HOLD_RELEASE, duration, referenceId, "Pending hold released due to rejection or cancellation");
        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    // 2. SMART PENALTY DEDUCTION WITH UNPAID LEAVE SPILLOVER
    @Transactional
    public void deductPenalty(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, String description) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);
        BigDecimal availableBalance = getEffectiveBalance(balance);

        // If they have enough balance, deduct normally
        if (availableBalance.compareTo(duration) >= 0) {
            executeDirectPenalty(balance, duration, description);
        } else {
            // SPILLOVER LOGIC: They don't have enough. Drain what they have, push the rest to Unpaid Leave.
            BigDecimal shortfall = duration.subtract(availableBalance);

            if (availableBalance.compareTo(BigDecimal.ZERO) > 0) {
                executeDirectPenalty(balance, availableBalance, description + " (Partial exhaustion)");
            }

            LeaveType unpaidLeaveType = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Unpaid Leave", UNPAID_LEAVE_CODE)
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unpaid Leave type (" + UNPAID_LEAVE_CODE + ") must be configured in the database."));

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

    // 3. DYNAMIC ENTITLEMENT CREATION
    private LeaveBalance getOrCreateBalance(Employee employee, LeaveType leaveType, Integer year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                .orElseGet(() -> {
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
        return String.format("{ \"totalEntitled\": %s, \"used\": %s, \"pendingDays\": %s }",
                balance.getTotalEntitled(), balance.getUsed(), balance.getPendingDays());
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceTransaction> findAllWithDetails() {
        return transactionRepository.findAllWithDetails();
    }
}