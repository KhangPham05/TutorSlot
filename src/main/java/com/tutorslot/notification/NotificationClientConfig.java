package com.tutorslot.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

// Builds the one RestClient HttpNotificationGateway talks through, with timeouts applied.
// Kept separate from the gateway itself so a test can build its own RestClient directly from a
// MockRestServiceServer-bound builder without anything here able to overwrite that binding.
@Configuration
public class NotificationClientConfig {

    @Bean
    public RestClient notificationRestClient(RestClient.Builder builder,
                                              @Value("${notification.base-url}") String baseUrl,
                                              @Value("${notification.connect-timeout-ms:500}") long connectTimeoutMs,
                                              @Value("${notification.read-timeout-ms:1500}") long readTimeoutMs) {
        // Without these, a hung service would hold the calling thread (and, later, the async
        // notification thread pool) forever.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
