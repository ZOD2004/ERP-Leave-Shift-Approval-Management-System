package com.murali.repository;

import com.murali.entity.User;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    @PermitAll
    public User findByUsername(String username);
}
