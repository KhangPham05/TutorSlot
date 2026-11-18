package com.tutorslot.controller;

import com.tutorslot.ai.AssistantService;
import com.tutorslot.dto.ChatLineDto;
import com.tutorslot.model.User;
import com.tutorslot.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Controller
@RequestMapping("/customer/assistant")
public class AssistantController {

    private static final String TRANSCRIPT = "assistantTranscript";

    private final AssistantService assistantService;
    private final CurrentUserService currentUserService;

    // The turn counter behind the confirm rule (see AssistantTools.confirmPendingAction): +1 for
    // every message a student sends. Kept per user, not per HTTP session, and never reset, so it
    // only ever goes up -- even after "New conversation" or logging in again.
    private final Map<Long, AtomicLong> turnsByUserId = new ConcurrentHashMap<>();

    public AssistantController(AssistantService assistantService, CurrentUserService currentUserService) {
        this.assistantService = assistantService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String page(HttpSession session, Model model) {
        model.addAttribute("assistantEnabled", assistantService.isEnabled());
        model.addAttribute("transcript", transcript(session));
        return "customer/assistant";
    }

    @PostMapping
    public String send(@RequestParam(name = "message", defaultValue = "") String message,
                       Authentication authentication, HttpSession session) {
        if (StringUtils.hasText(message)) {
            // The customer id comes from the login session, never from the form or the AI.
            User customer = currentUserService.requireByEmail(authentication.getName());
            long turn = turnsByUserId.computeIfAbsent(customer.userId(), id -> new AtomicLong()).incrementAndGet();
            String reply = assistantService.chat(customer.userId(), turn, message.trim());

            List<ChatLineDto> transcript = transcript(session);
            transcript.add(new ChatLineDto("You", message.trim()));
            transcript.add(new ChatLineDto("Assistant", reply));
            session.setAttribute(TRANSCRIPT, transcript);
        }
        // Post/Redirect/Get, so refreshing the page never sends the same message twice.
        return "redirect:/customer/assistant";
    }

    @PostMapping("/reset")
    public String newConversation(Authentication authentication, HttpSession session) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        assistantService.reset(customer.userId());
        session.removeAttribute(TRANSCRIPT);
        return "redirect:/customer/assistant";
    }

    @SuppressWarnings("unchecked")
    private static List<ChatLineDto> transcript(HttpSession session) {
        Object saved = session.getAttribute(TRANSCRIPT);
        return saved == null ? new ArrayList<>() : (List<ChatLineDto>) saved;
    }
}
