package com.murali.service;

import com.murali.dto.LeaveDurationResultDTO;
import com.murali.entity.*;
import com.murali.entity.enums.CancellationStatus;
import com.murali.entity.enums.LeaveSession;
import com.murali.exception.LeaveMergeException;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final DurationEngineService durationEngineService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveApprovalRuleService ruleService;
    private final EmployeeRepository employeeRepository;
    private final ApprovalRoutingService approvalRoutingService;
    private final AuditLogService auditLoggingService;
    private final AttendanceProcessService attendanceProcessService;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DRAFT = "DRAFT";


    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, DurationEngineService durationEngineService, LeaveBalanceService leaveBalanceService, LeaveApprovalRuleService ruleService, EmployeeRepository employeeRepository, ApprovalRoutingService approvalRoutingService, AuditLogService auditLoggingService, AttendanceProcessService attendanceProcessService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.durationEngineService = durationEngineService;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.approvalRoutingService = approvalRoutingService;
        this.auditLoggingService = auditLoggingService;
        this.attendanceProcessService = attendanceProcessService;
    }

    @Transactional
    public void submitLeaveRequest(Long existingDraftId, Employee detachedEmployee, LeaveType leaveType, LocalDate inputStartDate, LocalDate inputEndDate, String reason, Integer currentYear, LeaveSession inputStartSession, LeaveSession inputEndSession, List<Long> mergedLeaveIds) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId()).orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        LeaveRequest request = (existingDraftId != null) ? leaveRequestRepository.findById(existingDraftId).orElseThrow() : new LeaveRequest();
        String oldState = existingDraftId != null ? String.format("{ \"status\": \"%s\" }", request.getStatus()) : null;

        LocalDate startDate = inputStartDate;
        LocalDate endDate = inputEndDate;
        LeaveSession startSession = inputStartSession;
        LeaveSession endSession = inputEndSession;

        List<LeaveRequest> pendingLeavesToCancelImmediately = new ArrayList<>();
        List<LeaveRequest> allLeavesToLink = new ArrayList<>();
        BigDecimal alreadyDeducted = BigDecimal.ZERO;

        if (mergedLeaveIds != null && !mergedLeaveIds.isEmpty()) {

            for (Long id : mergedLeaveIds) {
                LeaveRequest oldReq = leaveRequestRepository.findById(id).orElseThrow();
                allLeavesToLink.add(oldReq);
                if (!oldReq.getLeaveType().getId().equals(leaveType.getId())) {
                    throw new IllegalArgumentException("Merged leaves must be of the exact same Leave Type.");
                }

                if (oldReq.getStartDate().isBefore(startDate)) {
                    startDate = oldReq.getStartDate();
                    startSession = oldReq.getStartSession();
                }

                if (oldReq.getEndDate().isAfter(endDate)) {
                    endDate = oldReq.getEndDate();
                    endSession = oldReq.getEndSession();
                }

                if (oldReq.getStatus().equals(STATUS_APPROVED)) {
                    alreadyDeducted = alreadyDeducted.add(oldReq.getDurationDays());
                } else if (oldReq.getStatus().startsWith(STATUS_PENDING)) {
                    pendingLeavesToCancelImmediately.add(oldReq);
                }
            }
        }

        boolean hasOverlap;
        if (mergedLeaveIds != null && !mergedLeaveIds.isEmpty()) {
            hasOverlap = leaveRequestRepository.hasOverlappingLeaveIgnoring(employee.getId(), startDate, endDate, mergedLeaveIds);
        } else {
            hasOverlap = leaveRequestRepository.hasOverlappingLeave(employee.getId(), startDate, endDate);
        }

        if (hasOverlap) {
            throw new IllegalArgumentException("This date overlaps with another approved or pending leave.");
        }

        LeaveDurationResultDTO durationResult = durationEngineService.calculateLeaveDuration(startDate, endDate, employee, startSession, endSession, leaveType.getApplySandwichRule());

        BigDecimal duration = durationResult.getNetLeaveDays();
        BigDecimal sandwichPenaltyDays = durationResult.getSandwichPenaltyDays() != null ? durationResult.getSandwichPenaltyDays() : BigDecimal.ZERO;


        LocalDate previousWorkingDay = durationEngineService.getPreviousWorkingDay(startDate, employee);
        LocalDate nextWorkingDay = durationEngineService.getNextWorkingDay(endDate, employee);

        List<LeaveRequest> adjacentLeaves = leaveRequestRepository.findAdjacentLeaves(employee.getId(), previousWorkingDay, nextWorkingDay);

        BigDecimal totalDuration = duration;
        List<LeaveRequest> adjacentPendingLeavesToUpgrade = new ArrayList<>();
        long penaltyDays = 0;

        for (LeaveRequest adj : adjacentLeaves) {
            if (mergedLeaveIds != null && mergedLeaveIds.contains(adj.getId())) {
                continue;
            }

            totalDuration = totalDuration.add(adj.getDurationDays());

            if (adj.getStatus().startsWith(STATUS_PENDING)) {
                adjacentPendingLeavesToUpgrade.add(adj);
            }

            if (leaveType.getApplySandwichRule()) {
                if (adj.getEndDate().isBefore(startDate)) {
                    long gap = java.time.temporal.ChronoUnit.DAYS.between(adj.getEndDate(), startDate) - 1;
                    if (gap > 0) penaltyDays += gap;
                } else if (adj.getStartDate().isAfter(endDate)) {
                    long gap = java.time.temporal.ChronoUnit.DAYS.between(endDate, adj.getStartDate()) - 1;
                    if (gap > 0) penaltyDays += gap;
                }
            }
        }

        if (penaltyDays > 0) {
            duration = duration.add(BigDecimal.valueOf(penaltyDays));
            sandwichPenaltyDays = sandwichPenaltyDays.add(BigDecimal.valueOf(penaltyDays));
            reason = String.format("[SANDWICH PENALTY: %d gap days] - ", penaltyDays) + reason;
        }

        BigDecimal netDurationToHold = duration.subtract(alreadyDeducted);
        if (netDurationToHold.compareTo(BigDecimal.ZERO) < 0) {
            netDurationToHold = BigDecimal.ZERO;
        }

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employee.getId(), currentYear);
        LeaveBalance currentBalance = balances.stream().filter(b -> b.getLeaveType().getId().equals(leaveType.getId())).findFirst().orElse(null);

        BigDecimal effectiveBalance = leaveBalanceService.getEffectiveBalance(currentBalance);

        if (netDurationToHold.compareTo(effectiveBalance) > 0) {
            if (!leaveType.getCode().equals("SL-001") && !leaveType.getCode().equals("EMG-001")) {
                throw new IllegalArgumentException("Insufficient balance.");
            }
            reason = "[WARNING: NEGATIVE BALANCE REQUEST] - " + reason;
        }

        List<LeaveApprovalRule> applicableRules = ruleService.getApplicableRules(leaveType.getId(), totalDuration);
        if (applicableRules.isEmpty()) {
            throw new IllegalStateException("System Configuration Error: No rules found.");
        }

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
        request.setIsSandwichLeave(durationResult.isSandwichLeave());
        request.setSandwichPenaltyDays(sandwichPenaltyDays);
        request.setCancellationStatus(CancellationStatus.NONE);

        LeaveRequest savedRequest = leaveRequestRepository.save(request);
        for (LeaveRequest oldReqToLink : allLeavesToLink) {
            oldReqToLink.setParentLeave(savedRequest);
            leaveRequestRepository.save(oldReqToLink);
        }

        leaveBalanceService.holdPendingBalance(savedRequest, netDurationToHold);
        approvalRoutingService.generateApprovalWorkflow(savedRequest, applicableRules);

        for (LeaveRequest pendingAdj : adjacentPendingLeavesToUpgrade) {
            List<LeaveApprovalRule> upgradedRules = ruleService.getApplicableRules(pendingAdj.getLeaveType().getId(), totalDuration);
            approvalRoutingService.upgradePendingWorkflow(pendingAdj, upgradedRules, savedRequest.getId());
        }

        String action = existingDraftId != null ? "UPDATED" : "CREATED";
        auditLoggingService.saveAuditLog(savedRequest.getId(), action, "leave_requests", oldState, "{...}");

        for (LeaveRequest pendingOldReq : pendingLeavesToCancelImmediately) {
            String oldPendingState = String.format("{ \"status\": \"%s\" }", pendingOldReq.getStatus());

            pendingOldReq.setStatus(STATUS_CANCELLED);
            pendingOldReq.setReason(pendingOldReq.getReason() + " [AUTO-CANCELLED: MERGED INTO REQUEST ID: " + savedRequest.getId() + "]");
            leaveRequestRepository.save(pendingOldReq);

            leaveBalanceService.releasePendingHold(pendingOldReq);
            approvalRoutingService.cancelPendingApprovals(pendingOldReq.getId());

            String newPendingState = String.format("{ \"status\": \"%s\" }", STATUS_CANCELLED);
            auditLoggingService.saveAuditLog(pendingOldReq.getId(), "AUTO_CANCELLED_FOR_MERGE", "leave_requests", oldPendingState, newPendingState);
        }
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
        return leaveRequestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Leave Request not found with ID: " + id));
    }

    @Transactional
    public void systemCancelLeave(Long leaveRequestId, Long requestingEmployeeId) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId).orElseThrow(() -> new IllegalArgumentException("Leave request not found"));

        if (!request.getEmployee().getId().equals(requestingEmployeeId)) {
            throw new SecurityException("You do not have permission to cancel this leave.");
        }

        leaveRequestRepository.findActiveParentLeave(leaveRequestId).ifPresent(parentLeave -> {
            throw new IllegalStateException("This leave cannot be cancelled because it is merged into an active leave request (ID: " + parentLeave.getId() + "). Please cancel the merged request instead.");
        });

        String currentStatus = request.getStatus();
        if (currentStatus.equals(STATUS_REJECTED) || currentStatus.equals(STATUS_CANCELLED)) {
            throw new IllegalStateException("This request is already " + currentStatus);
        }

        String oldState = String.format("{ \"status\": \"%s\" }", currentStatus);

        if (currentStatus.startsWith(STATUS_PENDING)) {
            request.setStatus(STATUS_CANCELLED);
            BigDecimal heldAmount = leaveBalanceService.calculateHeldAmount(request);
            leaveBalanceService.releasePendingHold(request, heldAmount);
            approvalRoutingService.cancelPendingApprovals(request.getId());
        } else if (currentStatus.equals(STATUS_APPROVED)) {
            request.setStatus(STATUS_CANCELLED);
            leaveBalanceService.rollbackDeduction(request);

            LocalDate startDate = request.getStartDate();
            while (!startDate.isAfter(request.getEndDate())) {
                if (startDate.isBefore(LocalDate.now()) || startDate.equals(LocalDate.now())) {
                    attendanceProcessService.recalculateAttendanceForDate(request.getEmployee().getId(), startDate);
                }
                startDate = startDate.plusDays(1);
            }
        }

        LeaveRequest savedRequest = leaveRequestRepository.save(request);
        auditLoggingService.saveAuditLog(leaveRequestId, "CANCELLED", "leave_requests", oldState, String.format("{ \"status\": \"%s\" }", savedRequest.getStatus()));
    }

    @Transactional
    public void requestManualCancellation(Long leaveRequestId, Long requestingEmployeeId, Integer currentYear) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId).orElseThrow(() -> new IllegalArgumentException("Leave request not found"));

        if (!request.getEmployee().getId().equals(requestingEmployeeId)) {
            throw new SecurityException("You do not have permission to cancel this leave.");
        }
        leaveRequestRepository.findActiveParentLeave(leaveRequestId).ifPresent(parentLeave -> {
            throw new IllegalStateException("This leave is currently merged into an active request (ID: " + parentLeave.getId() + "). You must cancel that request instead.");
        });

        String currentStatus = request.getStatus();
        if (currentStatus.equals(STATUS_REJECTED) || currentStatus.equals(STATUS_CANCELLED)) {
            throw new IllegalStateException("This request is already " + currentStatus);
        }

        if (request.getCancellationStatus() == CancellationStatus.PENDING) {
            throw new IllegalStateException("A cancellation request is already pending for this leave.");
        }

        List<LeaveApproval> approvedOriginals = approvalRoutingService.getApprovedOriginals(request.getId());

        if (approvedOriginals.isEmpty()) {
            systemCancelLeave(leaveRequestId, requestingEmployeeId);
        } else {
            request.setCancellationStatus(CancellationStatus.PENDING);
            leaveRequestRepository.save(request);

            approvalRoutingService.generateCancellationWorkflow(request, approvedOriginals);

            auditLoggingService.saveAuditLog(leaveRequestId, "REQUESTED_CANCELLATION", "leave_requests", "{ \"cancellationStatus\": \"NONE\" }", "{ \"cancellationStatus\": \"PENDING\" }");
        }
    }

    @Transactional
    public LeaveRequest saveDraft(Long draftId, Employee detachedEmployee, LeaveType leaveType, LocalDate startDate, LocalDate endDate, String reason, LeaveSession startSession, LeaveSession endSession, boolean applySandwichRule) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId()).orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        LeaveDurationResultDTO durationResult = durationEngineService.calculateLeaveDuration(startDate, endDate, employee, startSession, endSession, leaveType.getApplySandwichRule());
        BigDecimal duration = durationResult.getNetLeaveDays();

        String oldState = null;
        LeaveRequest draft;

        if (draftId != null) {
            draft = leaveRequestRepository.findById(draftId).orElse(new LeaveRequest());
            if (draft.getId() != null) {
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

        draft.setStartSession(startSession);
        draft.setEndSession(endSession);
        draft.setIsSandwichLeave(durationResult.isSandwichLeave());
        draft.setCancellationStatus(CancellationStatus.NONE);

        draft.setStatus(STATUS_DRAFT);
        draft.setCurrentLevel(0);

        LeaveRequest savedDraft = leaveRequestRepository.save(draft);

        String newState = String.format("{ \"status\": \"%s\", \"durationDays\": %s }", savedDraft.getStatus(), savedDraft.getDurationDays());
        String action = (draftId != null && oldState != null) ? "UPDATED" : "CREATED";

        auditLoggingService.saveAuditLog(savedDraft.getId(), action, "leave_requests", oldState, newState);

        return savedDraft;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getMergeConflicts(Long employeeId, LocalDate startDate, LocalDate endDate) {
        Employee emp = employeeRepository.findById(employeeId).orElseThrow();
        LocalDate prevDay = durationEngineService.getPreviousWorkingDay(startDate, emp);
        LocalDate nextDay = durationEngineService.getNextWorkingDay(endDate, emp);
        return leaveRequestRepository.findAdjacentLeaves(employeeId, prevDay, nextDay);
    }

    public void deleteDraft(Long id) {

        LeaveRequest request = (id != null) ? leaveRequestRepository.findById(id).orElseThrow() : null;
        if(request == null){
            throw new IllegalArgumentException("No such Draft Id");
        }
        leaveRequestRepository.delete(request);
    }
}