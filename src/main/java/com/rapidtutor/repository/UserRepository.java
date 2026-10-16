package com.rapidtutor.repository;

import com.rapidtutor.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    User findByUsernameIgnoreCase(String username);

    User findByEmailIgnoreCase(String email);

    List<User> findByRoleOrderByUsernameAsc(String role);
}
