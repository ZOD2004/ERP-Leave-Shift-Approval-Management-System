package com.murali.repository;

import com.murali.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType,Long> {
    List<LeaveType> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);
    List<LeaveType> findAll();

    Optional<LeaveType> findByCode(String code);

}
