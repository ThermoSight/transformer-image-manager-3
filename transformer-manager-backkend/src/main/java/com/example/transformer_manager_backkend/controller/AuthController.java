// AuthController.java
package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.config.JwtUtil;
import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.repository.UserRepository;
import com.example.transformer_manager_backkend.service.UniversalDetailsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UniversalDetailsService universalDetailsService;
    private final JwtUtil jwtUtil;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager,
            UniversalDetailsService universalDetailsService,
            JwtUtil jwtUtil,
            AdminRepository adminRepository,
            UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.universalDetailsService = universalDetailsService;
        this.jwtUtil = jwtUtil;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthRequest authRequest) throws Exception {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));
        } catch (Exception e) {
            throw new Exception("Incorrect username or password", e);
        }

        final UserDetails userDetails = universalDetailsService.loadUserByUsername(authRequest.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails);
        final String role = universalDetailsService.getUserRole(authRequest.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("role", role);

        if ("ADMIN".equals(role)) {
            Admin admin = adminRepository.findByUsername(authRequest.getUsername())
                    .orElseThrow(() -> new Exception("Admin not found"));
            response.put("user", admin);
        } else if ("USER".equals(role)) {
            User user = userRepository.findByUsername(authRequest.getUsername())
                    .orElseThrow(() -> new Exception("User not found"));
            response.put("user", user);
        }

        return ResponseEntity.ok(response);
    }

    public static class AuthRequest {
        private String username;
        private String password;

        // Getters and Setters
        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}