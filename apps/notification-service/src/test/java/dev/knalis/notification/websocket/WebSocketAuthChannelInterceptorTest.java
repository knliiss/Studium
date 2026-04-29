package dev.knalis.notification.websocket;

import dev.knalis.notification.exception.WebSocketAuthenticationException;
import dev.knalis.shared.security.jwt.ServletJwtAuthenticationConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {
    
    @Mock
    private JwtDecoder jwtDecoder;
    
    @Mock
    private ServletJwtAuthenticationConverter servletJwtAuthenticationConverter;
    
    private WebSocketAuthChannelInterceptor interceptor;
    
    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtDecoder, servletJwtAuthenticationConverter);
    }
    
    @Test
    void connectAuthenticatesBearerTokenFromNativeHeader() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", userId.toString())
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(servletJwtAuthenticationConverter.convert(jwt)).thenReturn(authentication);
        
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        
        Message<?> result = interceptor.preSend(message, null);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        
        assertNotNull(resultAccessor.getUser());
    }
    
    @Test
    void connectRejectsMissingToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        
        assertThrows(WebSocketAuthenticationException.class, () -> interceptor.preSend(message, null));
    }
}
