package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
public class OrderCreateRequest {
    @NotNull(message = "customerId is required")
    private UUID customerId;

    @NotNull(message = "addressId is required")
    private UUID addressId;

    @NotEmpty(message = "items is required")
    @Valid
    private List<OrderCreateItemRequest> items;

    @DecimalMin(value = "0.0", message = "shippingFee must be greater than or equal to 0")
    private double shippingFee;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderCreateItemRequest {
        @NotNull(message = "productVariantId is required")
        private UUID productVariantId;

        @Positive(message = "quantity must be greater than 0")
        private int quantity;
    }
}
