package com.tutorslot.web;

import com.tutorslot.exception.ForbiddenException;
import com.tutorslot.exception.NotFoundException;
import com.tutorslot.exception.SlotConflictException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// One place that turns an exception into a status code and an error template -- see
// templates/error/ for the pages themselves.
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        return "error/404";
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public String handleForbidden(HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
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

    @ExceptionHandler(Exception.class)
    public String handleOther(HttpServletResponse response) {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return "error/500";
    }
}
