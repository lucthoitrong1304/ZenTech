package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class IssueVoucherRequest {
    @NotNull(message = "couponId is required")
    private UUID couponId;

    private UUID customerId; // Null means issue to all customers
}
