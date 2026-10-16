package com.rapidtutor.config;

import com.rapidtutor.model.User;
import com.rapidtutor.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        User user = userRepository.findByUsername(authentication.getName());
        if (user != null && user.isMustChangePassword()) {
            response.sendRedirect("/account/password");
            return;
        }

        String role = authentication.getAuthorities().iterator().next().getAuthority();
        if (role.equals("ROLE_STUDENT")) {
            response.sendRedirect("/student/dashboard");
        } else if (role.equals("ROLE_TUTOR")) {
            response.sendRedirect("/tutor/board");
        } else if (role.equals("ROLE_ADMIN")) {
            response.sendRedirect("/admin");
        } else if (role.equals("ROLE_APPLICANT") || role.equals("ROLE_DECLINED")) {
            response.sendRedirect("/apply/status");
        } else {
            response.sendRedirect("/login");
        }
    }
}
