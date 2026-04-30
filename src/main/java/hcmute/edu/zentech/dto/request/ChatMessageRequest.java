package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatMessageRequest {
    @NotNull(message = "messageType is required")
    private ChatMessageType messageType;

    @NotBlank(message = "content is required")
    @Size(max = 5000, message = "content must not exceed 5000 characters")
    private String content;
}
