package com.rapidtutor.repository;

import com.rapidtutor.model.MeetingOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingOfferRepository extends JpaRepository<MeetingOffer, Long> {

    List<MeetingOffer> findByRequestIdOrderByOfferedTimeAsc(Long requestId);

    List<MeetingOffer> findByTutorId(Long tutorId);

    Optional<MeetingOffer> findByRequestIdAndTutorId(Long requestId, Long tutorId);

    long countByRequestId(Long requestId);

    void deleteByRequestId(Long requestId);
}
