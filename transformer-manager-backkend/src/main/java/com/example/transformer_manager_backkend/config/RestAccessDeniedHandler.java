package com.example.transformer_manager_backkend.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.stream.Collectors;

public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger logger = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authInfo = (auth == null)
                ? "anonymous"
                : (auth.getName() + " " + auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()));

        logger.warn("AccessDenied: path={} method={} auth={}", request.getRequestURI(), request.getMethod(), authInfo);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Access denied\",\"path\":\"" + request.getRequestURI() + "\"}");
    }
}

