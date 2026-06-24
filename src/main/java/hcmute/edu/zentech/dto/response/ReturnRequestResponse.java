package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.ReturnRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ReturnRequestResponse {
    private UUID id;
    private UUID orderId;
    private String customerName;
    private String reason;
    private String details;
    private String proofFileKeys;
    private java.util.List<String> proofFileUrls;
    private ReturnRequestStatus status;
    private boolean resellable;
    private Instant createdAt;
}
