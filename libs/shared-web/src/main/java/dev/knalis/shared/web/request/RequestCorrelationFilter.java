package dev.knalis.shared.web.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class RequestCorrelationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(RequestCorrelationContext.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        
        request.setAttribute(RequestCorrelationContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestCorrelationContext.REQUEST_ID_HEADER, requestId);
        RequestCorrelationContext.setCurrentRequestId(requestId);
        MDC.put(RequestCorrelationContext.REQUEST_ID_ATTRIBUTE, requestId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestCorrelationContext.REQUEST_ID_ATTRIBUTE);
            RequestCorrelationContext.clear();
        }
    }
}
