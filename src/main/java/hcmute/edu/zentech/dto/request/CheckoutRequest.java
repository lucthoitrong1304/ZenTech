package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CheckoutRequest {
    @NotNull(message = "addressId is required")
    private UUID addressId;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @NotEmpty(message = "items is required")
    @Valid
    private List<CheckoutItemRequest> items;

    private UUID customerVoucherId;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CheckoutItemRequest {
        @NotNull(message = "productVariantId is required")
        private UUID productVariantId;

        @Positive(message = "quantity must be greater than 0")
        private int quantity;
    }
}
