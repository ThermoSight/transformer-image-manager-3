package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.FeedbackSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackSnapshotRepository extends JpaRepository<FeedbackSnapshot, Long> {

    List<FeedbackSnapshot> findTop50ByOrderByCreatedAtDesc();

    List<FeedbackSnapshot> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
}
