package com.tutorslot.controller.api;

import com.tutorslot.dto.BookingConfirmationDto;
import com.tutorslot.dto.BookingRequest;
import com.tutorslot.dto.MyAppointmentsDto;
import com.tutorslot.model.User;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.BookingFacade;
import com.tutorslot.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// A student's bookings, for the Next.js pages: the same service calls as BookingController and
// AppointmentController. /api/customer/** is CUSTOMER-only (see ApiSecurityConfig), and the
// student is always the one in the token, never an id from the request.
@RestController
@RequestMapping("/api/customer")
public class CustomerApiController {

    private final BookingFacade bookingFacade;
    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;

    public CustomerApiController(BookingFacade bookingFacade, AppointmentService appointmentService,
                                 CurrentUserService currentUserService) {
        this.bookingFacade = bookingFacade;
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
    }

    // Through BookingFacade like the Book button, so the row lock, the unique index, and the
    // after-commit notification all still apply. A taken slot becomes a 409 (ApiExceptionHandler).
    @PostMapping("/bookings")
    public ResponseEntity<BookingConfirmationDto> book(@Valid @RequestBody BookingRequest request,
                                                       Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        Long appointmentId = bookingFacade.book(request.slotId(), customer.userId(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.getConfirmation(appointmentId, customer.userId()));
    }

    @GetMapping("/appointments")
    public MyAppointmentsDto myAppointments(Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        return appointmentService.getMyAppointments(customer.userId());
    }

    // The confirmation page after a booking; someone else's appointment is a 403.
    @GetMapping("/appointments/{id}")
    public BookingConfirmationDto appointment(@PathVariable("id") Long appointmentId, Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        return appointmentService.getConfirmation(appointmentId, customer.userId());
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("id") Long appointmentId, Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        appointmentService.cancel(appointmentId, customer.userId());
        return ResponseEntity.noContent().build();
    }
}
