package dev.knalis.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NotificationRestClientConfig {
    
    @Bean
    public RestClient educationServiceRestClient(
            NotificationEducationServiceProperties properties,
            ClientHttpRequestInterceptor restClientRequestIdInterceptor
    ) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(properties))
                .requestInterceptor(restClientRequestIdInterceptor)
                .build();
    }
    
    private ClientHttpRequestFactory clientHttpRequestFactory(NotificationEducationServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }
}
