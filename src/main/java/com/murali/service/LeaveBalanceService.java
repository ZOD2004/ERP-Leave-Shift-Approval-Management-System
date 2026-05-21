package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveBalanceTransaction;
import com.murali.entity.LeaveType;
import com.murali.repository.LeaveBalanceRepository;
import com.murali.repository.LeaveBalanceTransactionRepository;
import com.murali.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceTransactionRepository transactionRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public static final String TX_ALLOCATION = "ALLOCATION";
    public static final String TX_PENDING_HOLD = "PENDING_HOLD";
    public static final String TX_HOLD_RELEASE = "HOLD_RELEASE";
    public static final String TX_LEAVE_DEDUCT = "LEAVE_DEDUCT";
    public static final String TX_LEAVE_REFUND = "LEAVE_REFUND";

    public LeaveBalanceService(LeaveBalanceRepository leaveBalanceRepository,
                               LeaveBalanceTransactionRepository transactionRepository,
                               LeaveTypeRepository leaveTypeRepository) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.transactionRepository = transactionRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

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
    public void initializeBalancesForEmployee(Employee employee, Integer year) {
        java.util.Set<LeaveType> allowedLeaveTypes = employee.getApplicableLeaveTypes();
        if (allowedLeaveTypes == null || allowedLeaveTypes.isEmpty()) {
            return;
        }

        for (LeaveType leaveType : allowedLeaveTypes) {
            boolean exists = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                    employee.getId(), leaveType.getId(), year).isPresent();

            if (!exists) {
                LeaveBalance balance = new LeaveBalance();
                balance.setEmployee(employee);
                balance.setLeaveType(leaveType);
                balance.setYear(year);

                balance.setTotalEntitled(BigDecimal.valueOf(leaveType.getMaxDaysPerYear()));
                balance.setUsed(BigDecimal.ZERO);
                balance.setPendingDays(BigDecimal.ZERO);

                leaveBalanceRepository.save(balance);

                recordTransaction(
                        employee,
                        leaveType,
                        TX_ALLOCATION,
                        balance.getTotalEntitled(),
                        null,
                        "Initial balance allocated for year " + year
                );
            }
        }
    }
    @Transactional
    public void holdPendingBalance(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, Long referenceId) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        balance.setPendingDays(currentPending.add(duration));

        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, TX_PENDING_HOLD, duration, referenceId, "Pending hold placed for new leave request");
    }

    /**
     * 5. Transactional Update: Deduct approved days and release pending hold (Final Approval)
     */
    @Transactional
    public void deduct(Employee employee, LeaveType leaveType, BigDecimal duration, Long leaveRequestId, Integer year) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(duration));

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(duration);

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Pending days dropped below zero for request ID: " + leaveRequestId);
        }

        balance.setPendingDays(newPending);

        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, TX_LEAVE_DEDUCT, duration, leaveRequestId, "Leave approved and deducted from balance");
    }

    /**
     * 6. Rollback Logic: Handle Cancellations (For requests that were ALREADY approved)
     */
    //TODO: to be implemented on cancel leave
    @Transactional
    public void rollbackDeduction(Employee employee, LeaveType leaveType, BigDecimal duration, Long originalLeaveRequestId, Integer year) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        BigDecimal newUsed = currentUsed.subtract(duration);

        if (newUsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Cannot refund more days than used.");
        }
        balance.setUsed(newUsed);
        leaveBalanceRepository.save(balance);

        recordTransaction(
                employee,
                leaveType,
                TX_LEAVE_REFUND,
                duration,
                originalLeaveRequestId,
                "Leave cancelled and days refunded to available balance"
        );
    }

    /**
     * 7. Transactional Update: Release pending hold (When a request is Rejected or Cancelled BEFORE final approval)
     */
    @Transactional
    public void releasePendingHold(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, Long referenceId) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        BigDecimal newPending = currentPending.subtract(duration);

        if (newPending.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Data Corruption Alert: Attempting to release more pending days than exist.");
        }

        balance.setPendingDays(newPending);
        leaveBalanceRepository.save(balance);

        recordTransaction(
                employee,
                leaveType,
                TX_HOLD_RELEASE,
                duration,
                referenceId,
                "Pending hold released due to rejection or pre-approval cancellation"
        );
    }

    /**
     * Utility: Fetch existing balance or create an empty one safely
     */
    private LeaveBalance getOrCreateBalance(Employee employee, LeaveType leaveType, Integer year) {
        return leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employee.getId(), leaveType.getId(), year)
                .orElseGet(() -> {
                    LeaveBalance newBalance = new LeaveBalance();
                    newBalance.setEmployee(employee);
                    newBalance.setLeaveType(leaveType);
                    newBalance.setYear(year);
                    newBalance.setTotalEntitled(BigDecimal.ZERO);
                    newBalance.setUsed(BigDecimal.ZERO);
                    newBalance.setPendingDays(BigDecimal.ZERO);
                    return leaveBalanceRepository.save(newBalance);
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

    @Transactional
    public void deductPenalty(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, String description) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(duration));

        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, TX_LEAVE_DEDUCT, duration, null, description);
    }
}
