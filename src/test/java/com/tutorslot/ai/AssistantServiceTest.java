package com.tutorslot.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A real ChatClient around a mocked ChatModel: the whole Spring AI request is built, but nothing
// ever leaves the JVM, so no test calls Gemini.
@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final Long CUSTOMER_ID = 5L;

    @Mock
    private ChatModel chatModel;

    private final PendingActionStore pendingActionStore = new PendingActionStore();
    private final ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

    private AssistantService service(boolean aiEnabled) {
        // Gemini's real chat model has tool-calling options by default; without them the
        // ChatClient would quietly drop the tools and the tool context.
        lenient().when(chatModel.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        AiAvailability availability = new AiAvailability(aiEnabled ? "test-key" : AiAvailability.PLACEHOLDER_KEY);
        AssistantTools tools = mock(AssistantTools.class);
        return new AssistantService(availability, tools, pendingActionStore, chatMemory, ChatClient.builder(chatModel));
    }

    @Test
    void withNoKeyThePageSaysNotConfiguredAndGeminiIsNeverCalled() {
        String reply = service(false).chat(CUSTOMER_ID, 1, "Hi");

        assertThat(reply).isEqualTo("The assistant is not configured.");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void aMessageOverTheLimitIsRejectedWithoutCallingGemini() {
        String reply = service(true).chat(CUSTOMER_ID, 1, "x".repeat(1001));

        assertThat(reply).isEqualTo(AssistantService.TOO_LONG);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void aFailedGeminiCallBecomesAFriendlyMessageNotAnException() {
        AssistantService service = service(true);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("503 high demand"));

        String reply = service.chat(CUSTOMER_ID, 1, "Hi");

        assertThat(reply).isEqualTo(AssistantService.TRY_AGAIN);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void eachRequestCarriesTheToolsAndTheCustomerIdAndTurnInTheToolContext() {
        AssistantService service = service(true);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Hi! How can I help?")))));

        String reply = service.chat(CUSTOMER_ID, 3, "Hi");

        assertThat(reply).isEqualTo("Hi! How can I help?");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getValue().getOptions();
        assertThat(options.getToolContext()).containsEntry("customerId", 5L).containsEntry("turn", 3L);
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .contains("proposeBooking", "confirmPendingAction", "searchPolicies");
        // Both sides of the exchange are remembered for the next turn.
        assertThat(chatMemory.get("5")).hasSize(2);
    }

    @Test
    void theSystemPromptMentionsAProposalOnlyOnTheMessageRightAfterIt() {
        AssistantService service = service(true);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("OK")))));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);

        service.chat(CUSTOMER_ID, 1, "Book slot 7");
        pendingActionStore.save(CUSTOMER_ID, new PendingAction(PendingAction.Type.BOOK, 7L, 1));
        service.chat(CUSTOMER_ID, 2, "no, never mind");
        service.chat(CUSTOMER_ID, 3, "yes, what else can you do?");

        verify(chatModel, times(3)).call(prompt.capture());
        List<String> systemPrompts = prompt.getAllValues().stream().map(p -> p.getSystemMessage().getText()).toList();
        assertThat(systemPrompts.get(0)).doesNotContain("waiting");
        assertThat(systemPrompts.get(1)).contains("A proposed booking is waiting for the student's answer");
        // The student already answered on turn 2, so a later "yes" is not nudged into confirming.
        assertThat(systemPrompts.get(2)).doesNotContain("waiting");
    }

    @Test
    void anAnswerThatArrivesInSeveralPiecesIsShownWhole() {
        AssistantService service = service(true);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("Your Calculus session has been successfully")),
                new Generation(new AssistantMessage(" cancelled.")))));

        String reply = service.chat(CUSTOMER_ID, 2, "yes");

        assertThat(reply).isEqualTo("Your Calculus session has been successfully cancelled.");
    }

    @Test
    void newConversationForgetsTheChatMemoryAndThePendingAction() {
        AssistantService service = service(true);
        chatMemory.add("5", new UserMessage("Book slot 7"));
        pendingActionStore.save(CUSTOMER_ID, new PendingAction(PendingAction.Type.BOOK, 7L, 1));

        service.reset(CUSTOMER_ID);

        assertThat(chatMemory.get("5")).isEmpty();
        assertThat(pendingActionStore.find(CUSTOMER_ID)).isEmpty();
    }
}
