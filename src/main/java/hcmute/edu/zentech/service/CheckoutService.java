package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CheckoutRequest;
import hcmute.edu.zentech.dto.response.CheckoutResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.Address;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.CouponType;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderCoupon;
import hcmute.edu.zentech.model.OrderDetail;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentGateway;
import hcmute.edu.zentech.model.PaymentMethod;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.model.InventoryTransaction;
import hcmute.edu.zentech.model.InventoryTransactionReason;
import hcmute.edu.zentech.model.InventoryTransactionType;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.CustomerVoucherRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import hcmute.edu.zentech.repository.InventoryTransactionRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import hcmute.edu.zentech.service.payment.PaymentGatewayCreateResult;
import hcmute.edu.zentech.service.payment.MomoGatewayClient;
import hcmute.edu.zentech.service.payment.VnpayGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private static final double DEFAULT_SHIPPING_FEE = 0D;

    private final CustomerRepository customerRepository;
    private final CustomerVoucherRepository customerVoucherRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final VnpayGatewayClient vnpayGatewayClient;
    private final MomoGatewayClient momoGatewayClient;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request, String clientIp) {
        Customer customer = getCurrentCustomerWithAddresses();
        Address address = getOwnedActiveAddress(customer, request.getAddressId());

        Order order = new Order();
        order.setCustomer(customer);
        order.setAddress(address);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setOrderStatus(OrderStatus.CREATED);
        order.setShippingFee(DEFAULT_SHIPPING_FEE);
        order.setDiscountAmount(0D);

        List<OrderDetail> orderDetails = request.getItems().stream()
                .map(item -> buildOrderDetail(order, item))
                .toList();
        double originalTotalPrice = orderDetails.stream()
                .mapToDouble(detail -> detail.getPriceAtPurchase() * detail.getQuantity())
                .sum();

        order.setOriginalTotalPrice(originalTotalPrice);
        applyVoucherIfPresent(order, customer.getId(), request.getCustomerVoucherId());
        order.setFinalPrice(Math.max(0D, originalTotalPrice - order.getDiscountAmount() + order.getShippingFee()));
        order.setOrderItems(new HashSet<>(orderDetails));

        Order savedOrder = orderRepository.save(order);

        // Log inventory transactions
        for (OrderDetail detail : orderDetails) {
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .productVariant(detail.getProductVariant())
                    .type(InventoryTransactionType.EXPORT)
                    .quantity(detail.getQuantity())
                    .reason(InventoryTransactionReason.CUSTOMER_ORDER)
                    .note("Khách hàng đặt mua đơn hàng #" + savedOrder.getId())
                    .createdBy(customer.getUserInfo() != null ? customer.getUserInfo().getId() : null)
                    .build();
            inventoryTransactionRepository.save(transaction);
        }

        String paymentUrl = createPaymentUrlIfNeeded(savedOrder, orderDetails, clientIp);

        return CheckoutResponse.builder()
                .orderId(savedOrder.getId())
                .paymentMethod(savedOrder.getPaymentMethod())
                .paymentStatus(savedOrder.getPaymentStatus())
                .orderStatus(savedOrder.getOrderStatus())
                .amount(Math.round(savedOrder.getFinalPrice()))
                .paymentUrl(paymentUrl)
                .build();
    }

    private String createPaymentUrlIfNeeded(Order order, List<OrderDetail> orderDetails, String clientIp) {
        if (order.getPaymentMethod() == PaymentMethod.CASH) {
            return null;
        }

        if (order.getPaymentMethod() == PaymentMethod.VNPAY) {
            PaymentGatewayCreateResult result = vnpayGatewayClient.createPayment(order, clientIp);
            paymentService.createPendingTransaction(
                    order,
                    PaymentGateway.VNPAY,
                    vnpayGatewayClient.requestIdFromOrderId(order.getId()),
                    Math.round(order.getFinalPrice()),
                    result.paymentUrl(),
                    result.rawPayload()
            );
            return result.paymentUrl();
        }

        if (order.getPaymentMethod() == PaymentMethod.MOMO) {
            PaymentGatewayCreateResult result = momoGatewayClient.createPayment(order, orderDetails);
            paymentService.createPendingTransaction(
                    order,
                    PaymentGateway.MOMO,
                    momoGatewayClient.requestIdFromOrderId(order.getId()),
                    Math.round(order.getFinalPrice()),
                    result.paymentUrl(),
                    result.rawPayload()
            );
            return result.paymentUrl();
        }

        throw new IllegalArgumentException("Unsupported payment method");
    }

    private void applyVoucherIfPresent(Order order, UUID customerId, UUID customerVoucherId) {
        if (customerVoucherId == null) {
            order.setOrderCoupons(new HashSet<>());
            return;
        }

        CustomerVoucher customerVoucher = customerVoucherRepository.findByIdAndCustomer_Id(customerVoucherId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerVoucher", "id", customerVoucherId));
        Coupon coupon = customerVoucher.getCoupon();
        Instant now = Instant.now();

        if (customerVoucher.getUsedAt() != null) {
            throw new IllegalArgumentException("Voucher has already been used");
        }
        if (coupon == null || !coupon.isActive()) {
            throw new IllegalArgumentException("Voucher is not active");
        }
        if (coupon.getStartAt() != null && coupon.getStartAt().isAfter(now)) {
            throw new IllegalArgumentException("Voucher is not started");
        }
        if (coupon.getEndAt() != null && coupon.getEndAt().isBefore(now)) {
            throw new IllegalArgumentException("Voucher is expired");
        }
        if (coupon.getUsageLimit() > 0 && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new IllegalArgumentException("Voucher usage limit reached");
        }
        if (order.getOriginalTotalPrice() < coupon.getMinOrderAmount()) {
            throw new IllegalArgumentException("Order does not meet voucher minimum amount");
        }

        double appliedAmount = calculateDiscount(order, coupon);
        order.setDiscountAmount(appliedAmount);

        OrderCoupon orderCoupon = new OrderCoupon();
        orderCoupon.setOrder(order);
        orderCoupon.setCouponCode(coupon.getCode());
        orderCoupon.setCouponType(coupon.getType());
        orderCoupon.setDiscountValue(coupon.getDiscountValue());
        orderCoupon.setMaxDiscount(coupon.getMaxDiscount());
        orderCoupon.setAppliedAmount(appliedAmount);
        order.setOrderCoupons(new HashSet<>(List.of(orderCoupon)));

        customerVoucher.setUsedAt(now);
        coupon.setUsedCount(coupon.getUsedCount() + 1);
    }

    private double calculateDiscount(Order order, Coupon coupon) {
        if (coupon.getType() == CouponType.PERCENTAGE) {
            double discount = order.getOriginalTotalPrice() * coupon.getDiscountValue() / 100D;
            return coupon.getMaxDiscount() > 0 ? Math.min(discount, coupon.getMaxDiscount()) : discount;
        }
        if (coupon.getType() == CouponType.FIXED_AMOUNT) {
            return Math.min(coupon.getDiscountValue(), order.getOriginalTotalPrice());
        }
        if (coupon.getType() == CouponType.FREE_SHIPPING) {
            return Math.min(order.getShippingFee(), order.getOriginalTotalPrice() + order.getShippingFee());
        }
        return 0D;
    }

    private OrderDetail buildOrderDetail(Order order, CheckoutRequest.CheckoutItemRequest item) {
        ProductVariant productVariant = productVariantRepository.findOrderableById(item.getProductVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Product Variant", "id", item.getProductVariantId()));

        if (productVariant.getStockQuantity() < item.getQuantity()) {
            throw new IllegalArgumentException("Product variant stock is not enough");
        }

        productVariant.setStockQuantity(productVariant.getStockQuantity() - item.getQuantity());

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrder(order);
        orderDetail.setProductVariant(productVariant);
        orderDetail.setQuantity(item.getQuantity());
        orderDetail.setPriceAtPurchase(getEffectivePrice(productVariant));
        return orderDetail;
    }

    private double getEffectivePrice(ProductVariant productVariant) {
        return productVariant.getSalePrice() != null ? productVariant.getSalePrice() : productVariant.getOriginalPrice();
    }

    private Customer getCurrentCustomerWithAddresses() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        return customerRepository.findDetailByUserInfo_Id(accountId)
                .orElseThrow(() -> new AccessDeniedException("Only customers can checkout"));
    }

    private Address getOwnedActiveAddress(Customer customer, UUID addressId) {
        if (customer.getAddressList() == null || customer.getAddressList().isEmpty()) {
            throw new ResourceNotFoundException("Address", "id", addressId);
        }

        return customer.getAddressList().stream()
                .filter(address -> address.getId() != null && address.getId().equals(addressId))
                .filter(address -> !address.isDeleted())
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
    }
}
