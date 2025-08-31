package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByTransformerRecordId(Long transformerRecordId);
}
