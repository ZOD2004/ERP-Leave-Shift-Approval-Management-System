package com.murali.repository;

import com.murali.entity.Attendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId AND a.attendanceDate BETWEEN :startDate AND :endDate ORDER BY a.attendanceDate DESC")
    List<Attendance> findAttendanceHistoryByEmployee(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @EntityGraph(attributePaths = {"employee.user"})
    @Query(" SELECT a FROM Attendance a WHERE a.employee.id IN :employeeIds AND a.attendanceDate = :attendanceDate")
    List<Attendance> findByEmployeeIdsAndAttendanceDate(@Param("employeeIds") List<Long> employeeIds, @Param("attendanceDate") LocalDate attendanceDate);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE " + "(a.attendanceDate BETWEEN :startDate AND :endDate) AND " + "(a.status = com.murali.entity.enums.AttendanceStatus.MISSING_CHECKOUT OR " + "(a.firstCheckIn IS NOT NULL AND a.lastCheckOut IS NULL))")
    long countMissingPunches(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    List<Attendance> findByEmployeeIdInAndAttendanceDate(List<Long> employeeIds, LocalDate attendanceDate);
}