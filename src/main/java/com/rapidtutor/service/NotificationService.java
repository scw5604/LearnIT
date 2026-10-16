package com.rapidtutor.service;

import com.rapidtutor.model.Notification;
import com.rapidtutor.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(Long recipientUserId, String message, String link) {
        if (recipientUserId == null || message == null || message.isBlank()) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setMessage(message.trim());
        notification.setLink(link);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
}
