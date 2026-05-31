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
import hcmute.edu.zentech.dto.webrtc.WebRtcSignalMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class CallStateService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    // Key: User Email, Value: Call State
    private final ConcurrentHashMap<String, CallState> userStates = new ConcurrentHashMap<>();
    
    // Key: User Email, Value: Partner Email
    private final ConcurrentHashMap<String, String> activeCallPartners = new ConcurrentHashMap<>();

    public void linkUsers(String userA, String userB) {
        activeCallPartners.put(userA, userB);
        activeCallPartners.put(userB, userA);
    }

    public void unlinkUser(String user) {
        String partner = activeCallPartners.remove(user);
        if (partner != null) {
            activeCallPartners.remove(partner);
        }
    }

    public String getPartner(String user) {
        return activeCallPartners.get(user);
    }

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
            
            String partner = getPartner(email);
            if (partner != null) {
                log.info("Notifying partner {} that {} has left", partner, email);
                WebRtcSignalMessage partnerLeftMessage = WebRtcSignalMessage.builder()
                        .type(WebRtcSignalMessage.MessageType.PARTNER_LEFT)
                        .senderEmail(email)
                        .targetEmail(partner)
                        .build();
                messagingTemplate.convertAndSendToUser(partner, "/queue/webrtc", partnerLeftMessage);
            }
        }
    }
}
