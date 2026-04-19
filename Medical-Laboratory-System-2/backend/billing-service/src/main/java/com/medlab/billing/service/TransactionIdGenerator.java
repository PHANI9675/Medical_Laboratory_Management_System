package com.medlab.billing.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TransactionIdGenerator {

    private final AtomicLong counter = new AtomicLong(1);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String next() {
        String date = LocalDate.now().format(FMT);
        return String.format("TXN-%s-%04d", date, counter.getAndIncrement());
    }
}