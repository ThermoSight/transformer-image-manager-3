package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.TransformerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransformerRecordRepository extends JpaRepository<TransformerRecord, Long> {
}