package com.murali.repository;

import com.murali.entity.ShiftRotationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface ShiftRotationPolicyRepository extends JpaRepository<ShiftRotationPolicy, Long> {

    List<ShiftRotationPolicy> findByActiveTrue();

    @EntityGraph(attributePaths = {"employee"})
    @Query("SELECT p FROM ShiftRotationPolicy p")
    List<ShiftRotationPolicy> findAllWithEmployee();

    @EntityGraph(attributePaths = {"employee", "sequences", "sequences.shift"})
    @Query("SELECT p FROM ShiftRotationPolicy p WHERE p.id = :id")
    Optional<ShiftRotationPolicy> findByIdWithSequences(@Param("id") Long id);

    @EntityGraph(attributePaths = {"sequences", "sequences.shift"})
    @Query("SELECT p FROM ShiftRotationPolicy p WHERE p.employee.id = :employeeId AND p.active = true")
    Optional<ShiftRotationPolicy> findActivePolicyWithSequencesByEmployeeId(@Param("employeeId") Long employeeId);
}
