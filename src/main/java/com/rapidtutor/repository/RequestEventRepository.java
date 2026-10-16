package com.rapidtutor.repository;

import com.rapidtutor.model.RequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestEventRepository extends JpaRepository<RequestEvent, Long> {

    List<RequestEvent> findByRequestIdOrderByAtAsc(Long requestId);
}
