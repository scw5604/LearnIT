package com.rapidtutor.controller;

import com.rapidtutor.model.Notification;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.NotificationRepository;
import com.rapidtutor.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationController(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/notifications")
    public String list(Principal principal, Model model) {
        User user = requireUser(principal);
        model.addAttribute("notifications", notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId()));
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable Long id, Principal principal) {
        User user = requireUser(principal);
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification != null && user.getId().equals(notification.getRecipientUserId()) && notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return "redirect:/notifications";
    }

    private User requireUser(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }
}
