package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIOpsInsightResponse {
    private String id;
    private String type; // info, warning, success
    private String title;
    private String description;
    private String category;
    private Instant createdAt;
}
