package com.cognizant.Notification_service.service;

import com.cognizant.Notification_service.dto.NotificationRequest;
import com.cognizant.Notification_service.entity.Notification;
import com.cognizant.Notification_service.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public void createNotification(NotificationRequest request) {
        // Logic to create and save a notification
        Notification n = new Notification();
        n.setMessage(request.getMessage());
        n.setUsername(request.getUsername());
        n.setType(request.getType());
        notificationRepository.save(n);
    }

    public List<Notification> getUserNotification(String username) {
        // Logic to retrieve notifications for a specific user
        return notificationRepository.findByUsername(username);
    }

    public void broadcast(String message) {
        // Logic to broadcast a notification to all users
        List<Notification> notifications = notificationRepository.findAll();
        for (Notification n : notifications) {
            n.setMessage(message);
            notificationRepository.save(n);
        }
    }
}
