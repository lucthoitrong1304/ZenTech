package hcmute.edu.zentech.dto.webrtc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebRtcSignalMessage {
    private MessageType type;
    private String senderEmail;
    private String targetEmail;
    
    // For SDP Offer/Answer
    private String sdp;
    
    // For ICE Candidate
    private Object candidate; // JSON node or String or specific DTO

    public enum MessageType {
        CALL_REQUEST,
        CALL_ACCEPTED,
        CALL_REJECTED,
        OFFER,
        ANSWER,
        ICE_CANDIDATE,
        HANG_UP,
        BUSY,
        PARTNER_LEFT
    }
}
