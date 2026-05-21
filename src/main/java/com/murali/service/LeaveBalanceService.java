package com.murali.service;

import lombok.extern.slf4j.Slf4j;
import com.murali.entity.Employee;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveBalanceTransaction;
import com.murali.entity.LeaveType;
import com.murali.entity.AuditLog;
import com.murali.repository.LeaveBalanceRepository;
import com.murali.repository.LeaveBalanceTransactionRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.AuditLogRepository;
import com.murali.service.SecurityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveBalanceTransactionRepository transactionRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public static final String ALLOCATION = "ALLOCATION";
    public static final String PENDING_HOLD = "PENDING_HOLD";
    public static final String HOLD_RELEASE = "HOLD_RELEASE";
    public static final String LEAVE_DEDUCT = "LEAVE_DEDUCT";
    public static final String LEAVE_REFUND = "LEAVE_REFUND";

    public LeaveBalanceService(LeaveBalanceRepository leaveBalanceRepository,
                               LeaveBalanceTransactionRepository transactionRepository,
                               LeaveTypeRepository leaveTypeRepository,
                               AuditLogRepository auditLogRepository,
                               SecurityService securityService) {
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.transactionRepository = transactionRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
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
                        ALLOCATION,
                        balance.getTotalEntitled(),
                        null,
                        "Initial balance allocated for year " + year
                );

                log.info("Initialized leave balance for Employee ID: {}, LeaveType: {}, Year: {}", employee.getId(), leaveType.getCode(), year);
                saveAuditLog(balance.getId(), "INITIALIZED", "leave_balances", "Initialized balance for employee ID " + employee.getId() + " and leave type " + leaveType.getCode());
            }
        }
    }

    @Transactional
    public void holdPendingBalance(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, Long referenceId) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentPending = balance.getPendingDays() != null ? balance.getPendingDays() : BigDecimal.ZERO;
        balance.setPendingDays(currentPending.add(duration));

        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, PENDING_HOLD, duration, referenceId, "Pending hold placed for new leave request");

        log.info("Held {} pending days for Employee ID: {}, LeaveType: {}", duration, employee.getId(), leaveType.getCode());
        saveAuditLog(balance.getId(), "UPDATED", "leave_balances", "Held " + duration + " pending days for reference ID " + referenceId);
    }

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

        recordTransaction(employee, leaveType, LEAVE_DEDUCT, duration, leaveRequestId, "Leave approved and deducted from balance");

        log.info("Deducted {} days from balance for Employee ID: {}, LeaveType: {}", duration, employee.getId(), leaveType.getCode());
        saveAuditLog(balance.getId(), "UPDATED", "leave_balances", "Deducted " + duration + " days for leave request ID " + leaveRequestId);
    }

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
                LEAVE_REFUND,
                duration,
                originalLeaveRequestId,
                "Leave cancelled and days refunded to available balance"
        );

        log.info("Refunded {} days to balance for Employee ID: {}, LeaveType: {}", duration, employee.getId(), leaveType.getCode());
        saveAuditLog(balance.getId(), "UPDATED", "leave_balances", "Refunded " + duration + " days for cancelled leave request ID " + originalLeaveRequestId);
    }

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
                HOLD_RELEASE,
                duration,
                referenceId,
                "Pending hold released due to rejection or pre-approval cancellation"
        );

        log.info("Released {} pending days for Employee ID: {}, LeaveType: {}", duration, employee.getId(), leaveType.getCode());
        saveAuditLog(balance.getId(), "UPDATED", "leave_balances", "Released " + duration + " pending days for reference ID " + referenceId);
    }

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
                    LeaveBalance savedBalance = leaveBalanceRepository.save(newBalance);

                    log.info("Created missing balance record for Employee ID: {}, LeaveType: {}", employee.getId(), leaveType.getCode());
                    saveAuditLog(savedBalance.getId(), "CREATED", "leave_balances", "Created missing balance record for year " + year);

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

    @Transactional
    public void deductPenalty(Employee employee, LeaveType leaveType, BigDecimal duration, Integer year, String description) {
        LeaveBalance balance = getOrCreateBalance(employee, leaveType, year);

        BigDecimal currentUsed = balance.getUsed() != null ? balance.getUsed() : BigDecimal.ZERO;
        balance.setUsed(currentUsed.add(duration));

        leaveBalanceRepository.save(balance);

        recordTransaction(employee, leaveType, LEAVE_DEDUCT, duration, null, description);

        log.info("Deducted penalty of {} days for Employee ID: {}, LeaveType: {}", duration, employee.getId(), leaveType.getCode());
        saveAuditLog(balance.getId(), "UPDATED", "leave_balances", "Deducted penalty of " + duration + " days. Description: " + description);
    }

    private void saveAuditLog(Long recordId, String action, String tableAffected, String details) {
        try {
            String username = "SYSTEM";
            String role = "SYSTEM";

            if (securityService.getPrincipal() != null) {
                username = securityService.getPrincipal().getUsername();
                if (securityService.getAuthentication() != null && !securityService.getAuthentication().getAuthorities().isEmpty()) {
                    role = securityService.getAuthentication().getAuthorities().iterator().next().getAuthority();
                }
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setRole(role);
            auditLog.setRecordId(recordId);
            auditLog.setAction(action);
            auditLog.setTableAffected(tableAffected);
            auditLog.setDetails(details);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for leave balance record {}: {}", recordId, e.getMessage());
        }
    }
}