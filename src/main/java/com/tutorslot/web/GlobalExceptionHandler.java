package com.tutorslot.web;

import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// One place that turns an exception into a status code and an error template -- see
// templates/error/ for the pages themselves.
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return "error/404";
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public String handleForbidden(HttpServletResponse response, HttpServletRequest request) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        String user = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "anonymous";
        log.warn("Access denied: user={} path={}", user, request.getRequestURI());
        return "error/403";
    }

    @ExceptionHandler(SlotConflictException.class)
    public String handleConflict(HttpServletResponse response) {
        response.setStatus(HttpStatus.CONFLICT.value());
        return "error/409";
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public String handleBadInput(HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return "error/400";
    }

    // The exception is only ever used here, server side, for the log line -- the client still
    // gets the generic error/500 template with no message or stack trace (server.error.include-*
    // is also off, as a second layer of the same rule).
    @ExceptionHandler(Exception.class)
    public String handleOther(HttpServletResponse response, HttpServletRequest request, Exception ex) {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return "error/500";
    }
}
