package com.rapidtutor.service;

import com.rapidtutor.model.TutoringRequest;
import com.rapidtutor.repository.TutoringRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RequestExpiration {

    private static final Logger log = LoggerFactory.getLogger(RequestExpiration.class);
    private static final List<String> EXPIRING = List.of("ASAP", "Within 2 hours", "Today");

    private final TutoringRequestRepository requestRepository;
    private final EconomyService economyService;
    private final Clock clock;

    public RequestExpiration(TutoringRequestRepository requestRepository, EconomyService economyService, Clock clock) {
        this.requestRepository = requestRepository;
        this.economyService = economyService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (TutoringRequest request : requestRepository.findByStatusAndDesiredResponseTimeIn("OPEN", EXPIRING)) {
            LocalDateTime deadline = deadline(request);
            if (deadline != null && !now.isBefore(deadline)) {
                try {
                    economyService.expireAndRefund(request.getId());
                } catch (RuntimeException e) {
                    log.warn("Could not expire request {}: {}", request.getId(), e.getMessage());
                }
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
