package com.medlab.billing.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InvoiceNumberGenerator {

    private final AtomicLong counter = new AtomicLong(1);

    public String next() {
        int year = LocalDate.now().getYear();
        return String.format("INV-%d-%04d", year, counter.getAndIncrement());
    }
}