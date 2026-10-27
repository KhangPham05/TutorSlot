package com.tutorslot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class HttpNotificationGateway implements NotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);

    private final RestClient restClient;
    private final String apiKey;
    private final int maxAttempts;

    public HttpNotificationGateway(RestClient notificationRestClient,
                                    @Value("${notification.api-key:dev-key}") String apiKey,
                                    @Value("${notification.max-attempts:3}") int maxAttempts) {
        this.restClient = notificationRestClient;
        this.apiKey = apiKey;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public NotificationResult send(NotificationRequest request) {
        String lastFailureReason = "unknown error";

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                MockResponseBody body = restClient.post()
                        .uri("/notifications")
                        .header("X-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(MockResponseBody.class);
                return NotificationResult.success(body != null ? body.status() : "UNKNOWN");
            } catch (HttpClientErrorException e) {
                // 4xx is our own bug or bad config (wrong API key, malformed request) -- retrying
                // the exact same request can't fix that, so don't.
                log.warn("Notification attempt {}/{} failed: key={} cause={} (client error, not retrying)",
                        attempt, maxAttempts, request.idempotencyKey(), e.getStatusCode());
                return NotificationResult.failure("Client error: " + e.getStatusCode());
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastFailureReason = e.getMessage();
                log.warn("Notification attempt {}/{} failed: key={} cause={}",
                        attempt, maxAttempts, request.idempotencyKey(), lastFailureReason);
                if (attempt < maxAttempts) {
                    sleep(backoffMillis(attempt));
                }
            }
        }

        return NotificationResult.failure(lastFailureReason);
    }

    // 200ms after the 1st failed attempt, 400ms after the 2nd -- no wait after the 3rd, since
    // there's no 4th attempt to make.
    private long backoffMillis(int justFailedAttempt) {
        return justFailedAttempt == 1 ? 200 : 400;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Just enough of the mock service's response to read back the status. A private, local shape
    // for parsing someone else's JSON -- not a shared class, same as any HTTP client would define
    // for consuming a third-party API's response.
    private record MockResponseBody(String status, Long notificationId) {
    }
}
