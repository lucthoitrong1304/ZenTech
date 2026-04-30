package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPresignResponse {
    private String presignedUrl;
    private String fileKey;
    private String method;
    private long expiresInMinutes;
    private Map<String, String> requiredHeaders;
}
