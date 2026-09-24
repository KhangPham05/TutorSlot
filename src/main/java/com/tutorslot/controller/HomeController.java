package com.tutorslot.controller;

import com.tutorslot.dto.ProviderSummaryDto;
import com.tutorslot.service.ProviderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final ProviderService providerService;

    public HomeController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<ProviderSummaryDto> providers = providerService.getProvidersWithSubjects();
        model.addAttribute("providers", providers);
        return "home";
    }
}
