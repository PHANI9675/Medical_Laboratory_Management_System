package com.lab_processing.lps.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.lab_processing.lps.exception.InvalidJobStateException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidJobStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJobState(
            InvalidJobStateException ex) {

        ApiErrorResponse response =
                new ApiErrorResponse("INVALID_JOB_STATE", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {

        ApiErrorResponse response =
                new ApiErrorResponse("RESOURCE_NOT_FOUND", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) throws Exception {
//
//        if (ex.getClass().getPackageName().startsWith("org.springdoc")) {
//            throw ex;
//        }
//        ApiErrorResponse response =
//                new ApiErrorResponse(
//                        "INTERNAL_ERROR",
//                        "Unexpected error occurred"
//                );
//        return ResponseEntity
//                .status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(response);
//    }

}