package com.rapidtutor.repository;

import com.rapidtutor.model.TutoringRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TutoringRequestRepository extends JpaRepository<TutoringRequest, Long> {
    List<TutoringRequest> findByStatus(String status);

    List<TutoringRequest> findByStudentId(Long studentId);

    long countByStatus(String status);

    @Query("SELECT r.course, COUNT(r) FROM TutoringRequest r GROUP BY r.course ORDER BY COUNT(r) DESC")
    List<Object[]> countGroupedByCourse();

    @Query("SELECT r.topic, COUNT(r) FROM TutoringRequest r GROUP BY r.topic ORDER BY COUNT(r) DESC")
    List<Object[]> countGroupedByTopic();

    @Query("SELECT COUNT(r) FROM TutoringRequest r WHERE r.status = 'RESOLVED' AND r.aiDiagnosticNote IS NOT NULL AND TRIM(r.aiDiagnosticNote) <> ''")
    long countResolvedWithDiagnosticNote();
}

