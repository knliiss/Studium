package dev.knalis.testing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TestingRestClientConfig {
    
    @Bean
    public RestClient educationServiceRestClient(
            TestingEducationServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }

    @Bean
    public RestClient notificationServiceRestClient(
            TestingNotificationServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }

    @Bean
    public RestClient auditServiceRestClient(
            TestingAuditServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }
    
    private ClientHttpRequestFactory clientHttpRequestFactory(TestingEducationServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(TestingNotificationServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(TestingAuditServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}
