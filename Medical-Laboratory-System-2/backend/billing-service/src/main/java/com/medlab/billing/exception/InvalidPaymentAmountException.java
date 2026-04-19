package com.medlab.billing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the amount in a payment request does not match the invoice amount.
 * HTTP 400 Bad Request — the client sent incorrect data.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPaymentAmountException extends RuntimeException {

    public InvalidPaymentAmountException(String message) {
        super(message);
    }
}
