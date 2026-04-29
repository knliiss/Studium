package dev.knalis.shared.web.autoconfigure;

import dev.knalis.shared.web.exception.GlobalExceptionHandler;
import dev.knalis.shared.web.request.RequestCorrelationFilter;
import dev.knalis.shared.web.request.RequestLoggingFilter;
import dev.knalis.shared.web.request.RestClientRequestIdInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestInterceptor;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SharedWebAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(FilterRegistrationBean.class)
    public RequestCorrelationFilter requestCorrelationFilter() {
        return new RequestCorrelationFilter();
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "requestCorrelationFilterRegistration")
    @ConditionalOnClass(FilterRegistrationBean.class)
    public FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilterRegistration(
            RequestCorrelationFilter requestCorrelationFilter
    ) {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestCorrelationFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(FilterRegistrationBean.class)
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }
    
    @Bean
    @ConditionalOnMissingBean(name = "requestLoggingFilterRegistration")
    @ConditionalOnClass(FilterRegistrationBean.class)
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
            RequestLoggingFilter requestLoggingFilter
    ) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestLoggingFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ClientHttpRequestInterceptor restClientRequestIdInterceptor() {
        return new RestClientRequestIdInterceptor();
    }
}
