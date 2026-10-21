package com.expensetracker.web;

import java.io.UncheckedIOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to clean responses and keeps infrastructure details
 * (file ids, absolute paths, stack traces) out of the response body — those are
 * logged server-side only. Spring MVC's own exceptions (validation 400, unknown
 * routes, etc.) keep their default handling via the superclass.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Row-lookup misses surface as IllegalArgumentException — report as 404. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    /** Backing-store / workbook failures — log the cause, return a generic 500. */
    @ExceptionHandler({UncheckedIOException.class, IllegalStateException.class})
    public ProblemDetail handleServerError(RuntimeException ex) {
        log.error("Unhandled server error", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred.");
    }
}
