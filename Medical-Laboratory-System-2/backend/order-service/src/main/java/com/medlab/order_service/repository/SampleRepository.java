package com.medlab.order_service.repository;

import com.medlab.order_service.entity.Order;
import com.medlab.order_service.entity.Sample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    /**
     * Returns the Sample linked to the given Order.
     * Used by OrderService.getOrderDetailById() to include the sampleId
     * in the response so downstream services (Billing) can fetch LPS results.
     */
    Optional<Sample> findByOrder(Order order);
}