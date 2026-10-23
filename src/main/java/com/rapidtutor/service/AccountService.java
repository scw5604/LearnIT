package com.rapidtutor.service;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AccountService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    private static final int STARTER_TOKENS = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    public record CreatedAccount(User user, String recoveryCode) {
    }

    @Transactional
    public CreatedAccount registerStudent(String displayName, String email, String username,
                                          String password, String confirmPassword) {
        User user = newAccount(displayName, email, username, password, confirmPassword);
        user.setRole("STUDENT");
        user.setTokenBalance(STARTER_TOKENS);
        String code = attachRecoveryCode(user);
        return new CreatedAccount(userRepository.saveAndFlush(user), code);
    }

    @Transactional
    public CreatedAccount applyAsTutor(String displayName, String email, String username,
                                       String password, String confirmPassword, List<String> courses) {
        String subjects = canonicalCourses(courses);
        User user = newAccount(displayName, email, username, password, confirmPassword);
        user.setRole("APPLICANT");
        user.setTokenBalance(0);
        user.setAppliedSubjects(subjects);
        String code = attachRecoveryCode(user);
        return new CreatedAccount(userRepository.saveAndFlush(user), code);
    }

    @Transactional
    public CreatedAccount createAdministrator(String displayName, String email, String username,
                                              String password, String confirmPassword) {
        User user = newAccount(displayName, email, username, password, confirmPassword);
        user.setRole("ADMIN");
        user.setTokenBalance(0);
        user.setMustChangePassword(true);
        String code = attachRecoveryCode(user);
        return new CreatedAccount(userRepository.saveAndFlush(user), code);
    }

    @Transactional
    public void approveTutor(Long applicantId) {
        User user = userRepository.findById(applicantId)
                .orElseThrow(() -> new IllegalArgumentException("That application does not exist."));
        if (!"APPLICANT".equals(user.getRole())) {
            throw new IllegalStateException("That person is not waiting for approval.");
        }
        if (user.getAppliedSubjects() == null || user.getAppliedSubjects().isBlank()) {
            throw new IllegalStateException("Choose at least one course before approving.");
        }
        user.setRole("TUTOR");
        user.setSubjects(user.getAppliedSubjects());
        userRepository.save(user);
        notificationService.notify(user.getId(), "You are approved as a tutor.", "/tutor/board");
    }

    @Transactional
    public void declineTutor(Long applicantId) {
        User user = userRepository.findById(applicantId)
                .orElseThrow(() -> new IllegalArgumentException("That application does not exist."));
        if (!"APPLICANT".equals(user.getRole())) {
            throw new IllegalStateException("That person is not waiting for approval.");
        }
        user.setRole("DECLINED");
        userRepository.save(user);
        notificationService.notify(user.getId(), "Your tutor application was declined.", "/apply/status");
    }

    @Transactional
    public void resetWithRecoveryCode(String email, String recoveryCode, String password, String confirmPassword) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email));
        String normalized = RecoveryCodes.normalize(recoveryCode);
        if (user == null || user.getRecoveryCodeHash() == null || normalized.isBlank()
                || !passwordEncoder.matches(normalized, user.getRecoveryCodeHash())) {
            throw new IllegalArgumentException("That email and recovery code do not match.");
        }
        applyNewPassword(user, password, confirmPassword);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String password, String confirmPassword) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        applyNewPassword(user, password, confirmPassword);
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private User newAccount(String displayName, String email, String username, String password, String confirmPassword) {
        String cleanName = required(displayName, "Enter your name.", 80);
        String cleanEmail = normalizeEmail(email);
        if (!EMAIL.matcher(cleanEmail).matches() || cleanEmail.length() > 120) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        String cleanUsername = username == null ? "" : username.trim();
        if (!USERNAME.matcher(cleanUsername).matches()) {
            throw new IllegalArgumentException("Username must be 3 to 40 letters, numbers, dots, hyphens, or underscores.");
        }
        if (userRepository.findByEmailIgnoreCase(cleanEmail) != null) {
            throw new IllegalArgumentException("That email is already registered.");
        }
        if (userRepository.findByUsernameIgnoreCase(cleanUsername) != null) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        String weakness = PasswordRules.violation(password, cleanUsername, cleanEmail);
        if (weakness != null) {
            throw new IllegalArgumentException(weakness);
        }
        String mismatch = PasswordRules.confirmationProblem(password, confirmPassword);
        if (mismatch != null) {
            throw new IllegalArgumentException(mismatch);
        }

        User user = new User();
        user.setDisplayName(cleanName);
        user.setEmail(cleanEmail);
        user.setUsername(cleanUsername);
        user.setPassword(passwordEncoder.encode(password));
        user.setMustChangePassword(false);
        return user;
    }

    private void applyNewPassword(User user, String password, String confirmPassword) {
        String weakness = PasswordRules.violation(password, user.getUsername(), user.getEmail());
        if (weakness != null) {
            throw new IllegalArgumentException(weakness);
        }
        String mismatch = PasswordRules.confirmationProblem(password, confirmPassword);
        if (mismatch != null) {
            throw new IllegalArgumentException(mismatch);
        }
        user.setPassword(passwordEncoder.encode(password));
    }

    private String attachRecoveryCode(User user) {
        String code = RecoveryCodes.newCode();
        user.setRecoveryCodeHash(passwordEncoder.encode(code));
        return code;
    }

    private String canonicalCourses(List<String> courses) {
        if (courses == null || courses.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one course.");
        }
        List<String> canonical = new ArrayList<>();
        for (String course : courses) {
            String match = Courses.canonical(course);
            if (match != null && !canonical.contains(match)) {
                canonical.add(match);
            }
        }
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one course.");
        }
        return String.join(", ", canonical);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String required(String value, String message, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Keep that entry under " + max + " characters.");
        }
        return trimmed;
    }
}
