package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantResponse {
    private UUID id;
    private ParticipantType userType;
    private UUID referenceId;
    private ParticipantStatus status;
    private Instant joinedAt;
    private Instant leftAt;
}
