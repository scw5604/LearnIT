package com.rapidtutor.bootstrap;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Inserts the in-memory users that the test suite signs in as.
 * This class is on the test classpath only.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TestAccountSeeder implements CommandLineRunner {

    static final String PASSWORD = "password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String tutorSubjects = String.join(", ", Courses.OFFERED);
        ensure("student1", "student1@rapidtutor.local", "STUDENT", null, 100);
        ensure("tutor1", "tutor1@rapidtutor.local", "TUTOR", tutorSubjects, 100);
        ensure("admin", "admin@rapidtutor.local", "ADMIN", null, 0);
    }

    private void ensure(String username, String email, String role, String subjects, int tokenBalance) {
        if (userRepository.findByUsername(username) != null) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setSubjects(subjects);
        user.setTokenBalance(tokenBalance);
        userRepository.save(user);
    }
}
