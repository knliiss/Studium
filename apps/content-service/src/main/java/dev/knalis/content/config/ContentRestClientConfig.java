package dev.knalis.content.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class ContentRestClientConfig {

    @Bean
    public RestClient educationServiceRestClient(
            RestTemplateBuilder restTemplateBuilder,
            ContentEducationServiceProperties properties
    ) {
        return RestClient.builder(restTemplateBuilder
                        .setConnectTimeout(properties.connectTimeout())
                        .setReadTimeout(properties.readTimeout())
                        .build())
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

