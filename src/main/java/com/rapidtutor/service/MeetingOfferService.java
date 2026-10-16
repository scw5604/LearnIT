package com.rapidtutor.service;

import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MeetingOfferService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final MeetingOfferRepository offerRepository;
    private final TutoringRequestRepository requestRepository;
    private final NotificationService notificationService;
    private final RequestLifecycle lifecycle;
    private final Clock clock;

    public MeetingOfferService(MeetingOfferRepository offerRepository, TutoringRequestRepository requestRepository,
                               NotificationService notificationService, RequestLifecycle lifecycle, Clock clock) {
        this.offerRepository = offerRepository;
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    public void saveOffer(User tutor, Long requestId, LocalDateTime offeredTime) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("That request does not exist."));
        if (!SubjectMatcher.covers(tutor.getSubjects(), request.getCourse())) {
            throw new IllegalArgumentException("This request is outside your subjects.");
        }
        if (!"OPEN".equals(request.getStatus())) {
            throw new IllegalStateException("This request is no longer available.");
        }
        requireFuture(offeredTime);

        MeetingOffer offer = offerRepository.findByRequestIdAndTutorId(requestId, tutor.getId())
                .orElseGet(MeetingOffer::new);
        offer.setRequestId(requestId);
        offer.setTutorId(tutor.getId());
        offer.setOfferedTime(offeredTime);
        offerRepository.save(offer);
        notificationService.notify(request.getStudentId(),
                tutor.getPublicName() + " offered " + offeredTime.format(WHEN) + ".",
                "/student/requests/" + requestId);
    }

    @Transactional
    public void withdraw(User tutor, Long requestId, Long offerId) {
        MeetingOffer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("That offer is no longer available."));
        if (!requestId.equals(offer.getRequestId()) || !tutor.getId().equals(offer.getTutorId())) {
            throw new IllegalArgumentException("You can only withdraw your own offer.");
        }
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("That request does not exist."));
        if (!"OPEN".equals(request.getStatus())) {
            throw new IllegalStateException("This request is no longer open.");
        }
        String when = offer.getOfferedTime() == null ? "a time" : offer.getOfferedTime().format(WHEN);
        offerRepository.delete(offer);
        notificationService.notify(request.getStudentId(),
                tutor.getPublicName() + " withdrew the " + when + " offer.",
                "/student/requests/" + requestId);
    }

    /** Accepting an offer is the claim. The request stays open until this runs. */
    @Transactional
    public void accept(User student, Long requestId, Long offerId) {
        MeetingOffer offer = offerRepository.findById(offerId).orElse(null);
        if (offer == null || !requestId.equals(offer.getRequestId())) {
            throw new IllegalArgumentException("That offer is no longer available.");
        }

        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("That request does not exist."));
        if (!student.getId().equals(request.getStudentId())) {
            throw new IllegalArgumentException("You can only manage your own requests.");
        }
        if (!"OPEN".equals(request.getStatus())) {
            throw new IllegalStateException("This request was already claimed.");
        }

        request.setStatus("CLAIMED");
        request.setAssignedTutorId(offer.getTutorId());
        request.setClaimedTimestamp(LocalDateTime.now(clock));
        request.setOfferedMeetingTime(offer.getOfferedTime());
        requestRepository.save(request);
        offerRepository.deleteByRequestId(requestId);
        lifecycle.record(requestId, "OPEN", "CLAIMED", student.getUsername());
        notificationService.notify(offer.getTutorId(),
                student.getPublicName() + " accepted your time.",
                "/tutor/requests/" + requestId);
    }

    @Transactional
    public void deleteOffers(Long requestId) {
        offerRepository.deleteByRequestId(requestId);
    }

    private void requireFuture(LocalDateTime offeredTime) {
        if (offeredTime == null || !offeredTime.isAfter(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("Choose a meeting time in the future.");
        }
    }
}
