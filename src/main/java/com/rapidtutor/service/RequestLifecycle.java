package com.rapidtutor.service;

import com.rapidtutor.model.RequestEvent;
import com.rapidtutor.repository.RequestEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RequestLifecycle {

    private final RequestEventRepository eventRepository;

    public RequestLifecycle(RequestEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void record(Long requestId, String fromStatus, String toStatus, String actorUsername) {
        RequestEvent event = new RequestEvent();
        event.setRequestId(requestId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setActorUsername(actorUsername);
        event.setAt(LocalDateTime.now());
        eventRepository.save(event);
    }
}
