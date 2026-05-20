package com.murali.repository;


import com.murali.entity.AttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

    @Query("SELECT ac FROM AttendanceCorrection ac JOIN FETCH ac.attendance a JOIN FETCH a.employee e WHERE ac.approver.id = :approverId AND ac.status = 'PENDING'")
    List<AttendanceCorrection> findPendingCorrectionsForManager(@Param("approverId") Long approverId);
}
