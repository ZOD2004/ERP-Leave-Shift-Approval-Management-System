package com.murali.repository;

import com.murali.entity.TimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeLogRepository extends JpaRepository<TimeLog,Long> {

    Optional<TimeLog> findFirstByAttendance_Employee_IdOrderByPunchTimeDesc(Long employeeId);

    List<TimeLog> findByAttendanceIdOrderByPunchTimeAsc(Long attendanceId);

    @Query("SELECT t FROM TimeLog t WHERE t.attendance.employee.id = :employeeId AND t.attendance.attendanceDate = :date")
    List<TimeLog> findByEmployeeIdAndAttendanceDate(@Param("employeeId") Long employeeId, @Param("date") LocalDate date);
}
