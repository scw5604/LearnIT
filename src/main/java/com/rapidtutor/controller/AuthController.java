package com.rapidtutor.controller;

import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Serves the Thymeleaf registration form
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    // Processes the form submission
    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user) {
        // Prevent duplicate usernames from crashing the database
        if (userRepository.findByUsername(user.getUsername()) != null) {
            return "redirect:/register?error=true";
        }

        // Default all public signups to the Student role[cite: 1]
        user.setRole("STUDENT");

        // Ensure new students get their starting token balance[cite: 4]
        user.setTokenBalance(100);

        // Prepend {noop} to align with your existing Phase 1 MVP password encoder[cite: 1]
        user.setPassword("{noop}" + user.getPassword());

        userRepository.save(user);

        // Redirect back to the login screen upon success
        return "redirect:/login?registered=true";
    }

    // Add this to AuthController.java
    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }
}
