package com.murali.repository;

import com.murali.entity.LeaveApproval;
import com.murali.entity.enums.ApprovalType;
import com.murali.entity.enums.CancellationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveApprovalRepository extends JpaRepository<LeaveApproval, Long> {

    List<LeaveApproval> findByLeaveRequestIdAndAction(Long leaveRequestId, String action);

    @Query("SELECT a FROM LeaveApproval a " +
            "JOIN FETCH a.leaveRequest r " +
            "JOIN FETCH r.leaveType " +
            "JOIN FETCH r.employee " +
            "WHERE a.approver.id = :approverId " +
            "AND a.action = 'PENDING' " +
            "AND a.approvalType = :origType " +
            "AND a.approvalLevel = r.currentLevel " +
            "AND (r.cancellationStatus IS NULL OR r.cancellationStatus IN :safeStatuses)")
    List<LeaveApproval> findPendingOriginalApprovals(
            @Param("approverId") Long approverId,
            @Param("origType") ApprovalType origType,
            @Param("safeStatuses") List<CancellationStatus> safeStatuses
    );

    @Query("SELECT a FROM LeaveApproval a " +
            "JOIN FETCH a.leaveRequest r " +
            "JOIN FETCH r.leaveType " +
            "JOIN FETCH r.employee " +
            "WHERE a.approver.id = :approverId " +
            "AND a.action = 'PENDING' " +
            "AND a.approvalType = :cancelType " +
            "AND r.cancellationStatus = :pendingStatus")
    List<LeaveApproval> findPendingCancellationApprovals(
            @Param("approverId") Long approverId,
            @Param("cancelType") ApprovalType cancelType,
            @Param("pendingStatus") CancellationStatus pendingStatus
    );

    @Query("SELECT a FROM LeaveApproval a " +
            "WHERE a.leaveRequest.id = :leaveRequestId " +
            "AND a.approvalType = :origType " +
            "AND a.action = 'APPROVED' " +
            "ORDER BY a.approvalLevel ASC")
    List<LeaveApproval> findApprovedOriginals(
            @Param("leaveRequestId") Long leaveRequestId,
            @Param("origType") ApprovalType origType
    );
    @Query("SELECT a FROM LeaveApproval a " +
            "WHERE a.leaveRequest.id = :leaveRequestId " +
            "AND a.approvalType = 'ORIGINAL' " +
            "AND a.action = 'APPROVED' " +
            "ORDER BY a.approvalLevel ASC")
    List<LeaveApproval> findApprovedOriginals(@Param("leaveRequestId") Long leaveRequestId);
    @Query("SELECT a FROM LeaveApproval a " +
            "JOIN FETCH a.approver " +
            "WHERE a.leaveRequest.id = :leaveRequestId " +
            "ORDER BY a.approvalLevel ASC")
    List<LeaveApproval> findByLeaveRequestIdOrderByApprovalLevelAsc(@Param("leaveRequestId") Long leaveRequestId);

    @Query("SELECT la FROM LeaveApproval la LEFT JOIN FETCH la.approver WHERE la.leaveRequest.id = :leaveRequestId ORDER BY la.id ASC")
    List<LeaveApproval> findAllByLeaveRequestIdChronological(@Param("leaveRequestId") Long leaveRequestId);

    @EntityGraph(attributePaths = {"leaveRequest", "leaveRequest.employee", "leaveRequest.leaveType"})
    List<LeaveApproval> findByApproverIdAndAction(Long approverId, String action);

    @Query("SELECT COUNT(la) FROM LeaveApproval la WHERE la.action = 'PENDING'")
    Long countPendingEscalations();
}
