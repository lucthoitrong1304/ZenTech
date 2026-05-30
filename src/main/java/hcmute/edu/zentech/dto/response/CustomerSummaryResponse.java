package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSummaryResponse {
    private UUID customerId;
    private String fullName;
    private String email;
    private boolean active;
    private Instant registeredAt;
    private long totalOrders;
    private double totalSpent;
    private Instant lastOrderAt;
    private String imageUrl;
}
