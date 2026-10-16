package com.rapidtutor.config;

import com.rapidtutor.service.RequestExpiration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestExpirationInterceptor implements HandlerInterceptor {

    private final RequestExpiration requestExpiration;

    public RequestExpirationInterceptor(RequestExpiration requestExpiration) {
        this.requestExpiration = requestExpiration;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        requestExpiration.expireDue();
        return true;
    }
}
