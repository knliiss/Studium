package dev.knalis.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class GatewayClientConfig {
    
    @Bean
    public WebClient educationServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayEducationServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }
    
    @Bean
    public WebClient scheduleServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayScheduleServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public WebClient assignmentServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayAssignmentServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public WebClient testingServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayTestingServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public WebClient authServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayAuthServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public WebClient analyticsServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayAnalyticsServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public WebClient notificationServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayNotificationServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    public WebClient auditServiceWebClient(
            WebClient.Builder webClientBuilder,
            GatewayAuditServiceProperties properties
    ) {
        return webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .clientConnector(clientConnector(properties.getConnectTimeout().toMillis(), properties.getReadTimeout()))
                .build();
    }
    
    private ReactorClientHttpConnector clientConnector(long connectTimeoutMillis, Duration readTimeout) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeoutMillis))
                .responseTimeout(readTimeout);
        
        return new ReactorClientHttpConnector(httpClient);
    }
}
