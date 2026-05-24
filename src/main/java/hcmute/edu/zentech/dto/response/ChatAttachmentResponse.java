package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ChatAttachmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentResponse {
    private UUID id;
    private String fileKey;
    private String fileName;
    private String contentType;
    private long fileSize;
    private ChatAttachmentType attachmentType;
    private int sortOrder;
    private String mediaUrl;
}
