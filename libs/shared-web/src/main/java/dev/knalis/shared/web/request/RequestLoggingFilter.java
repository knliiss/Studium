package dev.knalis.shared.web.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = (String) request.getAttribute(RequestCorrelationContext.REQUEST_ID_ATTRIBUTE);
        
        try {
            filterChain.doFilter(request, response);
            log.info(
                    "Request completed method={} path={} status={} durationMs={} requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - startedAt,
                    requestId
            );
        } catch (Exception exception) {
            log.error(
                    "Request failed method={} path={} durationMs={} requestId={} message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    System.currentTimeMillis() - startedAt,
                    requestId,
                    exception.getMessage()
            );
            throw exception;
        }
    }
}
