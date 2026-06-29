package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.ChatMessageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class ChatMessageRequest {
    @NotNull(message = "messageType is required")
    private ChatMessageType messageType;

    @Size(max = 5000, message = "content must not exceed 5000 characters")
    private String content;

    @Size(max = 10, message = "attachments must not exceed 10 files")
    private List<@NotNull(message = "attachment is required") @Valid ChatAttachmentRequest> attachments;

    private Map<String, Object> pageContext;
}
