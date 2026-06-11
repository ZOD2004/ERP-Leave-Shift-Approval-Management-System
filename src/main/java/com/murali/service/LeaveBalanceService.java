package com.murali.service;

import com.murali.entity.*;
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
                ? leaveTypeRepository.findAll():selectedLeaves;

        for (LeaveType leaveType : typesToInitialize) {
            getOrCreateBalance(employee, leaveType, year);
        }

        log.info("Initialized specific leave balances for Employee ID: {} for year {}", employee.getId(), year);
    }

    @Transactional
    public void holdPendingBalance(LeaveRequest request, BigDecimal customNetDaysToHold) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;

        balance.setPendingDays(currentPending.add(customNetDaysToHold));

        leaveBalanceRepository.save(balance);

        recordTransaction(request.getEmployee(), request.getLeaveType(), PENDING_HOLD, customNetDaysToHold, request.getId(), "Pending hold placed for new/merged leave request");

        if (request.getSandwichPenaltyDays().compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(request.getEmployee(), request.getLeaveType(), PENDING_HOLD, request.getSandwichPenaltyDays(), request.getId(), "Pending hold for cross-request sandwich penalty");
        }

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void deduct(LeaveRequest request) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(request.getDurationDays()));

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(request.getDurationDays());

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Pending days dropped below zero for request ID: " + request.getId());
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        BigDecimal netDays = request.getDurationDays().subtract(request.getSandwichPenaltyDays());
        recordTransaction(request.getEmployee(), request.getLeaveType(), LEAVE_DEDUCT, netDays, request.getId(), "Leave approved and net days deducted");

        if (request.getSandwichPenaltyDays().compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(request.getEmployee(), request.getLeaveType(), LEAVE_DEDUCT, request.getSandwichPenaltyDays(), request.getId(), "Sandwich penalty days deducted");
        }

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void rollbackDeduction(LeaveRequest request) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        BigDecimal newUsed = currentUsed.subtract(request.getDurationDays());

        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Cannot refund more days than used.");
        }
        balance.setUsed(newUsed);
        leaveBalanceRepository.save(balance);

        BigDecimal netDays = request.getDurationDays().subtract(request.getSandwichPenaltyDays());
        recordTransaction(request.getEmployee(), request.getLeaveType(), LEAVE_REFUND, netDays, request.getId(), "Leave cancelled and net days refunded");

        if (request.getSandwichPenaltyDays().compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(request.getEmployee(), request.getLeaveType(), LEAVE_REFUND, request.getSandwichPenaltyDays(), request.getId(), "Leave cancelled and sandwich penalty refunded");
        }

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
    }

    @Transactional
    public void releasePendingHold(LeaveRequest request) {
        LeaveBalance balance = getOrCreateBalance(request.getEmployee(), request.getLeaveType(), request.getStartDate().getYear());
        String oldState = formatAuditState(balance);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(request.getDurationDays());

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Attempting to release more pending days than exist.");
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        BigDecimal netDays = request.getDurationDays().subtract(request.getSandwichPenaltyDays());
        recordTransaction(request.getEmployee(), request.getLeaveType(), HOLD_RELEASE, netDays, request.getId(), "Pending hold released for net days");

        if (request.getSandwichPenaltyDays().compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(request.getEmployee(), request.getLeaveType(), HOLD_RELEASE, request.getSandwichPenaltyDays(), request.getId(), "Pending hold released for sandwich penalty");
        }

        auditLoggingService.saveAuditLog(balance.getId(), "UPDATED", "leave_balances", oldState, formatAuditState(balance));
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