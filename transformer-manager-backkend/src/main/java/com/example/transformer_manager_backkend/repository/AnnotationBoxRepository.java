package com.example.transformer_manager_backkend.repository;

import com.example.transformer_manager_backkend.entity.AnnotationBox;
import com.example.transformer_manager_backkend.entity.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnotationBoxRepository extends JpaRepository<AnnotationBox, Long> {

    /**
     * Find all boxes for a specific annotation
     */
    List<AnnotationBox> findByAnnotation(Annotation annotation);

    /**
     * Find all boxes for a specific annotation ordered by creation time
     */
    List<AnnotationBox> findByAnnotationOrderByCreatedAtAsc(Annotation annotation);

    /**
     * Find boxes by action type
     */
    @Query("SELECT ab FROM AnnotationBox ab WHERE ab.annotation.id = :annotationId AND ab.action = :action")
    List<AnnotationBox> findByAnnotationIdAndAction(@Param("annotationId") Long annotationId,
            @Param("action") AnnotationBox.BoxAction action);

    /**
     * Find user-added boxes (confidence is null)
     */
    @Query("SELECT ab FROM AnnotationBox ab WHERE ab.annotation.id = :annotationId AND ab.confidence IS NULL")
    List<AnnotationBox> findUserAddedBoxesByAnnotationId(@Param("annotationId") Long annotationId);

    /**
     * Find AI-generated boxes (confidence is not null)
     */
    @Query("SELECT ab FROM AnnotationBox ab WHERE ab.annotation.id = :annotationId AND ab.confidence IS NOT NULL")
    List<AnnotationBox> findAIGeneratedBoxesByAnnotationId(@Param("annotationId") Long annotationId);

    /**
     * Count boxes by action type
     */
    @Query("SELECT COUNT(ab) FROM AnnotationBox ab WHERE ab.annotation.id = :annotationId AND ab.action = :action")
    Long countByAnnotationIdAndAction(@Param("annotationId") Long annotationId,
            @Param("action") AnnotationBox.BoxAction action);

    /**
     * Delete all boxes for an annotation
     */
    void deleteByAnnotation(Annotation annotation);
}