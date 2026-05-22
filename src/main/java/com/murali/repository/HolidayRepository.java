package com.murali.repository;

import com.murali.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday,Long> {

    @Query(
            value = """
        SELECT h.holiday_date
        FROM holidays h
        WHERE h.holiday_date BETWEEN :startDate AND :endDate
        ORDER BY h.holiday_date
        """,
            nativeQuery = true
    )
    List<LocalDate> findHolidayDatesBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByHolidayDate(LocalDate holidayDate);

    @Query("""
        SELECT COUNT(h)
        FROM Holiday h
        WHERE h.holidayDate >= :today
          AND MONTH(h.holidayDate) = :month
          AND YEAR(h.holidayDate) = :year
    """)
    long countUpcomingHolidaysInMonth(
            @Param("today") LocalDate today,
            @Param("month") int month,
            @Param("year") int year
    );
}
