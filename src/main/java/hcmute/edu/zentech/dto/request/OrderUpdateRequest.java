package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class OrderUpdateRequest {
    private OrderStatus orderStatus;

    private PaymentStatus paymentStatus;

    @DecimalMin(value = "0.0", message = "shippingFee must be greater than or equal to 0")
    private Double shippingFee;

    private String customerName;

    private String shippingAddress;

    private List<OrderItemUpdateRequest> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class OrderItemUpdateRequest {
        private UUID orderItemId;
        private int quantity;
    }
}
