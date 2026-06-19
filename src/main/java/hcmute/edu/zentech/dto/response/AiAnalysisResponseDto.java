package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.IncidentSeverity;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponseDto {
    private UUID id;
    private UUID incidentId;
    private String summary;
    private String rootCause;
    private IncidentSeverity severitySuggestion;
    private String solutionSuggestion;
    private Double confidenceScore;
    private Instant createdAt;
}
