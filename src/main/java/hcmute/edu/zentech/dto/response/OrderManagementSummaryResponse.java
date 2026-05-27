package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentMethod;
import hcmute.edu.zentech.model.PaymentStatus;
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
public class OrderManagementSummaryResponse {
    private UUID orderId;
    private Instant createdAt;
    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private double originalTotalPrice;
    private double discountAmount;
    private double shippingFee;
    private double finalPrice;
    private int itemCount;
    private OrderManagementCustomerResponse customer;
}
