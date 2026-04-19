package com.medlab.order_service.repository;

import com.medlab.order_service.entity.OrderTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTestRepository extends JpaRepository<OrderTest, Long> {
}