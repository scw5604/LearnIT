package com.rapidtutor.service;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.repository.MeetingOfferRepository;
import com.rapidtutor.repository.TutoringRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RequestExpiration {

    private final TutoringRequestRepository requestRepository;
    private final MeetingOfferRepository offerRepository;
    private final EconomyService economyService;
    private final RequestLifecycle lifecycle;
    private final Clock clock;

    public RequestExpiration(TutoringRequestRepository requestRepository, MeetingOfferRepository offerRepository,
                             EconomyService economyService, RequestLifecycle lifecycle, Clock clock) {
        this.requestRepository = requestRepository;
        this.offerRepository = offerRepository;
        this.economyService = economyService;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    @Transactional
    public void expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (TutoringRequest request : requestRepository.findByStatus("OPEN")) {
            LocalDateTime deadline = deadline(request);
            if (deadline != null && !now.isBefore(deadline)) {
                economyService.expireAndRefund(request.getId());
                offerRepository.deleteByRequestId(request.getId());
                lifecycle.record(request.getId(), "OPEN", "EXPIRED", "system");
            }
        }
    }

    static LocalDateTime deadline(TutoringRequest request) {
        if (request.getCreationTimestamp() == null || request.getDesiredResponseTime() == null) {
            return null;
        }
        LocalDateTime created = request.getCreationTimestamp();
        return switch (request.getDesiredResponseTime()) {
            case "ASAP" -> created.plusHours(1);
            case "Within 2 hours" -> created.plusHours(2);
            case "Today" -> created.toLocalDate().plusDays(1).atStartOfDay();
            default -> null;
        };
    }
}
