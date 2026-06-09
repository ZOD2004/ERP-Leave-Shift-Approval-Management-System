package com.murali.service;

import com.murali.dto.LeaveDurationResultDTO;
import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.exception.PastDateException;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final DurationEngineService durationEngineService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveApprovalRuleService ruleService;
    private final EmployeeRepository employeeRepository;
    private final ApprovalRoutingService approvalRoutingService;
    private final AttendanceCronJobService attendanceCronJobService;
    private final AuditLogService auditLoggingService;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DRAFT = "DRAFT";

    private static final List<String> BACKDATED_ALLOWED_CODES = List.of("EMG-001", "SL-001");

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, DurationEngineService durationEngineService, LeaveBalanceService leaveBalanceService, LeaveApprovalRuleService ruleService, EmployeeRepository employeeRepository,ApprovalRoutingService approvalRoutingService, AttendanceCronJobService attendanceCronJobService, AuditLogService auditLoggingService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.durationEngineService = durationEngineService;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.attendanceCronJobService = attendanceCronJobService;
        this.approvalRoutingService = approvalRoutingService;
        this.auditLoggingService = auditLoggingService;
    }

    @Transactional
    public void submitLeaveRequest(Long existingDraftId, Employee detachedEmployee, LeaveType leaveType,
                                   LocalDate startDate, LocalDate endDate,
                                   String reason, Integer currentYear,
                                   LeaveSession startSession, LeaveSession endSession,
                                   boolean applySandwichRule) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        // 1. Overlap Validation
        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(employee.getId(), startDate, endDate);
        if (hasOverlap) {
            throw new IllegalArgumentException("This date is already an approved or pending leave, so you cannot apply.");
        }

        // 2. Duration Engine Calculation
        LeaveDurationResultDTO durationResult = durationEngineService.calculateLeaveDuration(
                startDate, endDate, employee, startSession, endSession, leaveType.getApplySandwichRule()
        );
        BigDecimal duration = durationResult.getNetLeaveDays();

        // 3. Back-dating Validation
        if (startDate.isBefore(LocalDate.now()) && !BACKDATED_ALLOWED_CODES.contains(leaveType.getCode().toUpperCase())) {
            throw new PastDateException("Back-dating is only permitted for Sick or Emergency leaves.");
        }

        // 4. Calculate Consecutive "Chain" Duration for Rules
        LocalDate previousWorkingDay = durationEngineService.getPreviousWorkingDay(startDate, employee);
        LocalDate nextWorkingDay = durationEngineService.getNextWorkingDay(endDate, employee);

        List<LeaveRequest> adjacentLeaves = leaveRequestRepository.findAdjacentLeaves(
                employee.getId(), previousWorkingDay, nextWorkingDay
        );

        BigDecimal effectiveChainDuration = duration;
        long crossRequestPenaltyDays = 0;

        // NEW: Separate pending leaves that need auto-upgrading
        List<LeaveRequest> adjacentPendingLeavesToUpgrade = new ArrayList<>();

        for (LeaveRequest adj : adjacentLeaves) {
            // Add to total chain duration regardless of whether it is PENDING or APPROVED
            effectiveChainDuration = effectiveChainDuration.add(adj.getDurationDays());

            // If it is PENDING, we will need to upgrade its workflow later
            if (adj.getStatus().startsWith(STATUS_PENDING)) {
                adjacentPendingLeavesToUpgrade.add(adj);
            }

            if (leaveType.getApplySandwichRule()) {
                if (adj.getEndDate().isBefore(startDate)) {
                    long gap = java.time.temporal.ChronoUnit.DAYS.between(adj.getEndDate(), startDate) - 1;
                    if (gap > 0) crossRequestPenaltyDays += gap;
                }
                else if (adj.getStartDate().isAfter(endDate)) {
                    long gap = java.time.temporal.ChronoUnit.DAYS.between(endDate, adj.getStartDate()) - 1;
                    if (gap > 0) crossRequestPenaltyDays += gap;
                }
            }
        }

        boolean isSandwichLeave = durationResult.isSandwichLeave();
        BigDecimal sandwichPenaltyDays = durationResult.getSandwichPenaltyDays() != null
                ? durationResult.getSandwichPenaltyDays() : BigDecimal.ZERO;

        if (crossRequestPenaltyDays > 0) {
            duration = duration.add(BigDecimal.valueOf(crossRequestPenaltyDays));
            isSandwichLeave = true;
            sandwichPenaltyDays = sandwichPenaltyDays.add(BigDecimal.valueOf(crossRequestPenaltyDays));
            reason = String.format("[SANDWICH PENALTY: %d gap days] - ", crossRequestPenaltyDays) + reason;
        }

        // 5. Strict Balance Validation
        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employee.getId(), currentYear);
        LeaveBalance currentBalance = balances.stream()
                .filter(b -> b.getLeaveType().getId().equals(leaveType.getId()))
                .findFirst().orElse(null);

        BigDecimal effectiveBalance = leaveBalanceService.getEffectiveBalance(currentBalance);
        if (duration.compareTo(effectiveBalance) > 0) {
            if (!leaveType.getCode().equals("SL-001") && !leaveType.getCode().equals("EMG-001")) {
                throw new IllegalArgumentException("Insufficient balance.");
            }
            reason = "[WARNING: NEGATIVE BALANCE REQUEST] - " + reason;
        }

        // 6. Get Rules based on the combined CHAIN duration for the NEW request
        List<LeaveApprovalRule> applicableRules = ruleService.getApplicableRules(leaveType.getId(), effectiveChainDuration);
        if (applicableRules.isEmpty()) {
            throw new IllegalStateException("System Configuration Error: No rules found.");
        }

        // 7. Save Entity
        LeaveRequest request = (existingDraftId != null)
                ? leaveRequestRepository.findById(existingDraftId).orElseThrow() : new LeaveRequest();
        String oldState = existingDraftId != null ? String.format("{ \"status\": \"%s\" }", request.getStatus()) : null;

        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDurationDays(duration);
        request.setStartSession(startSession);
        request.setEndSession(endSession);
        request.setReason(reason);
        request.setStatus(STATUS_PENDING);
        request.setCurrentLevel(1);
        request.setIsSandwichLeave(isSandwichLeave);
        request.setSandwichPenaltyDays(sandwichPenaltyDays);

        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        // 8. Execute Side-Effects
        leaveBalanceService.holdPendingBalance(savedRequest);
        approvalRoutingService.generateApprovalWorkflow(savedRequest, applicableRules);

        // 9. NEW: Auto-Upgrade adjacent PENDING leaves
        for (LeaveRequest pendingAdj : adjacentPendingLeavesToUpgrade) {
            // Get the rules for the adjacent leave's specific type, but using the NEW combined duration
            List<LeaveApprovalRule> upgradedRules = ruleService.getApplicableRules(pendingAdj.getLeaveType().getId(), effectiveChainDuration);
            approvalRoutingService.upgradePendingWorkflow(pendingAdj, upgradedRules, savedRequest.getId());
        }

        String action = existingDraftId != null ? "UPDATED" : "CREATED";
        auditLoggingService.saveAuditLog(savedRequest.getId(), action, "leave_requests", oldState, "{...}");
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getLeaveHistoryForEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public long countPendingRequests() {
        return leaveRequestRepository.countByStatus(STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public long getActiveLeavesCountForDate(LocalDate date) {
        return leaveRequestRepository.countActiveLeavesForDate(date);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getDraftsForEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdAndStatusOrderByIdDesc(employeeId, STATUS_DRAFT);
    }

    @Transactional(readOnly = true)
    public LeaveRequest findById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave Request not found with ID: " + id));
    }

    @Transactional
    public void cancelLeaveRequest(Long leaveRequestId, Long requestingEmployeeId, Integer currentYear) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found"));

        if (!request.getEmployee().getId().equals(requestingEmployeeId)) {
            throw new SecurityException("You do not have permission to cancel this leave.");
        }

        String currentStatus = request.getStatus();
        if (currentStatus.equals(STATUS_REJECTED) || currentStatus.equals(STATUS_CANCELLED)) {
            throw new IllegalStateException("This request is already " + currentStatus);
        }

        String oldState = String.format("{ \"status\": \"%s\" }", currentStatus);

        if (currentStatus.startsWith(STATUS_PENDING)) {
            request.setStatus(STATUS_CANCELLED);
            leaveBalanceService.releasePendingHold(request);
            approvalRoutingService.cancelPendingApprovals(request.getId());
        }
        else if (currentStatus.equals(STATUS_APPROVED)) {
            request.setStatus(STATUS_CANCELLED);
            leaveBalanceService.rollbackDeduction(request);

            // Loop through dates and let the Cron Job fix the Attendance table perfectly
            LocalDate cursor = request.getStartDate();
            while (!cursor.isAfter(request.getEndDate())) {
                if (cursor.isBefore(LocalDate.now()) || cursor.equals(LocalDate.now())) {
                    attendanceCronJobService.recalculateAttendanceForDate(request.getEmployee().getId(), cursor);
                }
                cursor = cursor.plusDays(1);
            }
        }

        LeaveRequest savedRequest = leaveRequestRepository.save(request);
        auditLoggingService.saveAuditLog(leaveRequestId, "CANCELLED", "leave_requests", oldState,
                String.format("{ \"status\": \"%s\" }", savedRequest.getStatus()));
    }

    // Fixed: Updated method signature and implementation to match new DTO and Entity properties
    @Transactional
    public LeaveRequest saveOrUpdateDraft(Long draftId, Employee detachedEmployee, LeaveType leaveType,
                                          LocalDate startDate, LocalDate endDate, String reason,
                                          LeaveSession startSession, LeaveSession endSession,
                                          boolean applySandwichRule) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        // Fixed: Use calculateLeaveDuration and DTO
        LeaveDurationResultDTO durationResult = durationEngineService.calculateLeaveDuration(
                startDate, endDate, employee, startSession, endSession, leaveType.getApplySandwichRule()
        );
        BigDecimal duration = durationResult.getNetLeaveDays();

        String oldState = null;
        LeaveRequest draft;

        if (draftId != null) {
            draft = leaveRequestRepository.findById(draftId).orElse(new LeaveRequest());
            if(draft.getId() != null) {
                oldState = String.format("{ \"status\": \"%s\", \"durationDays\": %s }", draft.getStatus(), draft.getDurationDays());
            }
        } else {
            draft = new LeaveRequest();
        }

        draft.setEmployee(employee);
        draft.setLeaveType(leaveType);
        draft.setStartDate(startDate);
        draft.setEndDate(endDate);
        draft.setDurationDays(duration);
        draft.setReason(reason != null ? reason : "");

        // Fixed: set new session variables
        draft.setStartSession(startSession);
        draft.setEndSession(endSession);
        draft.setIsSandwichLeave(durationResult.isSandwichLeave());

        draft.setStatus(STATUS_DRAFT);
        draft.setCurrentLevel(0);

        LeaveRequest savedDraft = leaveRequestRepository.save(draft);

        String newState = String.format("{ \"status\": \"%s\", \"durationDays\": %s }", savedDraft.getStatus(), savedDraft.getDurationDays());
        String action = (draftId != null && oldState != null) ? "UPDATED" : "CREATED";

        auditLoggingService.saveAuditLog(savedDraft.getId(), action, "leave_requests", oldState, newState);

        return savedDraft;
    }
}