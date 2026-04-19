package com.medlab.billing.repository;

import com.medlab.billing.model.Invoice;
import com.medlab.billing.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByOrderId(Long orderId);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByPatientId(Long patientId);

    List<Invoice> findByStatus(InvoiceStatus status);
}