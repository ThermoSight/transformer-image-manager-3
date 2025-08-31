package com.example.transformer_manager_backkend.controller;

import com.example.transformer_manager_backkend.entity.Admin;
import com.example.transformer_manager_backkend.entity.User;
import com.example.transformer_manager_backkend.repository.AdminRepository;
import com.example.transformer_manager_backkend.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public TestController(AdminRepository adminRepository, UserRepository userRepository) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    public Map<String, Object> getAllUsersAndAdmins() {
        Map<String, Object> result = new HashMap<>();

        List<Admin> admins = adminRepository.findAll();
        List<User> users = userRepository.findAll();

        result.put("admins", admins);
        result.put("users", users);
        result.put("adminCount", admins.size());
        result.put("userCount", users.size());

        return result;
    }
}
