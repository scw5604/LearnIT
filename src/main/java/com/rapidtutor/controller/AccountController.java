package com.rapidtutor.controller;

import com.rapidtutor.model.AccountForm;
import com.rapidtutor.model.Courses;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import com.rapidtutor.service.AccountService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
public class AccountController {

    private final AccountService accountService;
    private final UserRepository userRepository;

    public AccountController(AccountService accountService, UserRepository userRepository) {
        this.accountService = accountService;
        this.userRepository = userRepository;
    }

    @GetMapping("/register")
    public String showRegistration(Model model) {
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute AccountForm accountForm, Model model) {
        return createAccount(accountForm, model, false);
    }

    @GetMapping("/apply/tutor")
    public String showTutorApplication(Model model) {
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        model.addAttribute("courses", Courses.OFFERED);
        return "apply-tutor";
    }

    @PostMapping("/apply/tutor")
    public String apply(@ModelAttribute AccountForm accountForm, Model model) {
        return createAccount(accountForm, model, true);
    }

    @GetMapping("/apply/status")
    public String applicationStatus(Principal principal, Model model) {
        User user = current(principal);
        model.addAttribute("applicant", user);
        return "apply-status";
    }

    @GetMapping("/forgot-password")
    public String showForgotPassword(Model model) {
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@ModelAttribute AccountForm accountForm, Model model) {
        try {
            accountService.resetWithRecoveryCode(accountForm.getEmail(), accountForm.getRecoveryCode(),
                    accountForm.getPassword(), accountForm.getConfirmPassword());
            return "redirect:/login?reset=true";
        } catch (IllegalArgumentException e) {
            accountForm.setPassword(null);
            accountForm.setConfirmPassword(null);
            accountForm.setRecoveryCode(null);
            model.addAttribute("accountForm", accountForm);
            model.addAttribute("errorMessage", e.getMessage());
            return "forgot-password";
        }
    }

    @GetMapping("/account/password")
    public String showChangePassword(Principal principal, Model model) {
        User user = current(principal);
        model.addAttribute("mustChange", user != null && user.isMustChangePassword());
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        return "change-password";
    }

    @PostMapping("/account/password")
    public String changePassword(@ModelAttribute AccountForm accountForm, Principal principal, Model model) {
        User user = current(principal);
        try {
            accountService.changePassword(user, accountForm.getCurrentPassword(),
                    accountForm.getPassword(), accountForm.getConfirmPassword());
            if ("ADMIN".equals(user.getRole())) {
                return "redirect:/admin";
            }
            if ("TUTOR".equals(user.getRole())) {
                return "redirect:/tutor/board";
            }
            if ("STUDENT".equals(user.getRole())) {
                return "redirect:/student/dashboard";
            }
            return "redirect:/apply/status";
        } catch (IllegalArgumentException e) {
            model.addAttribute("mustChange", user.isMustChangePassword());
            model.addAttribute("accountForm", new AccountForm());
            model.addAttribute("errorMessage", e.getMessage());
            return "change-password";
        }
    }

    private String createAccount(AccountForm accountForm, Model model, boolean tutor) {
        try {
            AccountService.CreatedAccount created = tutor
                    ? accountService.applyAsTutor(accountForm.getDisplayName(), accountForm.getEmail(),
                    accountForm.getUsername(), accountForm.getPassword(), accountForm.getConfirmPassword(),
                    accountForm.getCourses())
                    : accountService.registerStudent(accountForm.getDisplayName(), accountForm.getEmail(),
                    accountForm.getUsername(), accountForm.getPassword(), accountForm.getConfirmPassword());
            model.addAttribute("recoveryCode", created.recoveryCode());
            model.addAttribute("username", created.user().getUsername());
            return "recovery-code";
        } catch (DataIntegrityViolationException e) {
            return rejectedAccount(accountForm, model, tutor, duplicateAccountMessage(accountForm));
        } catch (IllegalArgumentException e) {
            return rejectedAccount(accountForm, model, tutor, e.getMessage());
        }
    }

    private String rejectedAccount(AccountForm accountForm, Model model, boolean tutor, String message) {
        accountForm.setPassword(null);
        accountForm.setConfirmPassword(null);
        model.addAttribute("accountForm", accountForm);
        model.addAttribute("errorMessage", message);
        if (tutor) {
            model.addAttribute("courses", Courses.OFFERED);
            return "apply-tutor";
        }
        return "register";
    }

    private String duplicateAccountMessage(AccountForm accountForm) {
        String username = accountForm.getUsername() == null ? "" : accountForm.getUsername().trim();
        if (userRepository.findByUsernameIgnoreCase(username) != null) {
            return "That username is already taken.";
        }
        return "That email is already registered.";
    }

    private User current(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(principal.getName());
    }
}
