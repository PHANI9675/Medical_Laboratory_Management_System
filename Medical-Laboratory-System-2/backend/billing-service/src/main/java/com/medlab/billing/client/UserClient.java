package com.medlab.billing.client;

import com.medlab.billing.dto.NotificationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * DEPRECATED — replaced by NotificationClient (POST /notification on notification-service).
 *
 * This interface is no longer used or injected anywhere.
 * The @FeignClient annotation has been removed so no Feign bean is registered.
 * File kept for reference only.
 */
public interface UserClient {

    @PostMapping("/notifications")
    ResponseEntity<Void> createNotification(@RequestBody NotificationRequest request);
}
