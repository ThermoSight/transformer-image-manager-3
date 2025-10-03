package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.AnalysisJob;
import com.example.transformer_manager_backkend.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    // Find jobs by status
    List<AnalysisJob> findByStatusOrderByCreatedAtAsc(AnalysisJob.AnalysisStatus status);

    // Find job by image
    Optional<AnalysisJob> findByImage(Image image);

    // Find jobs by inspection ID
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.image.inspection.id = :inspectionId ORDER BY aj.createdAt ASC")
    List<AnalysisJob> findByInspectionId(@Param("inspectionId") Long inspectionId);

    // Count queued jobs
    Long countByStatus(AnalysisJob.AnalysisStatus status);

    // Find next job in queue
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.status = 'QUEUED' ORDER BY aj.createdAt ASC LIMIT 1")
    Optional<AnalysisJob> findNextQueuedJob();

    // Find jobs by transformer record ID
    @Query("SELECT aj FROM AnalysisJob aj WHERE aj.image.transformerRecord.id = :transformerId ORDER BY aj.createdAt DESC")
    List<AnalysisJob> findByTransformerId(@Param("transformerId") Long transformerId);

    // Find all processing jobs (for cleanup/recovery)
    List<AnalysisJob> findByStatus(AnalysisJob.AnalysisStatus status);
}