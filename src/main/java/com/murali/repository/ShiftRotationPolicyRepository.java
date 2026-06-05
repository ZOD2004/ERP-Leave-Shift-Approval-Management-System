package com.murali.repository;

import com.murali.entity.ShiftRotationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRotationPolicyRepository extends JpaRepository<ShiftRotationPolicy, Long> {

    // Finds active policies that haven't expired before the target window starts
    @Query("SELECT p FROM ShiftRotationPolicy p WHERE p.active = true AND (p.endDate IS NULL OR p.endDate >= :windowStart)")
    List<ShiftRotationPolicy> findActivePoliciesValidFrom(@Param("windowStart") LocalDate windowStart);

    List<ShiftRotationPolicy> findByActiveTrue();

    @Query("SELECT p FROM ShiftRotationPolicy p JOIN FETCH p.employee")
    List<ShiftRotationPolicy> findAllWithEmployee();

    @Query("SELECT p FROM ShiftRotationPolicy p " +
            "JOIN FETCH p.employee " +
            "LEFT JOIN FETCH p.sequences s " +
            "LEFT JOIN FETCH s.shift " +
            "WHERE p.id = :id")
    Optional<ShiftRotationPolicy> findByIdWithSequences(@Param("id") Long id);
}
