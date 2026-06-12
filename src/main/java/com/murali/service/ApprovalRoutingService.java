package com.murali.service;

import com.murali.entity.*;
import com.murali.entity.enums.ApprovalType;
import com.murali.entity.enums.CancellationStatus;
import com.murali.repository.LeaveApprovalRepository;
import com.murali.repository.LeaveRequestRepository;
import com.murali.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ApprovalRoutingService {

    private final LeaveApprovalRepository leaveApprovalRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveApprovalRuleService ruleService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final AttendanceCronJobService attendanceCronJobService;
    private final DurationEngineService durationEngineService;

    public static final String ACTION_REJECTED = "REJECTED";
    public static final String ACTION_APPROVED = "APPROVED";
    public static final String ACTION_PENDING = "PENDING";
    public static final String ACTION_CANCELLED_BY_SIBLING = "HANDLED_BY_SIBLING";

    public ApprovalRoutingService(LeaveApprovalRepository leaveApprovalRepository, LeaveRequestRepository leaveRequestRepository, LeaveBalanceService leaveBalanceService, LeaveApprovalRuleService ruleService, AuditLogService auditLogService, UserRepository userRepository, AttendanceCronJobService attendanceCronJobService, DurationEngineService durationEngineService) {
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.attendanceCronJobService = attendanceCronJobService;
        this.durationEngineService = durationEngineService;
    }

    @Transactional
    public void generateApprovalWorkflow(LeaveRequest request, List<LeaveApprovalRule> applicableRules) {
        String oldState = "{ \"status\": \"SUBMITTED\", \"currentLevel\": 0 }";

        request.setCurrentLevel(1);
        request.setStatus("PENDING LVL 1");
        leaveRequestRepository.save(request);

        generateApprovalsForLevel(request, 1, applicableRules);

        log.info("Workflow started for Leave Request ID: {}", request.getId());

        String newState = String.format("{ \"status\": \"%s\", \"currentLevel\": %d }", request.getStatus(), request.getCurrentLevel());
        auditLogService.saveAuditLog(request.getId(), "WORKFLOW_STARTED", "LeaveRequest", oldState, newState);
    }

    private void generateApprovalsForLevel(LeaveRequest request, int targetLevel, List<LeaveApprovalRule> applicableRules) {
        LeaveApprovalRule levelRule = null;

        for (LeaveApprovalRule rule : applicableRules) {
            if (rule.getApprovalLevel() == targetLevel) {
                levelRule = rule;
                break;
            }
        }

        Role requiredRole = null;
        if (levelRule != null) {
            requiredRole = levelRule.getRequiredRole();
        }

        if (requiredRole == null) {
            throw new IllegalStateException("Cannot generate workflow for level " + targetLevel + ": No rule found.");
        }

        List<User> approvers = resolveApprovers(request.getEmployee(), requiredRole);

        for (User approver : approvers) {
            createApprovalRecord(request, approver, targetLevel, ApprovalType.ORIGINAL);
        }
    }

    private void createApprovalRecord(LeaveRequest request, User approver, int level, ApprovalType type) {
        LeaveApproval approval = new LeaveApproval();
        approval.setLeaveRequest(request);
        approval.setApprover(approver);
        approval.setApprovalLevel(level);
        approval.setAction(ACTION_PENDING);
        approval.setApprovalType(type);
        leaveApprovalRepository.save(approval);

        String state = String.format("{ \"approverId\": %d, \"level\": %d, \"action\": \"%s\", \"type\": \"%s\" }", approver.getId(), level, ACTION_PENDING, type.name());
        auditLogService.saveAuditLog(approval.getId(), "CREATE_QUEUE_ITEM", "LeaveApproval", null, state);
    }

    @Transactional
    public void processApprovalAction(Long leaveApprovalId, String action, String comments, User actor) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action cannot be null or empty");
        }
        String normalizedAction = action.toUpperCase();

        if (!normalizedAction.equals(ACTION_APPROVED) && !normalizedAction.equals(ACTION_REJECTED)) {
            throw new IllegalArgumentException("Invalid action: " + action);
        }

        LeaveApproval approval = leaveApprovalRepository.findById(leaveApprovalId).orElseThrow(() -> new IllegalArgumentException("Approval record not found"));

        LeaveRequest request = approval.getLeaveRequest();

        if (approval.getApprovalType() == ApprovalType.ORIGINAL && request.getCancellationStatus() == CancellationStatus.PENDING) {
            throw new IllegalStateException("Action denied: The employee has requested to cancel this leave. Please refresh your inbox.");
        }

        if (!approval.getApprover().getId().equals(actor.getId())) {
            throw new SecurityException("You are not authorized to process this approval step.");
        }

        if (!ACTION_PENDING.equals(approval.getAction())) {
            throw new IllegalStateException("This approval step has already been processed.");
        }

        String safeOldComments = (approval.getComments() != null) ? approval.getComments().replace("\"", "\\\"") : "";
        String oldApprovalState = String.format("{ \"action\": \"%s\", \"comments\": \"%s\" }", approval.getAction(), safeOldComments);

        approval.setAction(normalizedAction);
        approval.setComments(comments);
        approval.setActedAt(LocalDateTime.now());
        leaveApprovalRepository.save(approval);

        String safeNewComments = (comments != null) ? comments.replace("\"", "\\\"") : "";
        String newApprovalState = String.format("{ \"action\": \"%s\", \"comments\": \"%s\" }", approval.getAction(), safeNewComments);
        auditLogService.saveAuditLog(leaveApprovalId, normalizedAction, "LeaveApproval", oldApprovalState, newApprovalState);

        List<LeaveApproval> approvals = leaveApprovalRepository.findByLeaveRequestIdAndAction(request.getId(), ACTION_PENDING);

        List<LeaveApproval> siblings = new ArrayList<>();

        for (LeaveApproval a : approvals) {
            if (a.getApprovalLevel().equals(approval.getApprovalLevel()) && !a.getId().equals(approval.getId()) && a.getApprovalType() == approval.getApprovalType()) {
                siblings.add(a);
            }
        }

        for (LeaveApproval sibling : siblings) {
            String oldSiblingState = String.format("{ \"action\": \"%s\" }", sibling.getAction());
            sibling.setAction(ACTION_CANCELLED_BY_SIBLING);
            sibling.setComments("Request was processed by another approver: " + actor.getUsername());
            sibling.setActedAt(LocalDateTime.now());
            leaveApprovalRepository.save(sibling);

            String newSiblingState = String.format("{ \"action\": \"%s\", \"cancelledBySiblingOfId\": %d }", ACTION_CANCELLED_BY_SIBLING, leaveApprovalId);
            auditLogService.saveAuditLog(sibling.getId(), "CANCEL_BY_SIBLING", "LeaveApproval", oldSiblingState, newSiblingState);
        }

        log.info("Leave approval {} processed by user {}.", leaveApprovalId, actor.getUsername());

        if (approval.getApprovalType() == ApprovalType.CANCELLATION) {
            if (ACTION_REJECTED.equals(normalizedAction)) {
                handleCancellationRejection(request, actor);
            } else {
                handleCancellationAdvancement(request, approval, actor);
            }
        } else {
            if (ACTION_REJECTED.equals(normalizedAction)) {
                handleRejection(request, actor.getUsername());
            } else {
                handleAdvancement(request, actor);
            }
        }
    }


    private void handleAdvancement(LeaveRequest request, User actor) {
        List<LeaveApprovalRule> applicableRules = getRulesForEffectiveChain(request);

        int maxRequiredLevel = 1;

        for (LeaveApprovalRule rule : applicableRules) {
            if (rule.getApprovalLevel() > maxRequiredLevel) {
                maxRequiredLevel = rule.getApprovalLevel();
            }
        }

        int currentLevel = request.getCurrentLevel();
        String oldRequestState = String.format("{ \"status\": \"%s\", \"currentLevel\": %d }", request.getStatus(), currentLevel);

        if (currentLevel < maxRequiredLevel) {
            int nextLevel = currentLevel + 1;

            LeaveApprovalRule nextLevelRule = null;

            for (LeaveApprovalRule rule : applicableRules) {
                if (rule.getApprovalLevel() == nextLevel) {
                    nextLevelRule = rule;
                    break;
                }
            }

            boolean shouldBypass = false;

            if (nextLevelRule != null) {
                Role requiredRole = nextLevelRule.getRequiredRole();
                List<User> nextApprovers = resolveApprovers(request.getEmployee(), requiredRole);
                int actorHighestWeight = getHighestRoleWeight(actor);

                // SCENARIO 1: The actor who just approved IS the exact person who would approve the next step anyway.
                // (e.g., The employee's Manager is also the Dept Head. Prevents asking them to approve twice).
                boolean actorIsNextApprover = nextApprovers.stream().anyMatch(u -> u.getId().equals(actor.getId()));

                // SCENARIO 2: The specific target (Manager/HOD) is physically missing from the database,
                // AND the actor who just approved has enough authority to override the fallback pool.
                boolean specificApproverMissing = false;
                if ("ROLE_MANAGER".equals(requiredRole.getName())) {
                    specificApproverMissing = (request.getEmployee().getManager() == null || request.getEmployee().getManager().getUser() == null);
                } else if ("ROLE_DEPT_HEAD".equals(requiredRole.getName())) {
                    specificApproverMissing = (request.getEmployee().getDepartment() == null || request.getEmployee().getDepartment().getHod() == null || request.getEmployee().getDepartment().getHod().getUser() == null);
                }

                boolean actorOutranksFallback = (actorHighestWeight >= requiredRole.getHierarchyWeight());

                if (actorIsNextApprover || (specificApproverMissing && actorOutranksFallback)) {
                    shouldBypass = true;
                }
            }

            if (shouldBypass) {
                log.info("Smart Bypass triggered for Level {} on request {}.", nextLevel, request.getId());
                createBypassedApprovalRecord(request, actor, nextLevel);
                request.setCurrentLevel(nextLevel);
                handleAdvancement(request, actor);
                return;
            }

            // If no bypass conditions are met, strictly generate the next level!
            request.setCurrentLevel(nextLevel);
            request.setStatus("PENDING LVL " + nextLevel);
            leaveRequestRepository.save(request);

            generateApprovalsForLevel(request, nextLevel, applicableRules);

            String newRequestState = String.format("{ \"status\": \"%s\", \"currentLevel\": %d }", request.getStatus(), nextLevel);
            auditLogService.saveAuditLog(request.getId(), "WORKFLOW_ADVANCED", "LeaveRequest", oldRequestState, newRequestState);
        } else {
            finalizeApproval(request, actor.getUsername());
        }
    }
