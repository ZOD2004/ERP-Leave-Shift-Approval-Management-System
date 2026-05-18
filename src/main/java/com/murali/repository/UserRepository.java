package com.murali.repository;

import com.murali.entity.User;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    @PermitAll
    public User findByUsername(String username);

    List<User> findFirstByRoleName(String roleHr);
}
