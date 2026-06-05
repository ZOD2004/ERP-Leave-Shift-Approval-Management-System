package com.murali.repository;

import com.murali.entity.LeaveApproval;
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
            "AND a.approvalLevel = r.currentLevel")
    List<LeaveApproval> findActivePendingApprovalsForUser(@Param("approverId") Long approverId);
//    List<LeaveApproval> findByLeaveRequestIdOrderByApprovalLevelAsc(Long leaveRequestId);

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
