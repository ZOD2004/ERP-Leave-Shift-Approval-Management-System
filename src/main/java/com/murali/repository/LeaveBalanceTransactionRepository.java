package com.murali.repository;

import com.murali.entity.LeaveBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaveBalanceTransactionRepository extends JpaRepository<LeaveBalanceTransaction, Long> {
    @Query("SELECT tx FROM LeaveBalanceTransaction tx " +
            "JOIN FETCH tx.employee e " +
            "JOIN FETCH tx.leaveType lt " +
            "ORDER BY tx.createdAt DESC")
    List<LeaveBalanceTransaction> findAllWithDetails();
}
