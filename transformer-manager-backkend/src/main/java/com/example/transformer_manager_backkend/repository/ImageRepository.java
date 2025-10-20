package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {
	Optional<Image> findByFilePath(String filePath);
}