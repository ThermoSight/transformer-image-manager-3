package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.MLSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MLSettingsRepository extends JpaRepository<MLSettings, Long> {

    Optional<MLSettings> findBySettingKey(String settingKey);

    boolean existsBySettingKey(String settingKey);
}