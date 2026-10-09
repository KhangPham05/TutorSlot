package com.tutorslot.controller;

import com.tutorslot.dto.SlotDto;
import com.tutorslot.model.User;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.AvailabilitySlotService;
import com.tutorslot.service.BookingFacade;
import com.tutorslot.service.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/customer")
public class BookingController {

    private final AvailabilitySlotService availabilitySlotService;
    private final BookingFacade bookingFacade;
    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;

    public BookingController(AvailabilitySlotService availabilitySlotService, BookingFacade bookingFacade,
                              AppointmentService appointmentService, CurrentUserService currentUserService) {
        this.availabilitySlotService = availabilitySlotService;
        this.bookingFacade = bookingFacade;
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/book")
    public String bookForm(@RequestParam Long slotId, Model model) {
        SlotDto slot = availabilitySlotService.findSlotSummary(slotId);
        model.addAttribute("slot", slot);
        return "customer/book";
    }

    @PostMapping("/book")
    public String submitBooking(@RequestParam Long slotId, @RequestParam(required = false) String notes,
                                 Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        Long appointmentId = bookingFacade.book(slotId, customer.userId(), notes);
        // Post/Redirect/Get: a page refresh after booking re-fetches the confirmation, it never
        // re-submits the booking.
        return "redirect:/customer/appointments/" + appointmentId + "/confirmation";
    }

    @GetMapping("/appointments/{id}/confirmation")
    public String confirmation(@PathVariable("id") Long appointmentId, Authentication authentication, Model model) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        model.addAttribute("confirmation", appointmentService.getConfirmation(appointmentId, customer.userId()));
        return "customer/confirmation";
    }
}
