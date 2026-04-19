package com.cognizant.Notification_service.service;

import com.cognizant.Notification_service.dto.NotificationRequest;
import com.cognizant.Notification_service.entity.Notification;
import com.cognizant.Notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private NotificationRequest notificationRequest;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup test data
        notificationRequest = new NotificationRequest();
        notificationRequest.setMessage("Test notification message");
        notificationRequest.setUsername("testuser");
        notificationRequest.setType("INFO");

        testNotification = new Notification();
        testNotification.setId(1L);
        testNotification.setMessage("Test notification message");
        testNotification.setUsername("testuser");
        testNotification.setType("INFO");
    }

    // ========== CREATE NOTIFICATION TESTS ==========

    @Test
    @DisplayName("Should create notification successfully")
    void testCreateNotificationSuccess() {
        // Arrange
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act
        notificationService.createNotification(notificationRequest);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should set all notification fields correctly on creation")
    void testCreateNotificationSetsAllFields() {
        // Arrange
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            assertEquals("Test notification message", notification.getMessage());
            assertEquals("testuser", notification.getUsername());
            assertEquals("INFO", notification.getType());
            return notification;
        });

        // Act
        notificationService.createNotification(notificationRequest);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should capture and save notification with correct message")
    void testCreateNotificationCapturesMessage() {
        // Arrange
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act
        notificationService.createNotification(notificationRequest);

        // Assert
        verify(notificationRepository).save(captor.capture());
        Notification savedNotification = captor.getValue();
        assertEquals("Test notification message", savedNotification.getMessage());
    }

    @Test
    @DisplayName("Should capture and save notification with correct username")
    void testCreateNotificationCapturesUsername() {
        // Arrange
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act
        notificationService.createNotification(notificationRequest);

        // Assert
        verify(notificationRepository).save(captor.capture());
        Notification savedNotification = captor.getValue();
        assertEquals("testuser", savedNotification.getUsername());
    }

    @Test
    @DisplayName("Should capture and save notification with correct type")
    void testCreateNotificationCapturesType() {
        // Arrange
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act
        notificationService.createNotification(notificationRequest);

        // Assert
        verify(notificationRepository).save(captor.capture());
        Notification savedNotification = captor.getValue();
        assertEquals("INFO", savedNotification.getType());
    }

    @Test
    @DisplayName("Should create notification with different types")
    void testCreateNotificationWithDifferentTypes() {
        // Arrange
        String[] types = {"INFO", "WARNING", "ERROR", "SUCCESS"};
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act & Assert
        for (String type : types) {
            notificationRequest.setType(type);
            notificationService.createNotification(notificationRequest);
        }

        verify(notificationRepository, times(types.length)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should create notification with empty message")
    void testCreateNotificationWithEmptyMessage() {
        // Arrange
        notificationRequest.setMessage("");
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // Act
        notificationService.createNotification(notificationRequest);

        // Assert
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    // ========== GET USER NOTIFICATION TESTS ==========

    @Test
    @DisplayName("Should retrieve notifications for specific user successfully")
    void testGetUserNotificationSuccess() {
        // Arrange
        List<Notification> notifications = new ArrayList<>();
        notifications.add(testNotification);
        when(notificationRepository.findByUsername("testuser")).thenReturn(notifications);

        // Act
        List<Notification> result = notificationService.getUserNotification("testuser");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("testuser", result.get(0).getUsername());
        verify(notificationRepository, times(1)).findByUsername("testuser");
    }

    @Test
    @DisplayName("Should return empty list when user has no notifications")
    void testGetUserNotificationEmptyList() {
        // Arrange
        when(notificationRepository.findByUsername("newuser")).thenReturn(new ArrayList<>());

        // Act
        List<Notification> result = notificationService.getUserNotification("newuser");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(notificationRepository, times(1)).findByUsername("newuser");
    }

    @Test
    @DisplayName("Should return all notifications for user with multiple notifications")
    void testGetUserNotificationMultipleNotifications() {
        // Arrange
        Notification notif1 = new Notification();
        notif1.setId(1L);
        notif1.setMessage("Message 1");
        notif1.setUsername("testuser");
        notif1.setType("INFO");

        Notification notif2 = new Notification();
        notif2.setId(2L);
        notif2.setMessage("Message 2");
        notif2.setUsername("testuser");
        notif2.setType("WARNING");

        Notification notif3 = new Notification();
        notif3.setId(3L);
        notif3.setMessage("Message 3");
        notif3.setUsername("testuser");
        notif3.setType("ERROR");

        List<Notification> notifications = Arrays.asList(notif1, notif2, notif3);
        when(notificationRepository.findByUsername("testuser")).thenReturn(notifications);

        // Act
        List<Notification> result = notificationService.getUserNotification("testuser");

        // Assert
        assertEquals(3, result.size());
        assertEquals("Message 1", result.get(0).getMessage());
        assertEquals("Message 2", result.get(1).getMessage());
        assertEquals("Message 3", result.get(2).getMessage());
        verify(notificationRepository, times(1)).findByUsername("testuser");
    }

    @Test
    @DisplayName("Should return notifications with correct content for specific user")
    void testGetUserNotificationReturnsCorrectContent() {
        // Arrange
        Notification notification = new Notification();
        notification.setId(10L);
        notification.setMessage("Important update");
        notification.setUsername("user123");
        notification.setType("SUCCESS");

        when(notificationRepository.findByUsername("user123")).thenReturn(Arrays.asList(notification));

        // Act
        List<Notification> result = notificationService.getUserNotification("user123");

        // Assert
        assertEquals(1, result.size());
        assertEquals("Important update", result.get(0).getMessage());
        assertEquals("user123", result.get(0).getUsername());
        assertEquals("SUCCESS", result.get(0).getType());
    }

    @Test
    @DisplayName("Should handle null username in getUserNotification")
    void testGetUserNotificationWithNullUsername() {
        // Arrange
        when(notificationRepository.findByUsername(null)).thenReturn(new ArrayList<>());

        // Act
        List<Notification> result = notificationService.getUserNotification(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ========== BROADCAST TESTS ==========

    @Test
    @DisplayName("Should broadcast message to all users successfully")
    void testBroadcastSuccess() {
        // Arrange
        Notification notif1 = new Notification();
        notif1.setId(1L);
        notif1.setUsername("user1");
        notif1.setMessage("Old message");

        Notification notif2 = new Notification();
        notif2.setId(2L);
        notif2.setUsername("user2");
        notif2.setMessage("Old message");

        List<Notification> allNotifications = Arrays.asList(notif1, notif2);
        when(notificationRepository.findAll()).thenReturn(allNotifications);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.broadcast("Broadcast message");

        // Assert
        verify(notificationRepository, times(1)).findAll();
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should set same broadcast message for all notifications")
    void testBroadcastSetsSameMessageForAll() {
        // Arrange
        Notification notif1 = new Notification();
        notif1.setId(1L);
        notif1.setMessage("Old");

        Notification notif2 = new Notification();
        notif2.setId(2L);
        notif2.setMessage("Old");

        List<Notification> allNotifications = Arrays.asList(notif1, notif2);
        when(notificationRepository.findAll()).thenReturn(allNotifications);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.broadcast("Urgent: System maintenance");

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        
        List<Notification> savedNotifications = captor.getAllValues();
        for (Notification notification : savedNotifications) {
            assertEquals("Urgent: System maintenance", notification.getMessage());
        }
    }

    @Test
    @DisplayName("Should broadcast to empty notification list")
    void testBroadcastToEmptyList() {
        // Arrange
        when(notificationRepository.findAll()).thenReturn(new ArrayList<>());

        // Act
        notificationService.broadcast("Broadcast message");

        // Assert
        verify(notificationRepository, times(1)).findAll();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should broadcast to single notification")
    void testBroadcastToSingleNotification() {
        // Arrange
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setMessage("Old message");

        List<Notification> allNotifications = Arrays.asList(notification);
        when(notificationRepository.findAll()).thenReturn(allNotifications);
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // Act
        notificationService.broadcast("Single broadcast");

        // Assert
        verify(notificationRepository, times(1)).findAll();
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should broadcast to multiple notifications and save each one")
    void testBroadcastMultipleNotifications() {
        // Arrange
        List<Notification> allNotifications = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Notification notif = new Notification();
            notif.setId((long) i);
            notif.setUsername("user" + i);
            notif.setMessage("Old message " + i);
            allNotifications.add(notif);
        }

        when(notificationRepository.findAll()).thenReturn(allNotifications);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.broadcast("System maintenance scheduled");

        // Assert
        verify(notificationRepository, times(1)).findAll();
        verify(notificationRepository, times(5)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should preserve user information during broadcast")
    void testBroadcastPreservesUserInfo() {
        // Arrange
        Notification notif1 = new Notification();
        notif1.setId(1L);
        notif1.setUsername("alice");
        notif1.setType("ALERT");
        notif1.setMessage("Old");

        Notification notif2 = new Notification();
        notif2.setId(2L);
        notif2.setUsername("bob");
        notif2.setType("WARNING");
        notif2.setMessage("Old");

        List<Notification> allNotifications = Arrays.asList(notif1, notif2);
        when(notificationRepository.findAll()).thenReturn(allNotifications);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.broadcast("New broadcast");

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        
        List<Notification> savedNotifications = captor.getAllValues();
        assertEquals("alice", savedNotifications.get(0).getUsername());
        assertEquals("bob", savedNotifications.get(1).getUsername());
        assertEquals("ALERT", savedNotifications.get(0).getType());
        assertEquals("WARNING", savedNotifications.get(1).getType());
    }

    @Test
    @DisplayName("Should broadcast empty message string")
    void testBroadcastEmptyMessage() {
        // Arrange
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setMessage("Old");

        List<Notification> allNotifications = Arrays.asList(notification);
        when(notificationRepository.findAll()).thenReturn(allNotifications);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.broadcast("");

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals("", captor.getValue().getMessage());
    }

    // ========== INTEGRATION SCENARIO TESTS ==========

    @Test
    @DisplayName("Should create notification and retrieve it for user")
    void testCreateAndRetrieveNotification() {
        // Arrange
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);
        when(notificationRepository.findByUsername("testuser")).thenReturn(Arrays.asList(testNotification));

        // Act
        notificationService.createNotification(notificationRequest);
        List<Notification> result = notificationService.getUserNotification("testuser");

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test notification message", result.get(0).getMessage());
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationRepository, times(1)).findByUsername("testuser");
    }

    @Test
    @DisplayName("Should create multiple notifications for same user")
    void testCreateMultipleNotificationsForSameUser() {
        // Arrange
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        NotificationRequest request2 = new NotificationRequest();
        request2.setMessage("Second notification");
        request2.setUsername("testuser");
        request2.setType("WARNING");

        List<Notification> userNotifications = new ArrayList<>();
        userNotifications.add(testNotification);
        Notification notif2 = new Notification();
        notif2.setMessage("Second notification");
        notif2.setUsername("testuser");
        notif2.setType("WARNING");
        userNotifications.add(notif2);

        when(notificationRepository.findByUsername("testuser")).thenReturn(userNotifications);

        // Act
        notificationService.createNotification(notificationRequest);
        notificationService.createNotification(request2);
        List<Notification> result = notificationService.getUserNotification("testuser");

        // Assert
        assertEquals(2, result.size());
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(notificationRepository, times(1)).findByUsername("testuser");
    }

}

