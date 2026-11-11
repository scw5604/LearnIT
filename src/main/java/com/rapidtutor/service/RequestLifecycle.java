package com.rapidtutor.service;

import com.rapidtutor.model.RequestEvent;
import com.rapidtutor.repository.RequestEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RequestLifecycle {

    private final RequestEventRepository eventRepository;
    private final Clock clock;

    public RequestLifecycle(RequestEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public void record(Long requestId, String fromStatus, String toStatus, String actorUsername) {
        RequestEvent event = new RequestEvent();
        event.setRequestId(requestId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorUsername(actorUsername);
        event.setAt(LocalDateTime.now(clock));
        eventRepository.save(event);
    }
}
