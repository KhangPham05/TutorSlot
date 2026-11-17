package com.tutorslot.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

// No Spring context and no Gemini: the splitting is plain string work, and the disabled path is
// checked against a mock EmbeddingModel that must never be touched.
class PolicySearchServiceTest {

    @Test
    void splitsTheKnowledgeFileIntoOnePassagePerSection() throws IOException {
        String markdown = new ClassPathResource("knowledge/tutorslot-policies.md")
                .getContentAsString(StandardCharsets.UTF_8);

        List<Document> sections = PolicySearchService.splitIntoSections(markdown);

        assertThat(sections).extracting(d -> d.getMetadata().get("section")).containsExactly(
                "Tutoring hours", "How booking works", "Cancellation policy", "Late arrival and no-show policy",
                "Subjects and tutors", "Session length", "Reminders", "How to get help");
        Document cancellation = sections.get(2);
        assertThat(cancellation.getText())
                .startsWith("## Cancellation policy")
                .contains("any time before the session starts");
    }

    @Test
    void searchReturnsNothingAndNeverCallsGeminiWhenAiIsDisabled() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        PolicySearchService service = new PolicySearchService(
                new AiAvailability(AiAvailability.PLACEHOLDER_KEY), embeddingModel);

        service.loadKnowledgeFile();

        assertThat(service.search("What is the cancellation policy?")).isEmpty();
        verifyNoInteractions(embeddingModel);
    }
}
