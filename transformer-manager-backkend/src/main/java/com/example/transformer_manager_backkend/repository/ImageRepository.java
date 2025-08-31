package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<Image, Long> {
}