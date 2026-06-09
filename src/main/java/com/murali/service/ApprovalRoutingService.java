package com.murali.service;

import com.murali.entity.*;
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

    public ApprovalRoutingService(LeaveApprovalRepository leaveApprovalRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  LeaveBalanceService leaveBalanceService,
                                  LeaveApprovalRuleService ruleService,
                                  AuditLogService auditLogService,
                                  UserRepository userRepository,
                                  AttendanceCronJobService attendanceCronJobService, DurationEngineService durationEngineService) {
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
            if (rule.getApprovalLevel() == targetLevel) { // use targetLevel to get the applicable rule
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
            createApprovalRecord(request, approver, targetLevel);
        }
    }

    private void createApprovalRecord(LeaveRequest request, User approver, int level) {
        LeaveApproval approval = new LeaveApproval();
        approval.setLeaveRequest(request);
        approval.setApprover(approver);
        approval.setApprovalLevel(level);
        approval.setAction(ACTION_PENDING);
        leaveApprovalRepository.save(approval);

        String state = String.format("{ \"approverId\": %d, \"level\": %d, \"action\": \"%s\" }", approver.getId(), level, ACTION_PENDING);
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

        LeaveApproval approval = leaveApprovalRepository.findById(leaveApprovalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval record not found"));

        LeaveRequest request = approval.getLeaveRequest();

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
            if (a.getApprovalLevel().equals(approval.getApprovalLevel())
                    && !a.getId().equals(approval.getId())) {
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

        if (ACTION_REJECTED.equals(normalizedAction)) {
            handleRejection(request, actor.getUsername());
        } else {
            handleAdvancement(request, actor);
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

            int actorHighestWeight = getHighestRoleWeight(actor);

            if (nextLevelRule != null && actorHighestWeight >= nextLevelRule.getRequiredRole().getHierarchyWeight()) {

                log.info("Actor {} (Weight {}) outranks Level {} requirement (Weight {}). Auto-skipping level.",
                        actor.getUsername(), actorHighestWeight, nextLevel, nextLevelRule.getRequiredRole().getHierarchyWeight());

                createBypassedApprovalRecord(request, actor, nextLevel);

                request.setCurrentLevel(nextLevel);
                handleAdvancement(request, actor);
                return;
            }

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

    private void finalizeApproval(LeaveRequest request, String actorUsername) {
        String oldRequestState = String.format("{ \"status\": \"%s\" }", request.getStatus());

        request.setStatus(LeaveRequestService.STATUS_APPROVED);
        leaveRequestRepository.save(request);

        leaveBalanceService.deduct(request);

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
            if (empApplied.getDepartment() != null
                    && empApplied.getDepartment().getHod() != null
                    && empApplied.getDepartment().getHod().getUser() != null) {
                return List.of(empApplied.getDepartment().getHod().getUser());
            }
            log.info("No direct HOD found for {}, cascading to the HR / Admin pool.", empApplied.getEmployeeCode());
            currentRoleName = "ROLE_HR_ADMIN";
        }

        List<User> eligibleApprovers = userRepository.findEligibleApproversByWeight(minWeight);

        List<User> finalApprovers = new ArrayList<>();
        for (User approver : eligibleApprovers) {
            if (approver.getId().equals(empApplied.getUser().getId())) {
                continue;
            }
            if (("ROLE_MANAGER".equals(requiredRole.getName()) || "ROLE_DEPT_HEAD".equals(requiredRole.getName()))
                    && approver.getRole().getHierarchyWeight() <= requiredRole.getHierarchyWeight()) {
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

        List<LeaveRequest> adjacentLeaves = leaveRequestRepository.findAdjacentLeaves(
                request.getEmployee().getId(), previousWorkingDay, nextWorkingDay
        );

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
        leaveApprovalRepository.save(bypassedApproval);
    }
    @Transactional
    public void upgradePendingWorkflow(LeaveRequest existingRequest, List<LeaveApprovalRule> upgradedRules, Long triggeringRequestId) {

        log.info("Auto-upgrading workflow for Leave Request ID: {} due to chained request ID: {}",
                existingRequest.getId(), triggeringRequestId);

        // 1. Cancel currently pending approval queues for this request
        List<LeaveApproval> pendingApprovals = leaveApprovalRepository.findByLeaveRequestIdAndAction(existingRequest.getId(), ACTION_PENDING);

        for (LeaveApproval approval : pendingApprovals) {
            String oldState = String.format("{ \"action\": \"%s\" }", approval.getAction());
            approval.setAction("CANCELLED");
            approval.setComments("System Auto-Action: Workflow reset and upgraded due to adjacent leave submission (Salami-slicing prevention).");
            approval.setActedAt(LocalDateTime.now());
            leaveApprovalRepository.save(approval);

            auditLogService.saveAuditLog(approval.getId(), "UPGRADE_RESET", "LeaveApproval", oldState, "{ \"action\": \"CANCELLED\" }");
        }

        // 2. Reset the Request level back to 1 and update status
        String oldReqState = String.format("{ \"currentLevel\": %d, \"status\": \"%s\" }",
                existingRequest.getCurrentLevel(), existingRequest.getStatus());

        existingRequest.setCurrentLevel(1);
        existingRequest.setStatus("PENDING LVL 1");
        leaveRequestRepository.save(existingRequest);

        // 3. Generate the new upgraded workflow
        generateApprovalsForLevel(existingRequest, 1, upgradedRules);

        String newReqState = String.format("{ \"currentLevel\": 1, \"status\": \"PENDING LVL 1\", \"upgradedBy\": %d }", triggeringRequestId);
        auditLogService.saveAuditLog(existingRequest.getId(), "WORKFLOW_UPGRADED", "LeaveRequest", oldReqState, newReqState);
    }
}
