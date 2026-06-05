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

    public static final String ACTION_REJECTED = "REJECTED";
    public static final String ACTION_APPROVED = "APPROVED";
    public static final String ACTION_PENDING = "PENDING";
    public static final String ACTION_CANCELLED_BY_SIBLING = "CANCELLED_BY_SIBLING";

    public ApprovalRoutingService(LeaveApprovalRepository leaveApprovalRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  LeaveBalanceService leaveBalanceService,
                                  LeaveApprovalRuleService ruleService,
                                  AuditLogService auditLogService,
                                  UserRepository userRepository,
                                  AttendanceCronJobService attendanceCronJobService) {
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.attendanceCronJobService = attendanceCronJobService;
    }

    // --- WORKFLOW GENERATION ---

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
        LeaveApprovalRule levelRule = applicableRules.stream()
                .filter(r -> r.getApprovalLevel() == targetLevel)
                .findFirst()
                .orElse(null);

        String requiredRoleName = (levelRule != null) ? levelRule.getRequiredRole().getName() : null;

        if (requiredRoleName == null) {
            throw new IllegalStateException("Cannot generate workflow for level " + targetLevel + ": No rule found.");
        }

        List<User> approvers = resolveApproversForRole(request.getEmployee(), requiredRoleName);

        // Filter out self-approval
        approvers = approvers.stream()
                .filter(u -> !u.getId().equals(request.getEmployee().getUser().getId()))
                .toList();

        if (approvers.isEmpty()) {
            log.info("Self-approval detected or no approvers found for role {}. Escalating to next admin tier.", requiredRoleName);
            approvers = getFallbackAdmins().stream()
                    .filter(u -> !u.getId().equals(request.getEmployee().getUser().getId()))
                    .toList();
        }

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

    // --- APPROVAL PROCESSING ---

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

        // Cancel sibling queue items (e.g., if there were multiple HR admins)
        List<LeaveApproval> siblings = leaveApprovalRepository.findByLeaveRequestIdAndAction(request.getId(), ACTION_PENDING)
                .stream()
                .filter(a -> a.getApprovalLevel().equals(approval.getApprovalLevel()) && !a.getId().equals(approval.getId()))
                .toList();

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
            handleAdvancement(request, actor.getUsername());
        }
    }

    private void handleAdvancement(LeaveRequest request, String actorUsername) {
        // Calculate the "Chain" dynamically so advancing levels respects the consecutive day rule!
        List<LeaveApprovalRule> applicableRules = getRulesForEffectiveChain(request);

        int maxRequiredLevel = applicableRules.stream()
                .mapToInt(LeaveApprovalRule::getApprovalLevel)
                .max()
                .orElse(1);

        int currentLevel = request.getCurrentLevel();
        String oldRequestState = String.format("{ \"status\": \"%s\", \"currentLevel\": %d }", request.getStatus(), currentLevel);

        if (currentLevel < maxRequiredLevel) {
            int nextLevel = currentLevel + 1;
            request.setCurrentLevel(nextLevel);
            request.setStatus("PENDING LVL " + nextLevel);
            leaveRequestRepository.save(request);

            generateApprovalsForLevel(request, nextLevel, applicableRules);

            String newRequestState = String.format("{ \"status\": \"%s\", \"currentLevel\": %d }", request.getStatus(), nextLevel);
            auditLogService.saveAuditLog(request.getId(), "WORKFLOW_ADVANCED", "LeaveRequest", oldRequestState, newRequestState);
        } else {
            finalizeApproval(request, actorUsername);
        }
    }

    private void finalizeApproval(LeaveRequest request, String actorUsername) {
        String oldRequestState = String.format("{ \"status\": \"%s\" }", request.getStatus());

        request.setStatus(LeaveRequestService.STATUS_APPROVED);
        leaveRequestRepository.save(request);

        Integer year = request.getStartDate().getYear();

        // 1. Deduct Balance (Will auto-spillover to Unpaid if negative!)
        leaveBalanceService.deduct(request.getEmployee(), request.getLeaveType(), request.getDurationDays(), request.getId(), year);

        // 2. Safely recalculate Attendance via the Cron Job loop (Only for Past/Current dates)
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

        Integer year = request.getStartDate().getYear();
        leaveBalanceService.releasePendingHold(request.getEmployee(), request.getLeaveType(), request.getDurationDays(), year, request.getId());

        cancelPendingApprovals(request.getId());

        String newRequestState = String.format("{ \"status\": \"%s\" }", LeaveRequestService.STATUS_REJECTED);
        auditLogService.saveAuditLog(request.getId(), "WORKFLOW_FINALIZE_REJECTED", "LeaveRequest", oldRequestState, newRequestState);
    }

    // --- HELPER METHODS ---

    @Transactional
    public void cancelPendingApprovals(Long leaveRequestId) {
        List<LeaveApproval> pendingApprovals = leaveApprovalRepository
                .findByLeaveRequestIdAndAction(leaveRequestId, ACTION_PENDING);

        for (LeaveApproval approval : pendingApprovals) {
            String oldState = String.format("{ \"action\": \"%s\" }", approval.getAction());
            approval.setAction("CANCELLED");
            approval.setComments("System: Request cancelled.");
            approval.setActedAt(LocalDateTime.now());
            leaveApprovalRepository.save(approval);
            auditLogService.saveAuditLog(approval.getId(), "CANCEL_PENDING_STEP", "LeaveApproval", oldState, "{ \"action\": \"CANCELLED\" }");
        }
    }


    /**
     * Strict Fallback Routing: Manager -> HOD -> HR Admin -> Super Admin
     */
    private List<User> resolveApproversForRole(Employee applicant, String roleName) {

        // 1. Try Manager
        if ("ROLE_MANAGER".equals(roleName)) {
            if (applicant.getManager() != null && applicant.getManager().getUser() != null) {
                return List.of(applicant.getManager().getUser());
            }
            log.info("No Manager found for {}, falling back to Department Head.", applicant.getEmployeeCode());
            roleName = "ROLE_DEPT_HEAD";
        }

        // 2. Try HOD
        if ("ROLE_DEPT_HEAD".equals(roleName)) {
            if (applicant.getDepartment() != null && applicant.getDepartment().getHod() != null && applicant.getDepartment().getHod().getUser() != null) {
                return List.of(applicant.getDepartment().getHod().getUser());
            }
            log.info("No HOD found for {}, falling back to HR Admin.", applicant.getEmployeeCode());
            roleName = "ROLE_HR_ADMIN";
        }

        // 3. Try HR (or Super Admin)
        if ("ROLE_HR_ADMIN".equals(roleName) || "ROLE_SUPER_ADMIN".equals(roleName)) {
            List<User> groupUsers = userRepository.findByRoleName(roleName);
            if (!groupUsers.isEmpty()) {
                return groupUsers;
            }
        }

        return getFallbackAdmins();
    }

    private List<User> getFallbackAdmins() {
        List<User> users = userRepository.findByRoleName("ROLE_HR_ADMIN");
        if (users.isEmpty()) {
            users = userRepository.findByRoleName("ROLE_SUPER_ADMIN");
        }
        if (users.isEmpty()) {
            throw new IllegalStateException("CRITICAL SYSTEM ERROR: No HR Admin or Super Admin found in the system to route approvals.");
        }
        return users;
    }
    @Transactional(readOnly = true)
    public List<LeaveApproval> getApprovalsForRequest(Long leaveRequestId) {
        return leaveApprovalRepository.findAllByLeaveRequestIdChronological(leaveRequestId);
    }
    @Transactional(readOnly = true)
    public List<LeaveApproval> getPendingApprovalsForUser(Long userId) {
        return leaveApprovalRepository.findByApproverIdAndAction(userId, ACTION_PENDING);
    }

    /**
     * Re-calculates the contiguous chain to ensure loophole validation persists during level advancement.
     */
    private List<LeaveApprovalRule> getRulesForEffectiveChain(LeaveRequest request) {
        List<LeaveRequest> adjacentLeaves = leaveRequestRepository.findAdjacentLeaves(
                request.getEmployee().getId(), request.getStartDate().minusDays(1), request.getEndDate().plusDays(1)
        );

        BigDecimal effectiveDuration = request.getDurationDays();

        for (LeaveRequest adj : adjacentLeaves) {
            // Include adjacent cross-type leaves to ensure HOD levels are still triggered
            effectiveDuration = effectiveDuration.add(adj.getDurationDays());
        }

        return ruleService.getApplicableRules(request.getLeaveType().getId(), effectiveDuration);
    }
}