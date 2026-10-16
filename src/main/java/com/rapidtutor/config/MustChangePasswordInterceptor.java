package com.rapidtutor.config;

import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;

@Component
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public MustChangePasswordInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal.getName() == null || "anonymousUser".equals(principal.getName())) {
            return true;
        }
        User user = userRepository.findByUsername(principal.getName());
        if (user != null && user.isMustChangePassword()) {
            response.sendRedirect(request.getContextPath() + "/account/password");
            return false;
        }
        return true;
    }
}
