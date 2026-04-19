package com.medlab.billing.client;

import com.medlab.billing.dto.NotificationRequest;
import org.springframework.http.ResponseEntity;

/**
 * DEPRECATED — UserClient is no longer active (no @FeignClient annotation).
 * This class is kept for reference only; @Component removed so no Spring bean is created.
 */
public class UserClientFallback implements UserClient {

    @Override
    public ResponseEntity<Void> createNotification(NotificationRequest request) {
        return ResponseEntity.ok().build();
    }
}
