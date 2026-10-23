package com.rapidtutor.repository;

import com.rapidtutor.model.MeetingOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingOfferRepository extends JpaRepository<MeetingOffer, Long> {

    List<MeetingOffer> findByRequestIdOrderByOfferedTimeAsc(Long requestId);

    List<MeetingOffer> findByTutorId(Long tutorId);

    Optional<MeetingOffer> findByRequestIdAndTutorId(Long requestId, Long tutorId);

    @Query("SELECT o.requestId, COUNT(o) FROM MeetingOffer o WHERE o.requestId IN :ids GROUP BY o.requestId")
    List<Object[]> countGroupedByRequestId(@Param("ids") Collection<Long> ids);

    void deleteByRequestId(Long requestId);
}
