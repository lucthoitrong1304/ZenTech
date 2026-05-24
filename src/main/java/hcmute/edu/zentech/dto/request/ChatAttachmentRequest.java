package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.ChatAttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatAttachmentRequest {
    @NotBlank(message = "fileKey is required")
    @Size(max = 500, message = "fileKey must not exceed 500 characters")
    private String fileKey;

    @NotBlank(message = "fileName is required")
    @Size(max = 255, message = "fileName must not exceed 255 characters")
    private String fileName;

    @NotBlank(message = "contentType is required")
    @Size(max = 100, message = "contentType must not exceed 100 characters")
    private String contentType;

    @NotNull(message = "fileSize is required")
    @Positive(message = "fileSize must be greater than 0")
    private Long fileSize;

    @NotNull(message = "attachmentType is required")
    private ChatAttachmentType attachmentType;
}
