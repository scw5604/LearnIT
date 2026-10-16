package com.rapidtutor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MustChangePasswordInterceptor mustChangePasswordInterceptor;
    private final RequestExpirationInterceptor requestExpirationInterceptor;

    public WebConfig(MustChangePasswordInterceptor mustChangePasswordInterceptor,
                     RequestExpirationInterceptor requestExpirationInterceptor) {
        this.mustChangePasswordInterceptor = mustChangePasswordInterceptor;
        this.requestExpirationInterceptor = requestExpirationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestExpirationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**");
        registry.addInterceptor(mustChangePasswordInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**",
                        "/login",
                        "/logout",
                        "/account/password",
                        "/register",
                        "/forgot-password",
                        "/apply/tutor",
                        "/h2-console/**");
    }
}
