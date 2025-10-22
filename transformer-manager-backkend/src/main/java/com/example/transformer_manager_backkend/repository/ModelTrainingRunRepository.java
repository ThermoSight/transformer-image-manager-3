package com.example.transformer_manager_backkend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.transformer_manager_backkend.entity.ModelTrainingRun;

public interface ModelTrainingRunRepository extends JpaRepository<ModelTrainingRun, Long> {

    Optional<ModelTrainingRun> findByRunId(String runId);

    default List<ModelTrainingRun> findAllNewestFirst() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    Optional<ModelTrainingRun> findTopByOrderByCreatedAtDesc();

    Optional<ModelTrainingRun> findTopByStatusOrderByCreatedAtAsc(ModelTrainingRun.Status status);
}
