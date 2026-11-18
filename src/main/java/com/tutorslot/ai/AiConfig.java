package com.tutorslot.ai;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// proxyBeanMethods = false: the one @Bean below never calls another, so no proxy is needed.
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    // Replaces Spring AI's default Gemini client (same Developer API mode: api-key only) to bound
    // how long one Gemini call can hold a request thread. The SDK's defaults are no timeout at
    // all, plus up to 5 attempts with 1+2+4+8 seconds of backoff -- and those backoff sleeps keep
    // going even after a timeout. So both are set: a timeout on the call, and few attempts.
    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                                    @Value("${gemini.timeout-ms}") int timeoutMs,
                                    @Value("${gemini.max-attempts}") int maxAttempts) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(timeoutMs)
                        .retryOptions(HttpRetryOptions.builder().attempts(maxAttempts).build())
                        .build())
                .build();
    }
}
