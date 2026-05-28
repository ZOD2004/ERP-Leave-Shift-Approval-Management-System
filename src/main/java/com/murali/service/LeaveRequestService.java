package com.murali.service;

import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.exception.PastDateException;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final DurationEngineService durationEngineService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveApprovalRuleService ruleService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceSyncService attendanceSyncService;
    private final ApprovalRoutingService approvalRoutingService;
    private final ShiftAssignmentService shiftAssignmentService;

   private final AuditLogService auditLoggingService;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DRAFT = "DRAFT";

    private static final List<String> BACKDATED_ALLOWED_CODES = List.of("EMG-001", "SL-001");

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, DurationEngineService durationEngineService, LeaveBalanceService leaveBalanceService, LeaveApprovalRuleService ruleService, EmployeeRepository employeeRepository, AttendanceSyncService attendanceSyncService, ApprovalRoutingService approvalRoutingService, ShiftAssignmentService shiftAssignmentService, AuditLogService auditLoggingService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.durationEngineService = durationEngineService;
        this.leaveBalanceService = leaveBalanceService;
        this.ruleService = ruleService;
        this.employeeRepository = employeeRepository;
        this.attendanceSyncService = attendanceSyncService;
        this.approvalRoutingService = approvalRoutingService;
        this.shiftAssignmentService = shiftAssignmentService;
        this.auditLoggingService = auditLoggingService;
    }

    @Transactional
    public void submitLeaveRequest(Long existingDraftId,Employee detachedEmployee, LeaveType leaveType,
                                   LocalDate startDate, LocalDate endDate,
                                   String reason, Integer currentYear, LeaveSession leaveSession) {

        if (leaveSession != null) {
            endDate = startDate;
        }
        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        List<String> activeStatuses = List.of(STATUS_PENDING, STATUS_APPROVED);
        boolean hasOverlap = leaveRequestRepository.hasOverlappingLeave(
                employee.getId(), startDate, endDate, activeStatuses
        );

        if (hasOverlap) {
            throw new IllegalArgumentException(
                    "Validation Failed: You already have a pending or approved leave request that overlaps with these dates."
            );
        }

        BigDecimal duration = durationEngineService.calculateNetLeaveDays(
                startDate, endDate, employee, leaveType, false
        );

        if (duration.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Calculated leave duration must be greater than 0 days.");
        }

        if (startDate.isBefore(LocalDate.now())) {
            if (!BACKDATED_ALLOWED_CODES.contains(leaveType.getCode().toUpperCase())) {
                throw new PastDateException(
                        "Validation Failed: Back-dating is only permitted for Sick or Emergency leaves."
                );
            }
        }

        List<LeaveBalance> balances = leaveBalanceService.getBalancesForEmployee(employee.getId(), currentYear);

        LeaveBalance currentBalance = null;

        for (LeaveBalance balance : balances) {
            if (balance.getLeaveType().getId().equals(leaveType.getId())) {
                currentBalance = balance;
                break;
            }
        }

        BigDecimal effectiveBalance = leaveBalanceService.getEffectiveBalance(currentBalance);
        boolean isNegativeBalance = duration.compareTo(effectiveBalance) > 0;

        if (isNegativeBalance) {
            String code = leaveType.getCode();
            if (!code.equals("SL-001") && !code.equals("EMG-001")) {
                throw new IllegalArgumentException(
                        "Insufficient balance. You only have " + effectiveBalance + " days available. " +
                                "Negative balances are only permitted for Sick[SL-001] or Emergency leaves[EMG-001]."
                );
            }
        }

        List<LeaveApprovalRule> applicableRules = ruleService.getApplicableRules(leaveType.getId(), duration);

        if (applicableRules.isEmpty()) {
            throw new IllegalStateException("System Configuration Error: No approval rules found for this leave type and duration.");
        }

        if (isNegativeBalance) {
            reason = "[WARNING: NEGATIVE BALANCE REQUEST] - " + reason;
        }

        LeaveRequest request;
        String oldState = null;
        if (existingDraftId != null) {
            request = leaveRequestRepository.findById(existingDraftId)
                    .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
            oldState = String.format("{ \"status\": \"%s\", \"durationDays\": %s }", request.getStatus(), request.getDurationDays());
        } else {
            request = new LeaveRequest();
        }

        request.setEmployee(employee);
        request.setLeaveType(leaveType);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setDurationDays(duration);
        request.setReason(reason);
        request.setStatus(STATUS_PENDING);
        request.setLeaveSession(leaveSession);
        request.setCurrentLevel(1);

        LeaveRequest savedRequest = leaveRequestRepository.save(request);

        leaveBalanceService.holdPendingBalance(employee, leaveType, duration, currentYear, savedRequest.getId());

        approvalRoutingService.generateApprovalWorkflow(savedRequest, applicableRules, isNegativeBalance);

        String newState = String.format("{ \"status\": \"%s\", \"durationDays\": %s, \"leaveTypeId\": %d }",
                savedRequest.getStatus(), savedRequest.getDurationDays(), savedRequest.getLeaveType().getId());
        String action = existingDraftId != null ? "UPDATED" : "CREATED";

        log.info("Leave request submitted successfully. Employee ID: {}, Leave Request ID: {}", employee.getId(), savedRequest.getId());
        auditLoggingService.saveAuditLog(savedRequest.getId(), action, "leave_requests", oldState, newState);
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
        String oldState = String.format("{ \"status\": \"%s\" }", currentStatus);

        if (currentStatus.equals(STATUS_REJECTED) || currentStatus.equals(STATUS_CANCELLED)) {
            throw new IllegalStateException("This request is already " + currentStatus);
        }

        if (currentStatus.startsWith(STATUS_PENDING)) {
            request.setStatus(STATUS_CANCELLED);
            leaveBalanceService.releasePendingHold(
                    request.getEmployee(), request.getLeaveType(), request.getDurationDays(), currentYear, request.getId()
            );
            approvalRoutingService.cancelPendingApprovals(request.getId());
        }
        else if (currentStatus.equals(STATUS_APPROVED)) {
            request.setStatus(STATUS_CANCELLED);
            leaveBalanceService.rollbackDeduction(
                    request.getEmployee(), request.getLeaveType(), request.getDurationDays(), request.getId(), currentYear
            );
            attendanceSyncService.revertLeaveFromAttendance(request);
            shiftAssignmentService.revertHalfDayLeaveOverride(request);
        }

        LeaveRequest savedRequest = leaveRequestRepository.save(request);
        String newState = String.format("{ \"status\": \"%s\" }", savedRequest.getStatus());

        log.info("Leave request cancelled successfully. Leave Request ID: {}, Previous Status: {}", leaveRequestId, currentStatus);
        auditLoggingService.saveAuditLog(leaveRequestId, "CANCELLED", "leave_requests", oldState, newState);
    }

    @Transactional
    public LeaveRequest saveOrUpdateDraft(Long draftId, Employee detachedEmployee, LeaveType leaveType,
                                          LocalDate startDate, LocalDate endDate, String reason,
                                          LeaveSession leaveSession) {

        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        BigDecimal duration = durationEngineService.calculateNetLeaveDays(startDate, endDate, employee, leaveType, false);

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
        draft.setLeaveSession(leaveSession);
        draft.setStatus(STATUS_DRAFT);
        draft.setCurrentLevel(0);

        LeaveRequest savedDraft = leaveRequestRepository.save(draft);

        String newState = String.format("{ \"status\": \"%s\", \"durationDays\": %s }", savedDraft.getStatus(), savedDraft.getDurationDays());
        String action = (draftId != null && oldState != null) ? "UPDATED" : "CREATED";

        auditLoggingService.saveAuditLog(savedDraft.getId(), action, "leave_requests", oldState, newState);

        return savedDraft;
    }
}