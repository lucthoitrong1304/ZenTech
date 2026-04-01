package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewItemResponse {
    private UUID reviewId;
    private Integer rating;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID customerId;
    private String customerName;
    private Boolean isOwner;
}
