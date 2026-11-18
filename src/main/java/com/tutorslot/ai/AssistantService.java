package com.tutorslot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// One chat turn with Gemini: the system prompt, the tools, the tool context, and the chat memory.
// It never throws to the controller -- every failure becomes a short message for the page.
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    static final String NOT_CONFIGURED = "The assistant is not configured.";
    static final String TRY_AGAIN = "Sorry, the assistant is not available right now. Please try again in a minute.";
    static final int MAX_MESSAGE_LENGTH = 1000;
    static final String TOO_LONG = "Please keep your message under " + MAX_MESSAGE_LENGTH + " characters.";

    private static final DateTimeFormatter NOW = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy, h:mm a", Locale.US);

    private static final String SYSTEM_PROMPT = """
            You are TutorSlot's booking assistant for the logged-in student. Today is %s.
            - Use the tools for facts. Never invent slots, appointments, tutors, or policies.
            - To book or cancel: find the item, call proposeBooking or proposeCancellation, show the \
            student the details, and ask "Shall I confirm?". When the student says yes in their next \
            message, call confirmPendingAction. Never call it in the same message as the proposal.
            - If a slot is taken, say so, call findAvailableSlots, and offer the next open slots for the \
            same subject. If another tool returns FAILED, explain it simply.
            - If a request is unclear (which tutor, which day), ask one short question.
            - For questions about hours, policies, or rules, call searchPolicies and answer only from \
            what it returns. If it returns nothing relevant, say you don't know.
            - Reply in short plain text, without Markdown.
            """;

    private final AiAvailability aiAvailability;
    private final AssistantTools assistantTools;
    private final PendingActionStore pendingActionStore;
    private final ChatMemory chatMemory;
    private final ChatClient chatClient;

    public AssistantService(AiAvailability aiAvailability, AssistantTools assistantTools,
                            PendingActionStore pendingActionStore, ChatMemory chatMemory,
                            ChatClient.Builder chatClientBuilder) {
        this.aiAvailability = aiAvailability;
        this.assistantTools = assistantTools;
        this.pendingActionStore = pendingActionStore;
        this.chatMemory = chatMemory;
        // Spring AI's in-memory chat memory (a window of the last messages per conversation). The
        // advisor adds the earlier turns to each request, so "yes" knows what it is answering.
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public boolean isEnabled() {
        return aiAvailability.isEnabled();
    }

    public String chat(Long customerId, long turn, String message) {
        if (!aiAvailability.isEnabled()) {
            return NOT_CONFIGURED;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return TOO_LONG;
        }
        try {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt(customerId, turn))
                    .user(message)
                    .tools(assistantTools)
                    // Trusted values for the tools, straight from the login session. The model
                    // never sees them, so it can't change whose account a booking goes to.
                    .toolContext(AssistantTools.toolContext(customerId, turn))
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId(customerId)))
                    .call()
                    .chatResponse();
            String reply = GeminiText.of(response);
            return reply != null ? reply : TRY_AGAIN;
        } catch (RuntimeException e) {
            // Network, timeout, or quota trouble. Log the error type only: never the student's
            // message, and the page gets a friendly sentence instead of a stack trace.
            log.warn("Assistant call failed: user={} turn={} error={}", customerId, turn, GeminiText.describeError(e));
            return TRY_AGAIN;
        }
    }

    private String systemPrompt(Long customerId, long turn) {
        String prompt = SYSTEM_PROMPT.formatted(LocalDateTime.now().format(NOW));
        // The chat memory keeps only the text of earlier turns, not the tool calls, so the model
        // can't see that a proposal is still open. Without this line, a "yes" tends to re-propose
        // instead of confirming. Only on the message right after the proposal: once the student
        // has answered (even "no"), a later, unrelated "yes" must not be nudged into a booking.
        // The confirm rule itself is still enforced in AssistantTools.
        return pendingActionStore.find(customerId)
                .filter(pending -> pending.turn() == turn - 1)
                .map(pending -> prompt + "- A proposed "
                        + (pending.type() == PendingAction.Type.BOOK ? "booking" : "cancellation")
                        + " is waiting for the student's answer. If they say yes, call confirmPendingAction; "
                        + "do not propose it again.\n")
                .orElse(prompt);
    }

    // "New conversation": forget the chat memory and any proposal still waiting for a yes.
    public void reset(Long customerId) {
        chatMemory.clear(conversationId(customerId));
        pendingActionStore.clear(customerId);
    }

    private static String conversationId(Long customerId) {
        return String.valueOf(customerId);
    }
}
