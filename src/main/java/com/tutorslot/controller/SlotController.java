package com.tutorslot.controller;

import com.tutorslot.dto.SlotDto;
import com.tutorslot.service.AvailabilitySlotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class SlotController {

    private final AvailabilitySlotService availabilitySlotService;

    public SlotController(AvailabilitySlotService availabilitySlotService) {
        this.availabilitySlotService = availabilitySlotService;
    }

    @GetMapping("/slots")
    public String slots(Model model) {
        List<SlotDto> slots = availabilitySlotService.getAvailableSlots();
        model.addAttribute("slots", slots);
        return "slots";
    }
}
