package com.rapidtutor.service;

import com.rapidtutor.model.MeetingOffer;
import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.model.User;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TriageCloseService {

    private final TutoringRequestRepository requestRepository;
    private final MeetingOfferRepository offerRepository;
    private final EconomyService economyService;
    private final KnowledgeBankService knowledgeBankService;
    private final NotificationService notificationService;
    private final RequestLifecycle requestLifecycle;

    public TriageCloseService(TutoringRequestRepository requestRepository, MeetingOfferRepository offerRepository,
                              EconomyService economyService, KnowledgeBankService knowledgeBankService,
                              NotificationService notificationService, RequestLifecycle requestLifecycle) {
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.economyService = economyService;
        this.knowledgeBankService = knowledgeBankService;
        this.notificationService = notificationService;
        this.requestLifecycle = requestLifecycle;
    }

    /** Refunds the bounty, resolves the open request, and stores the note. */
    @Transactional
    public void close(User student, Long requestId) {
        TutoringRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("That request does not exist."));
        if (!student.getId().equals(request.getStudentId())) {
            throw new IllegalArgumentException("You can only manage your own requests.");
        }
        List<MeetingOffer> offers = offerRepository.findByRequestIdOrderByOfferedTimeAsc(requestId);
        economyService.resolveFromTriage(requestId);
        offerRepository.deleteByRequestId(requestId);
        for (MeetingOffer offer : offers) {
            notificationService.notify(offer.getTutorId(),
                    student.getPublicName() + " closed the request from the diagnostic note.",
                    "/tutor/board");
        }
        knowledgeBankService.saveTriage(request.getCourse(), request.getTopic(), request.getAiDiagnosticNote());
        requestLifecycle.record(requestId, "OPEN", "RESOLVED", student.getUsername());
    }
}
