package com.cognizant.Notification_service.controller;

import com.cognizant.Notification_service.dto.NotificationRequest;
import com.cognizant.Notification_service.entity.Notification;
import com.cognizant.Notification_service.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/test")
    public String testNotification() {
        return "Notification service is working!";
    }

    // Open endpoint — no token required (called by other services internally)
    @PostMapping
    public String create(@RequestBody NotificationRequest request) {
        notificationService.createNotification(request);
        return "Notification sent successfully!";
    }

    // FIX: hasRole('PATIENT') → hasAuthority('PATIENT')
    @GetMapping
    @PreAuthorize("hasAuthority('PATIENT')")
    public List<Notification> getNotifications(Authentication auth) {
        return notificationService.getUserNotification(auth.getName());
    }

    // FIX: hasRole('ADMIN') → hasAuthority('ADMIN')
    @PostMapping("/broadcast")
    @PreAuthorize("hasAuthority('ADMIN')")
    public String broadcast(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        notificationService.broadcast(message);
        return "Broadcast notification sent successfully!";
    }
}