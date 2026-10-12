package com.rapidtutor.repository;

import com.rapidtutor.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByRequestId(Long requestId);

    List<Review> findByStudentId(Long studentId);

    @Query("SELECT AVG(r.score) FROM Review r WHERE r.tutorId = :tutorId")
    Double getAverageScoreByTutorId(@Param("tutorId") Long tutorId);

    @Query("SELECT r.tutorId, AVG(r.score), COUNT(r) FROM Review r GROUP BY r.tutorId")
    List<Object[]> summarizeByTutor();
}
