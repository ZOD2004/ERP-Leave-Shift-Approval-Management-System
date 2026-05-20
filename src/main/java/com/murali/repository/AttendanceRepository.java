package com.murali.repository;

import com.murali.entity.Attendance;
import com.murali.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    List<Attendance> findByEmployeeIdAndAttendanceDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT a FROM Attendance a WHERE a.attendanceDate = :date")
    List<Attendance> findAllByAttendanceDate(@Param("date") LocalDate date);

    Optional<Attendance> findByEmployee_IdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    @Query("SELECT a FROM Attendance a " +
            "LEFT JOIN FETCH a.shiftAssignment sa " +
            "LEFT JOIN FETCH sa.shift " +
            "WHERE a.employee.id = :employeeId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate " +
            "ORDER BY a.attendanceDate DESC")
    List<Attendance> findAttendanceHistoryByEmployee(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT a
        FROM Attendance a
        LEFT JOIN FETCH a.employee e
        LEFT JOIN FETCH e.user
        LEFT JOIN FETCH a.shiftAssignment sa
        LEFT JOIN FETCH sa.shift
        WHERE a.employee.id IN :employeeIds
        AND a.attendanceDate = :attendanceDate
    """)
    List<Attendance> findByEmployeeIdsAndAttendanceDate(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    long countByEmployee_IdInAndAttendanceDateAndStatus(
            List<Long> employeeIds,
            LocalDate attendanceDate,
            String status
    );
}


