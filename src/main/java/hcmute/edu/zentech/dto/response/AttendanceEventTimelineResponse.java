package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceEventTimelineResponse {
    private String type;
    private LocalDateTime timestamp;
    private String source;
    private String faceImageUrl;
    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Boolean locationValid;
    private Double distanceMeters;
}
