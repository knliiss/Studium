package dev.knalis.notification.websocket;

import dev.knalis.notification.exception.WebSocketAuthenticationException;
import dev.knalis.shared.security.jwt.ServletJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    
    private final JwtDecoder jwtDecoder;
    private final ServletJwtAuthenticationConverter servletJwtAuthenticationConverter;
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            JwtAuthenticationToken authentication = authenticate(accessor);
            accessor.setUser(authentication);
        } else if (requiresAuthenticatedUser(accessor.getCommand()) && accessor.getUser() == null) {
            throw new WebSocketAuthenticationException("WebSocket user is not authenticated");
        }
        
        return message;
    }
    
    private JwtAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null || token.isBlank()) {
            throw new WebSocketAuthenticationException("Missing bearer token for websocket connection");
        }
        
        Jwt jwt = jwtDecoder.decode(token);
        return servletJwtAuthenticationConverter.convert(jwt);
    }
    
    private String resolveToken(StompHeaderAccessor accessor) {
        List<String> authorizationValues = accessor.getNativeHeader("Authorization");
        if (authorizationValues != null && !authorizationValues.isEmpty()) {
            String value = authorizationValues.getFirst();
            if (value != null && value.startsWith("Bearer ")) {
                return value.substring(7).trim();
            }
        }
        
        List<String> accessTokenValues = accessor.getNativeHeader("access_token");
        if (accessTokenValues != null && !accessTokenValues.isEmpty()) {
            return accessTokenValues.getFirst();
        }
        
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object token = sessionAttributes.get(WebSocketTokenHandshakeInterceptor.ACCESS_TOKEN_ATTRIBUTE);
            if (token instanceof String tokenValue) {
                return tokenValue;
            }
        }
        
        return null;
    }
    
    private boolean requiresAuthenticatedUser(StompCommand command) {
        return StompCommand.SUBSCRIBE.equals(command)
                || StompCommand.SEND.equals(command)
                || StompCommand.UNSUBSCRIBE.equals(command);
    }
}
