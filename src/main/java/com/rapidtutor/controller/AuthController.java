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

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user) {
        if (userRepository.findByUsername(user.getUsername()) != null) {
            return "redirect:/register?error=true";
        }
        user.setRole("STUDENT");
        user.setTokenBalance(100);
        user.setPassword("{noop}" + user.getPassword());
        userRepository.save(user);
        return "redirect:/login?registered=true";
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }
}
