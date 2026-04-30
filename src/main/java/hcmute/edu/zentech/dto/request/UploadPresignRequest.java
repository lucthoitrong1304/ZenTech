package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UploadPresignRequest {
    @NotBlank(message = "originalFilename is required")
    private String originalFilename;

    @NotBlank(message = "contentType is required")
    private String contentType;

    @NotNull(message = "fileSize is required")
    @Positive(message = "fileSize must be greater than 0")
    private Long fileSize;

    @NotNull(message = "purpose is required")
    private UploadPurpose purpose;
}
