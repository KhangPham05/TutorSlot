package com.tutorslot.web;

import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

// The JSON twin of GlobalExceptionHandler, for the /api controllers only: the same status codes,
// but a small {"error": "..."} body instead of an HTML page. It runs before GlobalExceptionHandler
// (highest order) and only for controller.api, so the web pages are unaffected. Like the web
// pages, the client never sees a stack trace or an internal message.
@RestControllerAdvice(basePackages = "com.tutorslot.controller.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleLoginFailed() {
        // No email in the log: people sometimes type their password into the email field.
        log.warn("API login failed");
        return error(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadInput() {
        return error(HttpStatus.BAD_REQUEST, "Invalid request.");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound() {
        return error(HttpStatus.NOT_FOUND, "Not found.");
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, String>> handleForbidden(HttpServletRequest request) {
        log.warn("Access denied: user={} path={}",
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                request.getRequestURI());
        return error(HttpStatus.FORBIDDEN, "You can't do that.");
    }

    // The booking rules' own messages ("This slot is already booked.") are written for students
    // and tutors, so they are passed on as they are.
    @ExceptionHandler(SlotConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(SlotConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(HttpServletRequest request, Exception ex) {
        log.error("Unhandled API error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
