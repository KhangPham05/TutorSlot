package com.tutorslot.notification;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

// No Spring context -- just RestClient.Builder bound to a MockRestServiceServer, so these run as
// fast, plain unit tests even though they exercise real HTTP client code.
class HttpNotificationGatewayTest {

    private static final String BASE_URL = "http://fake-notify";
    private static final NotificationRequest REQUEST = new NotificationRequest(
            "BOOKED-1", "dan.student@sjsu.edu", NotificationType.BOOKING_CONFIRMED, "Your session is confirmed.", 1L);

    @Test
    void sendsApiKeyHeaderAndJsonBodyAndReturnsSuccessOn202() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpNotificationGateway gateway = new HttpNotificationGateway(builder.build(), "dev-key", 3, new SimpleMeterRegistry());

        server.expect(requestTo(BASE_URL + "/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "dev-key"))
                .andExpect(content().string(containsString("\"idempotencyKey\":\"BOOKED-1\"")))
                .andExpect(content().string(containsString("\"type\":\"BOOKING_CONFIRMED\"")))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ACCEPTED\",\"notificationId\":1}"));

        NotificationResult result = gateway.send(REQUEST);

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("ACCEPTED");
        server.verify();
    }

    @Test
    void retriesOn503AndEventuallySucceeds() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpNotificationGateway gateway = new HttpNotificationGateway(builder.build(), "dev-key", 3, new SimpleMeterRegistry());

        server.expect(requestTo(BASE_URL + "/notifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE_URL + "/notifications"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ACCEPTED\",\"notificationId\":2}"));

        NotificationResult result = gateway.send(REQUEST);

        assertThat(result.success()).isTrue();
        server.verify();
    }

    @Test
    void retriesOnTimeoutUpToMaxAttemptsThenReturnsFailureWithoutThrowing() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpNotificationGateway gateway = new HttpNotificationGateway(builder.build(), "dev-key", 3, new SimpleMeterRegistry());

        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(BASE_URL + "/notifications"))
                    .andRespond(request -> {
                        throw new IOException("Read timed out");
                    });
        }

        NotificationResult result = gateway.send(REQUEST);

        assertThat(result.success()).isFalse();
        server.verify();
    }

    @Test
    void doesNotRetryOn400() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpNotificationGateway gateway = new HttpNotificationGateway(builder.build(), "dev-key", 3, new SimpleMeterRegistry());

        // Only one expectation registered: if the gateway retried, the second call would find no
        // matching expectation and the test would fail right there.
        server.expect(requestTo(BASE_URL + "/notifications")).andRespond(withBadRequest());

        NotificationResult result = gateway.send(REQUEST);

        assertThat(result.success()).isFalse();
        server.verify();
    }

    @Test
    void doesNotRetryOn401() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpNotificationGateway gateway = new HttpNotificationGateway(builder.build(), "dev-key", 3, new SimpleMeterRegistry());

        server.expect(requestTo(BASE_URL + "/notifications")).andRespond(withUnauthorizedRequest());

        NotificationResult result = gateway.send(REQUEST);

        assertThat(result.success()).isFalse();
        server.verify();
    }
}
