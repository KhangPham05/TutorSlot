package com.tutorslot.controller;

import com.tutorslot.dto.SlotsPageDto;
import com.tutorslot.service.AvailabilitySlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class SlotController {

    private final AvailabilitySlotService availabilitySlotService;

    public SlotController(AvailabilitySlotService availabilitySlotService) {
        this.availabilitySlotService = availabilitySlotService;
    }

    @GetMapping("/slots")
    public String slots(@RequestParam(required = false) Long providerId,
                         @RequestParam(required = false) Long serviceId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        SlotsPageDto slotsPage = availabilitySlotService.getAvailableSlotsPage(providerId, serviceId, date, page);
        model.addAttribute("slotsPage", slotsPage);
        return "slots";
    }
}
