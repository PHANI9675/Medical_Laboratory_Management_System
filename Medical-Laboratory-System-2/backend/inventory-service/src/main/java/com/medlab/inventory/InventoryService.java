package com.medlab.inventory;

import com.medlab.inventory.client.NotificationClient;
import com.medlab.inventory.dto.AdjustInventoryRequest;
import com.medlab.inventory.dto.InventoryItemResponse;
import com.medlab.inventory.dto.LowStockNotificationRequest;
import com.medlab.inventory.entity.InventoryItem;
import com.medlab.inventory.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryItemRepository repo;
    private final NotificationClient notificationClient;

    /**
     * Admin username to notify on low-stock events.
     * Configured in application.yaml: inventory.notification.admin-username
     * NOT sent to patients — this is an internal operational alert only.
     */
    @Value("${inventory.notification.admin-username}")
    private String adminUsername;

    public List<InventoryItemResponse> getAllItems() {
        return repo.findAll().stream().map(this::toResponse).toList();
    }

    public InventoryItemResponse adjustStock(AdjustInventoryRequest req) {
        InventoryItem item = repo.findById(req.getItemId())
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + req.getItemId()));

        int newQty = item.getQuantity() + req.getQuantityChange();
        if (newQty < 0) {
            throw new RuntimeException("Insufficient stock. Current quantity: " + item.getQuantity());
        }
        item.setQuantity(newQty);
        InventoryItem saved = repo.save(item);

        // INTEGRATION: send low-stock alert to ADMIN (not patient) via Notification_service
        // Triggered whenever stock falls to or below the item's configured threshold.
        // Failure is non-blocking — a notification error must never roll back a stock adjustment.
        int threshold = saved.getLowStockThreshold() != null ? saved.getLowStockThreshold() : 10;
        if (newQty <= threshold) {
            sendLowStockAlert(saved, newQty, threshold);
        }

        return toResponse(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void sendLowStockAlert(InventoryItem item, int currentQty, int threshold) {
        try {
            String message = String.format(
                    "Low stock alert: '%s' has only %d %s remaining (threshold: %d). Please reorder.",
                    item.getItemName(),
                    currentQty,
                    item.getUnit() != null ? item.getUnit() : "units",
                    threshold
            );
            LowStockNotificationRequest notification =
                    new LowStockNotificationRequest(adminUsername, message, "LOW_STOCK_ALERT");

            notificationClient.sendNotification(notification);
            log.warn("LOW_STOCK_ALERT sent to admin '{}': item='{}' qty={} threshold={}",
                    adminUsername, item.getItemName(), currentQty, threshold);

        } catch (Exception ex) {
            // Never block the stock adjustment if notification fails
            log.error("Failed to send low-stock alert for item='{}': {}",
                    item.getItemName(), ex.getMessage());
        }
    }

    private InventoryItemResponse toResponse(InventoryItem i) {
        InventoryItemResponse r = new InventoryItemResponse();
        r.setId(i.getId());
        r.setItemName(i.getItemName());
        r.setQuantity(i.getQuantity());
        r.setUnit(i.getUnit());
        r.setDescription(i.getDescription());
        r.setLowStockThreshold(i.getLowStockThreshold());
        r.setLowStock(i.getQuantity() <= i.getLowStockThreshold());
        return r;
    }
}