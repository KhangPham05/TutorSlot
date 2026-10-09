package com.tutorslot.controller;

import com.tutorslot.model.User;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;

    public AppointmentController(AppointmentService appointmentService, CurrentUserService currentUserService) {
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String myAppointments(Authentication authentication, Model model) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        model.addAttribute("myAppointments", appointmentService.getMyAppointments(customer.userId()));
        return "customer/appointments";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable("id") Long appointmentId, Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        appointmentService.cancel(appointmentId, customer.userId());
        redirectAttributes.addFlashAttribute("message", "Appointment cancelled.");
        return "redirect:/customer/appointments";
    }
}
