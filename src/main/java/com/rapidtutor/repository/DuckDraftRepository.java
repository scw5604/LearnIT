package com.rapidtutor.repository;

import com.rapidtutor.model.DuckDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DuckDraftRepository extends JpaRepository<DuckDraft, Long> {

    List<DuckDraft> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    Optional<DuckDraft> findByIdAndStudentId(Long id, Long studentId);
}
