package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.webrtc.WebRtcSignalMessage;
import hcmute.edu.zentech.model.CallState;
import hcmute.edu.zentech.service.CallStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebRtcSignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final CallStateService callStateService;

    @MessageMapping("/webrtc.signal")
    public void processSignaling(@Payload WebRtcSignalMessage message, Principal principal) {
        String senderEmail = principal.getName();
        String targetEmail = message.getTargetEmail();

        log.debug("Received WebRTC message type: {} from {} to {}", message.getType(), senderEmail, targetEmail);

        // Populate sender email to ensure it's authentic
        message.setSenderEmail(senderEmail);

        switch (message.getType()) {
            case CALL_REQUEST:
                if (callStateService.isUserAvailable(targetEmail) || senderEmail.equals(callStateService.getPartner(targetEmail))) {
                    callStateService.linkUsers(senderEmail, targetEmail);
                    callStateService.setUserState(senderEmail, CallState.RINGING);
                    if (callStateService.isUserAvailable(targetEmail)) {
                        callStateService.setUserState(targetEmail, CallState.RINGING);
                    }
                    messagingTemplate.convertAndSendToUser(targetEmail, "/queue/webrtc", message);
                } else {
                    // Target is busy
                    WebRtcSignalMessage busyMessage = WebRtcSignalMessage.builder()
                            .type(WebRtcSignalMessage.MessageType.BUSY)
                            .senderEmail(targetEmail)
                            .targetEmail(senderEmail)
                            .build();
                    messagingTemplate.convertAndSendToUser(senderEmail, "/queue/webrtc", busyMessage);
                }
                break;

            case CALL_ACCEPTED:
                callStateService.setUserState(senderEmail, CallState.IN_CALL);
                callStateService.setUserState(targetEmail, CallState.IN_CALL);
                messagingTemplate.convertAndSendToUser(targetEmail, "/queue/webrtc", message);
                break;

            case CALL_REJECTED:
            case HANG_UP:
                callStateService.unlinkUser(senderEmail);
                callStateService.setUserState(senderEmail, CallState.IDLE);
                callStateService.setUserState(targetEmail, CallState.IDLE);
                messagingTemplate.convertAndSendToUser(targetEmail, "/queue/webrtc", message);
                break;

            case OFFER:
            case ANSWER:
            case ICE_CANDIDATE:
                // Relay media signaling messages
                messagingTemplate.convertAndSendToUser(targetEmail, "/queue/webrtc", message);
                break;
                
            default:
                log.warn("Unknown WebRTC message type: {}", message.getType());
        }
    }
}