//    How this behaves now:
//    HOD Exists: If Tier 1 is approved, and the HOD exists, it will strictly generate a PENDING LVL 2 task and go to the HOD.
//
//    HOD is Missing: If Tier 1 is approved by someone with high authority, and the HOD position is empty, it skips Level 2 and moves straight to Level 3.
//
//    Manager IS the HOD: If the HOD approves Tier 1, it realizes the HOD is the next person in line anyway, and intelligently skips Tier 2 so they don't get duplicate notifications.

    private void finalizeApproval(LeaveRequest request, String actorUsername) {
        String oldRequestState = String.format("{ \"status\": \"%s\" }", request.getStatus());

        request.setStatus(LeaveRequestService.STATUS_APPROVED);
        leaveRequestRepository.save(request);

        leaveBalanceService.deduct(request);

        // REPLACEMENT BLOCK in finalizeApproval
        if (request.getSupersededLeaveIds() != null && !request.getSupersededLeaveIds().isEmpty()) {
            String[] ids = request.getSupersededLeaveIds().split(",");
            for (String idStr : ids) {
                Long oldId = Long.valueOf(idStr.trim());
                LeaveRequest oldReq = leaveRequestRepository.findById(oldId).orElse(null);

                if (oldReq != null && !oldReq.getStatus().equals(LeaveRequestService.STATUS_CANCELLED)) {

                    // 1. Capture the status BEFORE changing it
                    String previousStatus = oldReq.getStatus();

                    // 2. Cancel it
                    oldReq.setStatus(LeaveRequestService.STATUS_CANCELLED);
                    oldReq.setReason(oldReq.getReason() + " [SUPERSEDED BY REQUEST ID: " + request.getId() + "]");
                    leaveRequestRepository.save(oldReq);

                    // 3. Rollback using the captured status
                    if (previousStatus.equals(LeaveRequestService.STATUS_APPROVED)) {
                        leaveBalanceService.rollbackDeduction(oldReq);
                    } else {
                        leaveBalanceService.releasePendingHold(oldReq);
                    }
                }
            }
        }
        LocalDate cursor = request.getStartDate();
        while (!cursor.isAfter(request.getEndDate())) {
            if (!cursor.isAfter(LocalDate.now())) {
                attendanceCronJobService.recalculateAttendanceForDate(request.getEmployee().getId(), cursor);
            }
            cursor = cursor.plusDays(1);
        }

        String newRequestState = String.format("{ \"status\": \"%s\" }", LeaveRequestService.STATUS_APPROVED);
        auditLogService.saveAuditLog(request.getId(), "WORKFLOW_FINALIZE_APPROVED", "LeaveRequest", oldRequestState, newRequestState);
    }

    private void handleRejection(LeaveRequest request, String actorUsername) {
        String oldRequestState = String.format("{ \"status\": \"%s\" }", request.getStatus());

        request.setStatus(LeaveRequestService.STATUS_REJECTED);
        leaveRequestRepository.save(request);

        leaveBalanceService.releasePendingHold(request);
        cancelPendingApprovals(request.getId());

        String newRequestState = String.format("{ \"status\": \"%s\" }", LeaveRequestService.STATUS_REJECTED);
        auditLogService.saveAuditLog(request.getId(), "WORKFLOW_FINALIZE_REJECTED", "LeaveRequest", oldRequestState, newRequestState);
    }

    @Transactional
    public void cancelPendingApprovals(Long leaveRequestId) {
        List<LeaveApproval> pendingApprovals = leaveApprovalRepository.findByLeaveRequestIdAndAction(leaveRequestId, ACTION_PENDING);

        for (LeaveApproval approval : pendingApprovals) {
            String oldState = String.format("{ \"action\": \"%s\" }", approval.getAction());
            approval.setAction("CANCELLED");
            approval.setComments("System: Request cancelled.");
            approval.setActedAt(LocalDateTime.now());
            leaveApprovalRepository.save(approval);
            auditLogService.saveAuditLog(approval.getId(), "CANCEL_PENDING_STEP", "LeaveApproval", oldState, "{ \"action\": \"CANCELLED\" }");
        }
    }


    private List<User> resolveApprovers(Employee empApplied, Role requiredRole) {
        String currentRoleName = requiredRole.getName();
        int minWeight = requiredRole.getHierarchyWeight();

        if ("ROLE_MANAGER".equals(currentRoleName)) {
            if (empApplied.getManager() != null && empApplied.getManager().getUser() != null) {
                return List.of(empApplied.getManager().getUser());
            }
            log.info("No direct Manager found for {}, cascading to Department Head.", empApplied.getEmployeeCode());
            currentRoleName = "ROLE_DEPT_HEAD";
        }

        if ("ROLE_DEPT_HEAD".equals(currentRoleName)) {
            if (empApplied.getDepartment() != null && empApplied.getDepartment().getHod() != null && empApplied.getDepartment().getHod().getUser() != null) {
                return List.of(empApplied.getDepartment().getHod().getUser());
            }
            log.info("No direct HOD found for {}, cascading to the HR / Admin pool.", empApplied.getEmployeeCode());
        }

        List<User> eligibleApprovers = userRepository.findEligibleApproversByWeight(minWeight);

        List<User> finalApprovers = new ArrayList<>();
        for (User approver : eligibleApprovers) {
            if (approver.getId().equals(empApplied.getUser().getId())) {
                continue;
            }
            if (("ROLE_MANAGER".equals(requiredRole.getName()) || "ROLE_DEPT_HEAD".equals(requiredRole.getName())) && approver.getRole().getHierarchyWeight() <= requiredRole.getHierarchyWeight()) {
                continue;
            }
            finalApprovers.add(approver);
        }

        if (finalApprovers.isEmpty()) {
            throw new IllegalStateException("CRITICAL SYSTEM ERROR: No eligible approvers found for this request.");
        }

        int lowestFoundWeight = finalApprovers.get(0).getRole().getHierarchyWeight();

        List<User> result = new ArrayList<>();

        for (User user : finalApprovers) {
            if (user.getRole().getHierarchyWeight() == lowestFoundWeight) {
                result.add(user);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LeaveApproval> getApprovalsForRequest(Long leaveRequestId) {
        return leaveApprovalRepository.findAllByLeaveRequestIdChronological(leaveRequestId);
    }

    @Transactional(readOnly = true)
    public List<LeaveApproval> getPendingApprovalsForUser(Long userId) {
        return leaveApprovalRepository.findByApproverIdAndAction(userId, ACTION_PENDING);
    }

    private List<LeaveApprovalRule> getRulesForEffectiveChain(LeaveRequest request) {
        LocalDate previousWorkingDay = durationEngineService.getPreviousWorkingDay(request.getStartDate(), request.getEmployee());
        LocalDate nextWorkingDay = durationEngineService.getNextWorkingDay(request.getEndDate(), request.getEmployee());

        List<LeaveRequest> adjacentLeaves = leaveRequestRepository.findAdjacentLeaves(request.getEmployee().getId(), previousWorkingDay, nextWorkingDay);

        BigDecimal effectiveDuration = request.getDurationDays();

        for (LeaveRequest adj : adjacentLeaves) {
            effectiveDuration = effectiveDuration.add(adj.getDurationDays());
        }

        return ruleService.getApplicableRules(request.getLeaveType().getId(), effectiveDuration);
    }

    private int getHighestRoleWeight(User user) {
        if (user.getRole() != null) {
            return user.getRole().getHierarchyWeight();
        }
        return 0;
    }

    private void createBypassedApprovalRecord(LeaveRequest request, User higherAuthorityActor, int skippedLevel) {
        LeaveApproval bypassedApproval = new LeaveApproval();
        bypassedApproval.setLeaveRequest(request);
        bypassedApproval.setApprover(higherAuthorityActor);
        bypassedApproval.setApprovalLevel(skippedLevel);
        bypassedApproval.setAction("AUTO_APPROVED");
        bypassedApproval.setComments("System: Automatically approved because a higher authority processed a previous step.");
        bypassedApproval.setActedAt(LocalDateTime.now());
        bypassedApproval.setApprovalType(ApprovalType.ORIGINAL);
        leaveApprovalRepository.save(bypassedApproval);
    }

    @Transactional
    public void upgradePendingWorkflow(LeaveRequest existingRequest, List<LeaveApprovalRule> upgradedRules, Long triggeringRequestId) {

        log.info("Auto-upgrading workflow for Leave Request ID: {} due to chained request ID: {}", existingRequest.getId(), triggeringRequestId);

        List<LeaveApproval> pendingApprovals = leaveApprovalRepository.findByLeaveRequestIdAndAction(existingRequest.getId(), ACTION_PENDING);

        for (LeaveApproval approval : pendingApprovals) {
            String oldState = String.format("{ \"action\": \"%s\" }", approval.getAction());
            approval.setAction("CANCELLED");
            approval.setComments("System Auto-Action: Workflow reset and upgraded due to adjacent leave submission.");
            approval.setActedAt(LocalDateTime.now());
            leaveApprovalRepository.save(approval);

            auditLogService.saveAuditLog(approval.getId(), "UPGRADE_RESET", "LeaveApproval", oldState, "{ \"action\": \"CANCELLED\" }");
        }

        String oldReqState = String.format("{ \"currentLevel\": %d, \"status\": \"%s\" }", existingRequest.getCurrentLevel(), existingRequest.getStatus());

        existingRequest.setCurrentLevel(1);
        existingRequest.setStatus("PENDING LVL 1");
        leaveRequestRepository.save(existingRequest);

        generateApprovalsForLevel(existingRequest, 1, upgradedRules);

        String newReqState = String.format("{ \"currentLevel\": 1, \"status\": \"PENDING LVL 1\", \"upgradedBy\": %d }", triggeringRequestId);
        auditLogService.saveAuditLog(existingRequest.getId(), "WORKFLOW_UPGRADED", "LeaveRequest", oldReqState, newReqState);
    }

    public List<LeaveApproval> getApprovedOriginals(Long leaveRequestId) {
        return leaveApprovalRepository.findApprovedOriginals(leaveRequestId);
    }

    @Transactional
    public void generateCancellationWorkflow(LeaveRequest request, List<LeaveApproval> approvedOriginals) {
        LeaveApproval firstApprover = approvedOriginals.get(0);
        createApprovalRecord(request, firstApprover.getApprover(), firstApprover.getApprovalLevel(), ApprovalType.CANCELLATION);
    }

    // ADD THESE THREE METHODS
    private void handleCancellationRejection(LeaveRequest request, User actor) {
        request.setCancellationStatus(CancellationStatus.REJECTED);
        leaveRequestRepository.save(request);

        // Cancel any lingering CANCELLATION steps, but leave original ones frozen/alive
        List<LeaveApproval> pending = leaveApprovalRepository.findByLeaveRequestIdAndAction(request.getId(), ACTION_PENDING);
        for (LeaveApproval a : pending) {
            if (a.getApprovalType() == ApprovalType.CANCELLATION) {
                a.setAction("CANCELLED");
                a.setComments("System: Cancellation workflow was rejected by " + actor.getUsername());
                leaveApprovalRepository.save(a);
            }
        }
        auditLogService.saveAuditLog(request.getId(), "CANCELLATION_REJECTED", "LeaveRequest", null, "{ \"cancellationStatus\": \"REJECTED\" }");
    }

    private void handleCancellationAdvancement(LeaveRequest request, LeaveApproval currentApproval, User actor) {
        List<LeaveApproval> originalApprovals = leaveApprovalRepository.findApprovedOriginals(request.getId());

        LeaveApproval nextOriginal = null;
        for (LeaveApproval orig : originalApprovals) {
            if (orig.getApprovalLevel() > currentApproval.getApprovalLevel()) {
                nextOriginal = orig;
                break;
            }
        }

        if (nextOriginal != null) {
            // Still more approvers to ask, generate next sequential cancellation step
            createApprovalRecord(request, nextOriginal.getApprover(), nextOriginal.getApprovalLevel(), ApprovalType.CANCELLATION);
            auditLogService.saveAuditLog(request.getId(), "CANCELLATION_ADVANCED", "LeaveRequest", null, "{ \"cancellationLevel\": " + nextOriginal.getApprovalLevel() + " }");
        } else {
            // Everyone agreed to cancel it! Finalize it.
            finalizeCancellation(request, actor);
        }
    }

    private void finalizeCancellation(LeaveRequest request, User actor) {
        String previousStatus = request.getStatus();

        request.setStatus(LeaveRequestService.STATUS_CANCELLED);
        request.setCancellationStatus(CancellationStatus.APPROVED);
        leaveRequestRepository.save(request);

        if (previousStatus.equals(LeaveRequestService.STATUS_APPROVED)) {
            leaveBalanceService.rollbackDeduction(request);

            LocalDate cursor = request.getStartDate();
            while (!cursor.isAfter(request.getEndDate())) {
                if (!cursor.isAfter(LocalDate.now())) {
                    attendanceCronJobService.recalculateAttendanceForDate(request.getEmployee().getId(), cursor);
                }
                cursor = cursor.plusDays(1);
            }
        } else {
            leaveBalanceService.releasePendingHold(request);

            // Because the leave is now dead, we must permanently cancel any ORIGINAL pending tasks that were frozen
            List<LeaveApproval> pending = leaveApprovalRepository.findByLeaveRequestIdAndAction(request.getId(), ACTION_PENDING);
            for (LeaveApproval a : pending) {
                if (a.getApprovalType() == ApprovalType.ORIGINAL) {
                    a.setAction("CANCELLED");
                    a.setComments("System: Leave request was manually cancelled by employee and approved.");
                    leaveApprovalRepository.save(a);
                }
            }
        }
        auditLogService.saveAuditLog(request.getId(), "CANCELLATION_FULLY_APPROVED", "LeaveRequest", null, "{ \"status\": \"CANCELLED\" }");
    }
}
