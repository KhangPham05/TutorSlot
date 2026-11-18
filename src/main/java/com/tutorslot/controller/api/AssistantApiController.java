package com.tutorslot.controller.api;

import com.tutorslot.ai.AssistantService;
import com.tutorslot.ai.AssistantTurns;
import com.tutorslot.dto.AssistantMessageRequest;
import com.tutorslot.dto.AssistantReplyDto;
import com.tutorslot.dto.AssistantStatusDto;
import com.tutorslot.model.User;
import com.tutorslot.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The chat assistant for the Next.js page. Same AssistantService and the same shared turn counter
// as the web page, so the confirm-before-booking rule works the same. The API keeps no
// transcript: the page holds it, and the chat memory stays on the server as before.
@RestController
@RequestMapping("/api/customer/assistant")
public class AssistantApiController {

    private final AssistantService assistantService;
    private final AssistantTurns assistantTurns;
    private final CurrentUserService currentUserService;

    public AssistantApiController(AssistantService assistantService, AssistantTurns assistantTurns,
                                  CurrentUserService currentUserService) {
        this.assistantService = assistantService;
        this.assistantTurns = assistantTurns;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public AssistantStatusDto status() {
        return new AssistantStatusDto(assistantService.isEnabled());
    }

    // The customer id comes from the token, never from the message or the AI.
    @PostMapping
    public AssistantReplyDto send(@Valid @RequestBody AssistantMessageRequest request, Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        long turn = assistantTurns.next(customer.userId());
        return new AssistantReplyDto(assistantService.chat(customer.userId(), turn, request.message().trim()));
    }

    // "New conversation": clears the chat memory and any proposal waiting for a yes.
    @PostMapping("/reset")
    public ResponseEntity<Void> reset(Authentication authentication) {
        User customer = currentUserService.requireByEmail(authentication.getName());
        assistantService.reset(customer.userId());
        return ResponseEntity.noContent().build();
    }
}
