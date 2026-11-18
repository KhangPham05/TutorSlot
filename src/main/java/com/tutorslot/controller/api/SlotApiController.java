package com.tutorslot.controller.api;

import com.tutorslot.dto.SlotDto;
import com.tutorslot.dto.SlotsPageDto;
import com.tutorslot.service.AvailabilitySlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// Browsing open slots, open to everyone like the web /slots page (see ApiSecurityConfig).
@RestController
@RequestMapping("/api/slots")
public class SlotApiController {

    private final AvailabilitySlotService availabilitySlotService;

    public SlotApiController(AvailabilitySlotService availabilitySlotService) {
        this.availabilitySlotService = availabilitySlotService;
    }

    // Same filters and paging as the web page; the response also carries the dropdown options.
    @GetMapping
    public SlotsPageDto slots(@RequestParam(required = false) Long providerId,
                              @RequestParam(required = false) Long serviceId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam(defaultValue = "1") int page) {
        return availabilitySlotService.getAvailableSlotsPage(providerId, serviceId, date, page);
    }

    @GetMapping("/{id}")
    public SlotDto slot(@PathVariable("id") Long slotId) {
        return availabilitySlotService.findSlotSummary(slotId);
    }
}
