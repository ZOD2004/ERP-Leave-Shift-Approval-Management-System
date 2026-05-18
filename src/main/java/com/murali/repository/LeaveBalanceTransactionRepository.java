package com.murali.repository;

import com.murali.entity.LeaveBalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveBalanceTransactionRepository extends JpaRepository<LeaveBalanceTransaction, Long> {
}
