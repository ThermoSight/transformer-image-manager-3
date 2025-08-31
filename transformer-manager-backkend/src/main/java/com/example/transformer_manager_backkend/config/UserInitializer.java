package com.example.transformer_manager_backkend.config;

import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
public class UserInitializer {

    private static final Logger logger = LoggerFactory.getLogger(UserInitializer.class);

    @Bean
    @ConditionalOnProperty(name = "user.init.enabled", havingValue = "true")
    public CommandLineRunner initUsers(UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            logger.info("Starting user initialization...");
            List<User> users = Stream.of(
                    new User("user1", passwordEncoder.encode("user1pass"), "User One"),
                    new User("user2", passwordEncoder.encode("user2pass"), "User Two"),
                    new User("user3", passwordEncoder.encode("user3pass"), "User Three"),
                    new User("user4", passwordEncoder.encode("user4pass"), "User Four"))
                    .collect(Collectors.toList());

            int createdCount = 0;
            for (User user : users) {
                if (!userRepository.existsByUsername(user.getUsername())) {
                    userRepository.save(user);
                    createdCount++;
                    logger.info("Created user: {}", user.getUsername());
                } else {
                    logger.info("User already exists: {}", user.getUsername());
                }
            }
            logger.info("User initialization complete. Created {} users.", createdCount);
        };
    }
}
