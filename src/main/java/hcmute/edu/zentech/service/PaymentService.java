package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.PaymentGateway;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.PaymentTransaction;
import hcmute.edu.zentech.model.PaymentTransactionStatus;
import hcmute.edu.zentech.repository.PaymentTransactionRepository;
import hcmute.edu.zentech.service.payment.MomoGatewayClient;
import hcmute.edu.zentech.service.payment.VnpayGatewayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VnpayGatewayClient vnpayGatewayClient;
    private final MomoGatewayClient momoGatewayClient;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-base-url:${APP_FRONTEND_BASE_URL:http://localhost:4200}}")
    private String frontendBaseUrl;

    @Transactional
    public PaymentTransaction createPendingTransaction(
            Order order,
            PaymentGateway gateway,
            String requestId,
            long amount,
            String paymentUrl,
            String rawPayload
    ) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order);
        transaction.setGateway(gateway);
        transaction.setRequestId(requestId);
        transaction.setAmount(amount);
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setPaymentUrl(paymentUrl);
        transaction.setRawPayload(rawPayload);
        return paymentTransactionRepository.save(transaction);
    }

    public String buildVnpayReturnUrl(Map<String, String> params) {
        String orderId = params.get("vnp_TxnRef");
        boolean valid = vnpayGatewayClient.verify(params);
        boolean success = "00".equals(params.get("vnp_ResponseCode"));
        return buildFrontendResultUrl(orderId, "VNPAY", valid ? (success ? "success" : "failed") : "invalid");
    }

    public String buildMomoReturnUrl(Map<String, String> params) {
        String orderId = params.get("orderId");
        boolean valid = momoGatewayClient.verify(params);
        boolean success = "0".equals(params.get("resultCode"));
        return buildFrontendResultUrl(orderId, "MOMO", valid ? (success ? "success" : "failed") : "invalid");
    }

    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        if (!vnpayGatewayClient.verify(params)) {
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }

        String requestId = params.get("vnp_TxnRef");
        PaymentTransaction transaction = paymentTransactionRepository
                .findByGatewayAndRequestId(PaymentGateway.VNPAY, requestId)
                .orElse(null);
        if (transaction == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }
        if (!isAmountMatched(transaction, parseVnpayAmount(params.get("vnp_Amount")))) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }

        boolean success = "00".equals(params.get("vnp_ResponseCode"));
        updateTransaction(transaction, success, params.get("vnp_TransactionNo"), toJson(params));
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    @Transactional
    public void handleMomoIpn(Map<String, String> params) {
        if (!momoGatewayClient.verify(params)) {
            throw new IllegalArgumentException("Invalid MoMo signature");
        }

        String requestId = params.get("requestId");
        PaymentTransaction transaction = paymentTransactionRepository
                .findByGatewayAndRequestId(PaymentGateway.MOMO, requestId)
                .orElseThrow(() -> new IllegalArgumentException("MoMo transaction not found"));
        if (!isAmountMatched(transaction, parseAmount(params.get("amount")))) {
            throw new IllegalArgumentException("Invalid MoMo amount");
        }

        boolean success = "0".equals(params.get("resultCode"));
        updateTransaction(transaction, success, params.get("transId"), toJson(params));
    }

    private void updateTransaction(
            PaymentTransaction transaction,
            boolean success,
            String gatewayTransactionId,
            String rawPayload
    ) {
        if (transaction.getStatus() == PaymentTransactionStatus.SUCCESS) {
            return;
        }

        transaction.setGatewayTransactionId(gatewayTransactionId);
        transaction.setRawPayload(rawPayload);
        transaction.setStatus(success ? PaymentTransactionStatus.SUCCESS : PaymentTransactionStatus.FAILED);
        transaction.setUpdatedAt(Instant.now());

        if (success) {
            transaction.setPaidAt(Instant.now());
            transaction.getOrder().setPaymentStatus(PaymentStatus.SUCCESS);
        }
    }

    private boolean isAmountMatched(PaymentTransaction transaction, long amount) {
        return transaction.getAmount() == amount;
    }

    private long parseVnpayAmount(String value) {
        return parseAmount(value) / 100;
    }

    private long parseAmount(String value) {
        try {
            return Long.parseLong(value == null ? "0" : value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String buildFrontendResultUrl(String orderId, String gateway, String status) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/checkout/result")
                .queryParam("orderId", orderId)
                .queryParam("gateway", gateway)
                .queryParam("status", status)
                .build()
                .toUriString();
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            return params.toString();
        }
    }
}
