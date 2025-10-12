package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.Annotation;
import com.example.transformer_manager_backkend.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    /**
     * Find annotation by analysis job
     */
    Optional<Annotation> findByAnalysisJob(AnalysisJob analysisJob);

    /**
     * Find annotation by analysis job ID
     */
    @Query("SELECT a FROM Annotation a WHERE a.analysisJob.id = :analysisJobId")
    Optional<Annotation> findByAnalysisJobId(@Param("analysisJobId") Long analysisJobId);

    /**
     * Find all annotations for a specific inspection
     */
    @Query("SELECT a FROM Annotation a WHERE a.analysisJob.image.inspection.id = :inspectionId")
    List<Annotation> findByInspectionId(@Param("inspectionId") Long inspectionId);

    /**
     * Find annotations created by a specific user
     */
    @Query("SELECT a FROM Annotation a WHERE a.annotatedByUser.id = :userId")
    List<Annotation> findByAnnotatedByUserId(@Param("userId") Long userId);

    /**
     * Find annotations created by a specific admin
     */
    @Query("SELECT a FROM Annotation a WHERE a.annotatedByAdmin.id = :adminId")
    List<Annotation> findByAnnotatedByAdminId(@Param("adminId") Long adminId);

    /**
     * Find all annotations with feedback log data for export
     */
    @Query("SELECT a FROM Annotation a JOIN FETCH a.analysisJob aj JOIN FETCH aj.image")
    List<Annotation> findAllWithFeedbackData();

    /**
     * Check if annotation exists for analysis job
     */
    boolean existsByAnalysisJob(AnalysisJob analysisJob);

    /**
     * Count annotations by type
     */
    @Query("SELECT COUNT(a) FROM Annotation a WHERE a.annotationType = :type")
    Long countByAnnotationType(@Param("type") Annotation.AnnotationType type);
}