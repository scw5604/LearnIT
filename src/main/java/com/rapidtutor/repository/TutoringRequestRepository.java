package com.rapidtutor.repository;

import com.rapidtutor.model.TutoringRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TutoringRequestRepository extends JpaRepository<TutoringRequest, Long> {
    List<TutoringRequest> findByStatus(String status);

    List<TutoringRequest> findByStudentId(Long studentId);
}

