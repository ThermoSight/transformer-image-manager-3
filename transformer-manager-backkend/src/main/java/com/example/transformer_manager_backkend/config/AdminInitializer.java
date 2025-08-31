package com.example.transformer_manager_backkend.config;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class AdminInitializer {

    @Bean
    @ConditionalOnProperty(name = "admin.init.enabled", havingValue = "true")
    public CommandLineRunner initAdmins(AdminRepository adminRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            List<Admin> admins = Stream.of(
                    new Admin("admin1", passwordEncoder.encode("admin1pass"), "Admin One"),
                    new Admin("admin2", passwordEncoder.encode("admin2pass"), "Admin Two"),
                    new Admin("admin3", passwordEncoder.encode("admin3pass"), "Admin Three"),
                    new Admin("admin4", passwordEncoder.encode("admin4pass"), "Admin Four"))
                    .collect(Collectors.toList());

            for (Admin admin : admins) {
                if (!adminRepository.existsByUsername(admin.getUsername())) {
                    adminRepository.save(admin);
                }
            }
        };
    }
}