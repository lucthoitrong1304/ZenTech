package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.CallState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class CallStateService {
    
    // Key: User Email, Value: Call State
    private final ConcurrentHashMap<String, CallState> userStates = new ConcurrentHashMap<>();

    public void setUserState(String email, CallState state) {
        if (state == CallState.IDLE) {
            userStates.remove(email);
        } else {
            userStates.put(email, state);
        }
    }

    public CallState getUserState(String email) {
        return userStates.getOrDefault(email, CallState.IDLE);
    }

    public boolean isUserAvailable(String email) {
        return getUserState(email) == CallState.IDLE;
    }

    @EventListener
    public void onDisconnectEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        if (user != null) {
            String email = user.getName();
            log.info("User {} disconnected, resetting call state to IDLE", email);
            setUserState(email, CallState.IDLE);
        }
    }
}
