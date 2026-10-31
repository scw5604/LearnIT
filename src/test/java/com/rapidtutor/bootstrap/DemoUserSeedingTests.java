package com.rapidtutor.bootstrap;

import com.rapidtutor.model.Courses;
import com.rapidtutor.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoUserSeedingTests {

    @Test
    void rejectsBlankShortAndSharedPasswords() {
        assertFalse(DataSeeder.acceptableLocalAccount("lt-student", "password"));
        assertFalse(DataSeeder.acceptableLocalAccount("lt-student", "short"));
        assertFalse(DataSeeder.acceptableLocalAccount("  ", "long-enough-password"));
        assertFalse(DataSeeder.acceptableLocalAccount("lt-student", "lt-student"));
        assertTrue(DataSeeder.acceptableLocalAccount("lt-student", "long-enough-password"));
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "spring.datasource.url=jdbc:h2:mem:rapidtutor-demo-off;DB_CLOSE_DELAY=-1",
            "rapidtutor.demo-users.enabled=false",
            "rapidtutor.demo-users.student.username=local-student",
            "rapidtutor.demo-users.student.password=local-student-pass",
            "rapidtutor.demo-users.tutor.username=local-tutor",
            "rapidtutor.demo-users.tutor.password=local-tutor-pass",
            "rapidtutor.demo-users.admin.username=local-admin",
            "rapidtutor.demo-users.admin.password=local-admin-pass"
    })
    class WhenDemoUsersAreDisabled {

        @Autowired
        private UserRepository userRepository;

        @Test
        void onlyTheTestFixturesExist() {
            assertEquals(3, userRepository.count());
            assertNull(userRepository.findByUsername("local-student"));
            assertEquals("STUDENT", userRepository.findByUsername("student1").getRole());
            assertEquals("TUTOR", userRepository.findByUsername("tutor1").getRole());
            assertEquals("ADMIN", userRepository.findByUsername("admin").getRole());
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {
            "spring.datasource.url=jdbc:h2:mem:rapidtutor-demo-on;DB_CLOSE_DELAY=-1",
            "rapidtutor.demo-users.enabled=true",
            "rapidtutor.demo-users.student.username=local-student",
            "rapidtutor.demo-users.student.password=local-student-pass",
            "rapidtutor.demo-users.tutor.username=local-tutor",
            "rapidtutor.demo-users.tutor.password=local-tutor-pass",
            "rapidtutor.demo-users.admin.username=local-admin",
            "rapidtutor.demo-users.admin.password=local-admin-pass"
    })
    class WhenDemoUsersAreEnabled {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Test
        void localAccountsAreStoredBesideTheTestFixtures() {
            assertEquals(6, userRepository.count());
            assertEquals("STUDENT", userRepository.findByUsername("local-student").getRole());
            assertEquals(100, userRepository.findByUsername("local-student").getTokenBalance());
            assertEquals("local-student@rapidtutor.local", userRepository.findByUsername("local-student").getEmail());
            assertTrue(userRepository.findByUsername("local-student").getPassword().startsWith("{bcrypt}"));
            assertTrue(passwordEncoder.matches("local-student-pass",
                    userRepository.findByUsername("local-student").getPassword()));
            assertFalse(userRepository.findByUsername("local-student").getPassword().contains("local-student-pass"));

            assertEquals("TUTOR", userRepository.findByUsername("local-tutor").getRole());
            assertEquals(String.join(", ", Courses.OFFERED), userRepository.findByUsername("local-tutor").getSubjects());
            assertEquals("ADMIN", userRepository.findByUsername("local-admin").getRole());
            assertEquals(0, userRepository.findByUsername("local-admin").getTokenBalance());
        }
    }
}
