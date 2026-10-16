package com.rapidtutor.config;

import com.rapidtutor.model.User;
import com.rapidtutor.repository.NotificationRepository;
import com.rapidtutor.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public CurrentUserModelAdvice(UserRepository userRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Principal principal) {
        User user = find(principal);
        return user;
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(Principal principal) {
        User user = find(principal);
        if (user == null) {
            return 0;
        }
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(user.getId());
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    private User find(Principal principal) {
        if (principal == null || principal.getName() == null || "anonymousUser".equals(principal.getName())) {
            return null;
        }
        return userRepository.findByUsername(principal.getName());
    }
}
