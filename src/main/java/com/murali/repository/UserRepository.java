package com.murali.repository;

import com.murali.entity.User;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    @EntityGraph(attributePaths = {"role"})
    User findByUsername(String username);

    long countByActiveTrue();

    List<User> findByRoleName(String roleName);

    @Query("SELECT u FROM User u WHERE u.role.hierarchyWeight >= :weight ORDER BY u.role.hierarchyWeight ASC")
    List<User> findEligibleApproversByWeight(@Param("weight") Integer weight);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findWithRoleById(Long id);
}
