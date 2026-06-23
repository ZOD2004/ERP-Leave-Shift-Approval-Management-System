package com.murali.repository;

import com.murali.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    @EntityGraph(attributePaths = {"approvalPolicy"})
    List<LeaveType> findAll();

    @EntityGraph(attributePaths = {"approvalPolicy"})
    List<LeaveType> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

    @EntityGraph(attributePaths = {"approvalPolicy"})
    Optional<LeaveType> findByCode(String code);
}
