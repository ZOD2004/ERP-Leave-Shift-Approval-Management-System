package com.murali.repository;

import com.murali.entity.User;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    User findByUsername(String username);

    long countByActiveTrue();

    List<User> findByRoleName(String roleName);

}
