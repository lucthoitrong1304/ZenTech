package hcmute.edu.zentech.config;

import hcmute.edu.zentech.security.CustomUserDetailsService;
import hcmute.edu.zentech.security.jwt.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class WebSocketConfigAuthorizationTest {

    private final WebSocketConfig config = new WebSocketConfig(
            mock(JwtUtils.class),
            mock(CustomUserDetailsService.class)
    );

    @Test
    void adminCanSubscribeToAdminTopic() {
        StompHeaderAccessor accessor = subscription("/topic/admin.logs", "ROLE_ADMIN");

        assertDoesNotThrow(() -> config.authorizeSubscription(accessor));
    }

    @Test
    void nonAdminCannotSubscribeToAdminTopic() {
        StompHeaderAccessor accessor = subscription("/topic/admin.logs", "ROLE_CUSTOMER");

        assertThrows(AccessDeniedException.class, () -> config.authorizeSubscription(accessor));
    }

    @Test
    void authenticatedUserCanSubscribeToNonAdminTopic() {
        StompHeaderAccessor accessor = subscription("/topic/chat.messages", "ROLE_CUSTOMER");

        assertDoesNotThrow(() -> config.authorizeSubscription(accessor));
    }

    private StompHeaderAccessor subscription(String destination, String authority) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority(authority))
        ));
        return accessor;
    }
}
