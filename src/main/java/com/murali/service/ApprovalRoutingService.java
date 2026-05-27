package com.murali.service;

import com.murali.entity.*;
import com.murali.exception.EmployeeNotFoundException;
import com.murali.exception.SelfApprovalException;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.LeaveApprovalRepository;
import com.murali.repository.LeaveRequestRepository;
import com.murali.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ApprovalRoutingService {

    private final LeaveApprovalRepository leaveApprovalRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    private final LeaveBalanceService leaveBalanceService;
    private final AttendanceSyncService attendanceSyncService;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;
    private final ShiftAssignmentService shiftAssignmentService;

    private final UserRepository userRepository;


    public static final String ACTION_REJECTED = "REJECTED";
    public static final String ACTION_APPROVED = "APPROVED";
    public static final String ACTION_PENDING = "PENDING";

    public ApprovalRoutingService(LeaveApprovalRepository leaveApprovalRepository, LeaveRequestRepository leaveRequestRepository, LeaveBalanceService leaveBalanceService, AttendanceSyncService attendanceSyncService, AuditLogRepository auditLogRepository, SecurityService securityService, ShiftAssignmentService shiftAssignmentService, UserRepository userRepository) {
        this.leaveApprovalRepository = leaveApprovalRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.attendanceSyncService = attendanceSyncService;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
        this.shiftAssignmentService = shiftAssignmentService;
        this.userRepository = userRepository;
    }

    private void createApprovalRecord(LeaveRequest request, User approver, int level) {
        LeaveApproval approval = new LeaveApproval();
        approval.setLeaveRequest(request);
        approval.setApprover(approver);
        approval.setApprovalLevel(level);
        approval.setAction("PENDING");
        leaveApprovalRepository.save(approval);
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

        if (!approval.getApprovalLevel().equals(request.getCurrentLevel())) {
            throw new IllegalStateException("Out of sequence: Request is currently at level "
                    + request.getCurrentLevel() + " but this approval is for level " + approval.getApprovalLevel());
        }

        approval.setAction(normalizedAction);
        approval.setComments(comments);
        approval.setActedAt(LocalDateTime.now());

        leaveApprovalRepository.save(approval);

        log.info("Leave approval {} processed by user {}. Action: {}", leaveApprovalId, actor.getUsername(), normalizedAction);
        saveAuditLog(
                leaveApprovalId,
                normalizedAction,
                "leave_approvals",
                null,
                "{ \"action\": \"processed\", \"comments\": \"" + comments + "\" }"
        );
        if (ACTION_REJECTED.equals(normalizedAction)) {
            handleRejection(request);
        } else {
            handleAdvancement(request);
        }
    }


    private void handleAdvancement(LeaveRequest request) {
        List<LeaveApproval> allSteps = leaveApprovalRepository
                .findByLeaveRequestIdOrderByApprovalLevelAsc(request.getId());

        Integer nextLevel = allSteps.stream()
                .map(LeaveApproval::getApprovalLevel)
                .filter(level -> level > request.getCurrentLevel())
                .min(Integer::compareTo)
                .orElse(null);

        if (nextLevel != null) {
            request.setCurrentLevel(nextLevel);
            request.setStatus("PENDING LVL"+nextLevel);
            leaveRequestRepository.save(request);
            // TODO: Trigger Email for the Next Level Approver
        } else {
            finalizeApproval(request);
        }
    }

    private void finalizeApproval(LeaveRequest request) {
        request.setStatus(LeaveRequestService.STATUS_APPROVED);
        leaveRequestRepository.save(request);

        // Deduct the actual days and remove the pending hold
        Integer year = request.getStartDate().getYear();

        leaveBalanceService.deduct(
                request.getEmployee(),
                request.getLeaveType(),
                request.getDurationDays(),
                request.getId(),
                year
        );

        // 2. Attendance Sync: Block out the calendar
        attendanceSyncService.syncLeaveRecords(request);
        shiftAssignmentService.applyHalfDayLeaveOverride(request);

        // TODO: Trigger Email to Employee: "Your leave is approved"
    }

    private void handleRejection(LeaveRequest request) {
        request.setStatus(LeaveRequestService.STATUS_REJECTED);
        leaveRequestRepository.save(request);

        Integer year = request.getStartDate().getYear();

        //Must release the "Pending Hold" we placed when the request was submitted.
        leaveBalanceService.releasePendingHold(
                request.getEmployee(),
                request.getLeaveType(),
                request.getDurationDays(),
                year,
                request.getId()
        );

        // TODO: Trigger Email to Employee: "Your leave was rejected with reason maybe"
    }
    @Transactional
    public void cancelPendingApprovals(Long leaveRequestId) {

        List<LeaveApproval> pendingApprovals = leaveApprovalRepository
                .findByLeaveRequestIdAndAction(leaveRequestId, ACTION_PENDING);

        for (LeaveApproval approval : pendingApprovals) {
            approval.setAction("CANCELLED");
            approval.setComments("System: Request cancelled by employee.");
            approval.setActedAt(LocalDateTime.now());
        }

        leaveApprovalRepository.saveAll(pendingApprovals);
        log.info("Cancelled {} pending approvals for leave request ID: {}", pendingApprovals.size(), leaveRequestId);
        saveAuditLog(
                leaveRequestId,
                "CANCELLED",
                "leave_approvals",
                "{ \"status\": \"PENDING\" }",
                "{ \"status\": \"CANCELLED\", \"reason\": \"Employee request cancellation\" }"
        );
    }

    @Transactional(readOnly = true)
    public List<LeaveApproval> getPendingApprovalsForUser(Long userId) {
        return leaveApprovalRepository.findActivePendingApprovalsForUser(userId);

    }

//    This is used to fetch the list = List<LeaveApprovalRule> applicableRules = ruleService.getApplicableRules(leaveType.getId(), duration);
    @Transactional
    public void generateApprovalWorkflow(LeaveRequest request, List<LeaveApprovalRule> rules, boolean isNegativeBalance) {
        if ((rules == null || rules.isEmpty()) && !isNegativeBalance) {
            throw new IllegalArgumentException("Cannot generate workflow: No routing rules found for this leave request configuration.");
        }
        Employee applicant = request.getEmployee();

        for (LeaveApprovalRule rule : rules) {
            String requiredRoleName = rule.getRequiredRole().getName();
            User approver = resolveApproverForRole(applicant, requiredRoleName);

            if (approver.getId().equals(applicant.getUser().getId())) {
                log.info("Self-approval detected for role " + requiredRoleName + ". Escalating to HR.");
                approver = getFallbackAdminUser();
            }

            createApprovalRecord(request, approver, rule.getApprovalLevel());
        }

        if (isNegativeBalance) {
            boolean hasLevel3 = false;

            for (LeaveApprovalRule rule : rules) {
                if (rule.getApprovalLevel() == 3) {
                    hasLevel3 = true;
                    break;
                }
            }
            if (!hasLevel3) {
                User deptHeadUser = resolveApproverForRole(applicant, "ROLE_DEPT_HEAD");

                if (deptHeadUser.getId().equals(applicant.getUser().getId())) {
                    deptHeadUser = getFallbackAdminUser();
                }

                createApprovalRecord(request, deptHeadUser, 3);
            }
        }
        log.info("Approval workflow generated successfully for Leave Request ID: {}", request.getId());
        saveAuditLog(
                request.getId(),
                "WORKFLOW_CREATED",
                "leave_approvals",
                null,
                "{ \"rulesGenerated\": " + rules.size() + ", \"status\": \"ACTIVE\" }"
        );
    }

    private User resolveApproverForRole(Employee applicant, String roleName) {
        switch (roleName) {
            case "ROLE_MANAGER":
                if (applicant.getManager() != null) {
                    return applicant.getManager().getUser();
                }
                return getFallbackAdminUser();

            case "ROLE_DEPT_HEAD":
                if (applicant.getDepartment() != null && applicant.getDepartment().getHod() != null) {
                    return applicant.getDepartment().getHod().getUser();
                }
                return getFallbackAdminUser();

            case "ROLE_HR_ADMIN":
                return getFallbackAdminUser();

            default:
                throw new IllegalArgumentException("Unknown routing role: " + roleName);
        }
    }


    private User getFallbackAdminUser() {
        List<User> users = userRepository.findFirstByRoleName("ROLE_HR_ADMIN");
        if (users.isEmpty()) {
            throw new EmployeeNotFoundException(
                    "CRITICAL SYSTEM ERROR: No HR Admin found in the system to route approvals."
            );
        }
        return users.getFirst();
    }
    public List<LeaveApproval> getApprovalsForRequest(Long leaveRequestId) {
        return leaveApprovalRepository.findByLeaveRequestIdOrderByApprovalLevelAsc(leaveRequestId);
    }
    private void saveAuditLog(Long recordId, String action, String entityName, String oldState, String newState) {
        try {
            String performedBy = "SYSTEM";

            // Safely fetch current user from SecurityService
            if (securityService.getPrincipal() != null) {
                String username = securityService.getPrincipal().getUsername();
                String role = "USER";

                if (securityService.getAuthentication() != null && !securityService.getAuthentication().getAuthorities().isEmpty()) {
                    role = securityService.getAuthentication().getAuthorities().iterator().next().getAuthority();
                }
                // Combine them to match your Vaadin grid requirement
                performedBy = username + " (" + role + ")";
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setRecordId(recordId);
            auditLog.setAction(action);
            auditLog.setEntityName(entityName);
            auditLog.setPerformedBy(performedBy);
            auditLog.setOldState(oldState);
            auditLog.setNewState(newState);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save database audit log for record {}: {}", recordId, e.getMessage());
        }
    }
}