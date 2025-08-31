package com.example.transformer_manager_backkend.service;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UniversalDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UniversalDetailsService.class);

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public UniversalDetailsService(AdminRepository adminRepository, UserRepository userRepository) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.info("Attempting to load user: {}", username);

        // First check if it's an admin
        Admin admin = adminRepository.findByUsername(username).orElse(null);
        if (admin != null) {
            logger.info("Found admin: {}", username);
            return org.springframework.security.core.userdetails.User.builder()
                    .username(admin.getUsername())
                    .password(admin.getPassword())
                    .roles("ADMIN")
                    .build();
        }

        // Then check if it's a user
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            logger.info("Found user: {}", username);
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUsername())
                    .password(user.getPassword())
                    .roles("USER")
                    .build();
        }

        logger.warn("User not found: {}", username);
        throw new UsernameNotFoundException("User not found with username: " + username);
    }

    public String getUserRole(String username) {
        if (adminRepository.findByUsername(username).isPresent()) {
            return "ADMIN";
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return "USER";
        }
        return null;
    }
}
