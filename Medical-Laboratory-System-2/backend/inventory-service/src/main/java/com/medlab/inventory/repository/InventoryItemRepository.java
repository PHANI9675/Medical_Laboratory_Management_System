package com.medlab.inventory.repository;

import com.medlab.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByQuantityLessThanEqual(Integer threshold);
}