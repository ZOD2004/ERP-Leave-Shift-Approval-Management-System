package com.murali.repository;

import com.murali.entity.TimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeLogRepository extends JpaRepository<TimeLog,Long> {
    @Query("SELECT t FROM TimeLog t WHERE t.attendance.employee.id = :employeeId " +
            "AND t.punchTime >= :windowStart AND t.punchTime <= :windowEnd " +
            "ORDER BY t.punchTime ASC")
    List<TimeLog> findPunchesInShiftWindow(
            @Param("employeeId") Long employeeId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );
    Optional<TimeLog> findFirstByAttendance_Employee_IdOrderByPunchTimeDesc(Long employeeId);

    List<TimeLog> findByAttendanceIdOrderByPunchTimeAsc(Long attendanceId);
}
