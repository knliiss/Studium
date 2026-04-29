package dev.knalis.notification.websocket;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class WebSocketTokenHandshakeInterceptor implements HandshakeInterceptor {
    
    public static final String ACCESS_TOKEN_ATTRIBUTE = "notificationAccessToken";
    
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            String token = resolveToken(
                    httpServletRequest.getHeader("Authorization"),
                    httpServletRequest.getParameter("access_token")
            );
            if (token != null && !token.isBlank()) {
                attributes.put(ACCESS_TOKEN_ATTRIBUTE, token);
            }
        }
        return true;
    }
    
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
    
    private String resolveToken(String authorizationHeader, String accessToken) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7).trim();
        }
        return accessToken;
    }
}
