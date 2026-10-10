package com.rapidtutor.repository;

import com.rapidtutor.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT AVG(r.score) FROM Review r WHERE r.tutorId = :tutorId")
    Double getAverageScoreByTutorId(@Param("tutorId") Long tutorId);
}
