package dev.knalis.education.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class EducationRestClientConfig {

    @Bean
    public RestClient auditServiceRestClient(
            EducationAuditServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }

    @Bean
    public RestClient fileServiceRestClient(
            EducationFileServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
