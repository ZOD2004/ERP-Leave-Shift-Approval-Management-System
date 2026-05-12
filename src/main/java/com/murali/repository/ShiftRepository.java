package com.murali.repository;

import com.murali.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<Shift,Long> {
    List<Shift> findByNameContainingIgnoreCase(String name);
}
