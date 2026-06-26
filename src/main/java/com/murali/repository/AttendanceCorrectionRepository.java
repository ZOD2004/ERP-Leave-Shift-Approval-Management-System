package com.murali.repository;


import com.murali.entity.AttendanceCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

    @EntityGraph(attributePaths = {"attendance", "attendance.employee", "attendance.shiftAssignment", "attendance.shiftAssignment.shift"})
    @Query("SELECT ac FROM AttendanceCorrection ac WHERE ac.approver.id = :approverId AND ac.status = 'PENDING'")
    List<AttendanceCorrection> findPendingCorrectionsForManager(@Param("approverId") Long approverId);


    @EntityGraph(attributePaths = {"attendance", "attendance.employee", "attendance.shiftAssignment", "attendance.shiftAssignment.shift"})
    @Query("SELECT ac FROM AttendanceCorrection ac WHERE ac.status = 'PENDING'")
    List<AttendanceCorrection> findAllPendingCorrectionsGlobally();

    long countByStatus(String status);

    @EntityGraph(attributePaths = {"attendance", "attendance.employee", "approver"})
    List<AttendanceCorrection> findByStatusContaining(String pending);
}