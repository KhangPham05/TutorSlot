package com.tutorslot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Retrieval for grounded policy answers (RAG). The knowledge file is split into one passage per
// "## " section, each passage is turned into an embedding (a list of numbers capturing its
// meaning) by Gemini, and kept in an in-memory SimpleVectorStore -- no database, no extra
// container. A question is embedded the same way, and the passages whose meaning is closest come
// back. The assistant then answers only from those passages instead of from its own memory.
@Service
public class PolicySearchService {

    private static final Logger log = LoggerFactory.getLogger(PolicySearchService.class);
    private static final String KNOWLEDGE_FILE = "knowledge/tutorslot-policies.md";
    private static final int TOP_K = 3;

    private final AiAvailability aiAvailability;
    private final VectorStore vectorStore;

    // Only true once the file is actually embedded. Until then search() returns nothing without
    // touching Gemini -- every similarity search embeds the question, which would otherwise send
    // the placeholder key to Google when AI is disabled.
    private volatile boolean loaded = false;

    public PolicySearchService(AiAvailability aiAvailability, EmbeddingModel embeddingModel) {
        this.aiAvailability = aiAvailability;
        // Rebuilt from the knowledge file on every startup, so there is nothing to persist.
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    // Runs once the app has fully started. With no real key (tests, CI) this does nothing, so
    // no test ever calls Gemini. A failure here (network, quota) never stops the app from
    // starting; policy search just stays empty.
    @EventListener(ApplicationReadyEvent.class)
    public void loadKnowledgeFile() {
        if (!aiAvailability.isEnabled()) {
            log.info("Policy knowledge file not embedded: AI disabled");
            return;
        }
        try {
            List<Document> sections = splitIntoSections(readKnowledgeFile());
            vectorStore.add(sections);
            loaded = true;
            log.info("Policy knowledge file embedded: sections={}", sections.size());
        } catch (Exception e) {
            log.warn("Policy knowledge file could not be embedded, policy search disabled: {}", e.getMessage());
        }
    }

    // The top 3 passages closest in meaning to the question, best match first. Never throws: an
    // empty list means "nothing to ground an answer on", and the assistant says it doesn't know.
    public List<String> search(String question) {
        if (!loaded || !StringUtils.hasText(question)) {
            return List.of();
        }
        try {
            return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(TOP_K).build())
                    .stream()
                    .map(Document::getText)
                    .toList();
        } catch (Exception e) {
            log.warn("Policy search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String readKnowledgeFile() throws IOException {
        return new ClassPathResource(KNOWLEDGE_FILE).getContentAsString(StandardCharsets.UTF_8);
    }

    // One Document per "## " section, heading included, so each passage still makes sense on its
    // own when the assistant reads it. The "# " title line above the first section is skipped.
    static List<Document> splitIntoSections(String markdown) {
        List<Document> sections = new ArrayList<>();
        String title = null;
        StringBuilder body = new StringBuilder();

        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                addSection(sections, title, body);
                title = line.substring(3).trim();
                body = new StringBuilder(line).append('\n');
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        addSection(sections, title, body);
        return sections;
    }

    private static void addSection(List<Document> sections, String title, StringBuilder body) {
        if (title != null) {
            sections.add(new Document(body.toString().trim(), Map.of("section", title)));
        }
    }
}
