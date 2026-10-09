package com.tutorslot.controller;

import com.tutorslot.dto.CreateSlotRequest;
import com.tutorslot.model.Provider;
import com.tutorslot.service.AppointmentService;
import com.tutorslot.service.CurrentUserService;
import com.tutorslot.service.ProviderDashboardService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/provider")
public class ProviderController {

    private final ProviderDashboardService providerDashboardService;
    private final AppointmentService appointmentService;
    private final CurrentUserService currentUserService;

    public ProviderController(ProviderDashboardService providerDashboardService,
                               AppointmentService appointmentService, CurrentUserService currentUserService) {
        this.providerDashboardService = providerDashboardService;
        this.appointmentService = appointmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        Provider provider = currentUserService.requireProviderByEmail(authentication.getName());
        model.addAttribute("dashboard", providerDashboardService.getDashboard(provider.providerId()));
        if (!model.containsAttribute("createSlotRequest")) {
            model.addAttribute("createSlotRequest", new CreateSlotRequest(null, null));
        }
        return "provider/dashboard";
    }

    @PostMapping("/slots")
    public String createSlot(@Valid @ModelAttribute("createSlotRequest") CreateSlotRequest request,
                              BindingResult bindingResult, Authentication authentication,
                              Model model, HttpServletResponse response) {
        Provider provider = currentUserService.requireProviderByEmail(authentication.getName());

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("dashboard", providerDashboardService.getDashboard(provider.providerId()));
            return "provider/dashboard";
        }

        providerDashboardService.createSlot(provider.providerId(), request.serviceId(), request.startDateTime());
        return "redirect:/provider/dashboard";
    }

    @PostMapping("/slots/{id}/delete")
    public String deleteSlot(@PathVariable("id") Long slotId, Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Provider provider = currentUserService.requireProviderByEmail(authentication.getName());
        providerDashboardService.removeSlot(slotId, provider.providerId());
        redirectAttributes.addFlashAttribute("message", "Slot removed.");
        return "redirect:/provider/dashboard";
    }

    @GetMapping("/appointments")
    public String appointments(Authentication authentication, Model model) {
        Provider provider = currentUserService.requireProviderByEmail(authentication.getName());
        model.addAttribute("myAppointments", appointmentService.getProviderAppointments(provider.providerId()));
        return "provider/appointments";
    }
}
