package com.medlab.inventory;

import com.medlab.inventory.dto.AdjustInventoryRequest;
import com.medlab.inventory.dto.InventoryItemResponse;
import com.medlab.inventory.entity.InventoryItem;
import com.medlab.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository repo;

    @InjectMocks
    private InventoryService service;

    private InventoryItem sampleItem() {
        InventoryItem i = new InventoryItem();
        i.setId(1L);
        i.setItemName("CBC Reagent Kit");
        i.setQuantity(100);
        i.setUnit("kits");
        i.setLowStockThreshold(10);
        return i;
    }

    // ── Test 1: adjust stock upward ───────────────────────────────────────────
    @Test
    void adjustStock_addStock_shouldIncreaseQuantity() {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setItemId(1L);
        req.setQuantityChange(50);

        InventoryItem item = sampleItem(); // quantity = 100
        when(repo.findById(1L)).thenReturn(Optional.of(item));
        when(repo.save(any())).thenReturn(item);

        InventoryItemResponse result = service.adjustStock(req);

        assertEquals(150, result.getQuantity()); // 100 + 50
        assertFalse(result.isLowStock());
    }

    // ── Test 2: adjust stock downward ─────────────────────────────────────────
    @Test
    void adjustStock_reduceStock_shouldDecreaseQuantity() {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setItemId(1L);
        req.setQuantityChange(-30);

        InventoryItem item = sampleItem(); // quantity = 100
        when(repo.findById(1L)).thenReturn(Optional.of(item));
        when(repo.save(any())).thenReturn(item);

        InventoryItemResponse result = service.adjustStock(req);
        assertEquals(70, result.getQuantity()); // 100 - 30
        // assertEquals(60, result.getQuantity()); // fail test case

        assertFalse(result.isLowStock());
    }

    // ── Test 3: stock goes below threshold → lowStock true ───────────────────
    @Test
    void adjustStock_whenQuantityDropsBelowThreshold_shouldFlagLowStock() {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setItemId(1L);
        req.setQuantityChange(-95);

        InventoryItem item = sampleItem(); // quantity=100, threshold=10
        when(repo.findById(1L)).thenReturn(Optional.of(item));
        when(repo.save(any())).thenReturn(item);

        InventoryItemResponse result = service.adjustStock(req);

        assertEquals(5, result.getQuantity()); // 100 - 95
        assertTrue(result.isLowStock());        // 5 <= 10 → true
    }

    // ── Test 4: reduce more than available → exception ───────────────────────
    @Test
    void adjustStock_whenInsufficientStock_shouldThrowException() {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setItemId(1L);
        req.setQuantityChange(-200); // only 100 available

        when(repo.findById(1L)).thenReturn(Optional.of(sampleItem()));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adjustStock(req));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
        verify(repo, never()).save(any()); // confirm nothing was saved
    }

    // ── Test 5: item not found → exception ───────────────────────────────────
    @Test
    void adjustStock_whenItemNotFound_shouldThrowException() {
        AdjustInventoryRequest req = new AdjustInventoryRequest();
        req.setItemId(99L);
        req.setQuantityChange(10);

        when(repo.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.adjustStock(req));

        assertEquals("Inventory item not found with id: 99", ex.getMessage());
    }
}