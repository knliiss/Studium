package dev.knalis.assignment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AssignmentRestClientConfig {
    
    @Bean
    public RestClient fileServiceRestClient(
            AssignmentFileServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }
    
    @Bean
    public RestClient educationServiceRestClient(
            AssignmentEducationServiceProperties properties,
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
            AssignmentNotificationServiceProperties properties,
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
            AssignmentAuditServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }
    
    private ClientHttpRequestFactory clientHttpRequestFactory(AssignmentFileServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
    
    private ClientHttpRequestFactory clientHttpRequestFactory(AssignmentEducationServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(AssignmentNotificationServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory(AssignmentAuditServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}
