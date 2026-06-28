package hcmute.edu.zentech.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.PaymentGateway;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.model.PaymentTransaction;
import hcmute.edu.zentech.model.PaymentTransactionStatus;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.NotificationType;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.PaymentTransactionRepository;
import hcmute.edu.zentech.service.payment.MomoGatewayClient;
import hcmute.edu.zentech.service.payment.VnpayGatewayClient;
import hcmute.edu.zentech.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final VnpayGatewayClient vnpayGatewayClient;
    private final MomoGatewayClient momoGatewayClient;
    private final ObjectMapper objectMapper;
    private final AccountUserRepository accountUserRepository;
    private final NotificationService notificationService;

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

    @Transactional
    public String buildVnpayReturnUrl(Map<String, String> params) {
        String orderId = params.get("vnp_TxnRef");
        VnpayCallbackResult result = processVnpayCallback(params);
        return buildFrontendResultUrl(orderId, "VNPAY", result.frontendStatus());
    }

    public String buildMomoReturnUrl(Map<String, String> params) {
        String orderId = params.get("orderId");
        boolean valid = momoGatewayClient.verify(params);
        boolean success = "0".equals(params.get("resultCode"));
        return buildFrontendResultUrl(orderId, "MOMO", valid ? (success ? "success" : "failed") : "invalid");
    }

    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        VnpayCallbackResult result = processVnpayCallback(params);
        if (result.responseCode().equals("97")) {
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }
        if (result.responseCode().equals("01")) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }
        if (result.responseCode().equals("04")) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }
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

            Order order = transaction.getOrder();
            // Notify Customer
            if (order.getCustomer() != null && order.getCustomer().getUserInfo() != null) {
                String title = "Thanh toán thành công";
                String content = String.format("Đơn hàng #%s của bạn đã được thanh toán thành công qua cổng %s.",
                        order.getId(), transaction.getGateway());
                notificationService.createNotification(
                        order.getCustomer().getUserInfo().getId(),
                        title,
                        content,
                        NotificationType.ORDER_STATUS,
                        order.getId()
                );
            }

            // Notify Managers
            List<AccountUser> managers = accountUserRepository.findByRoleInAndIsActiveTrue(
                    List.of(Role.ADMIN, Role.MANAGER, Role.OWNER)
            );
            String mgrTitle = "Đơn hàng thanh toán thành công";
            String mgrContent = String.format("Đơn hàng #%s đã được thanh toán thành công qua cổng %s.",
                    order.getId(), transaction.getGateway());
            for (AccountUser mgr : managers) {
                notificationService.createNotification(
                        mgr.getId(),
                        mgrTitle,
                        mgrContent,
                        NotificationType.ORDER_STATUS,
                        order.getId()
                );
            }
        }
    }

    private boolean isAmountMatched(PaymentTransaction transaction, long amount) {
        return transaction.getAmount() == amount;
    }

    private VnpayCallbackResult processVnpayCallback(Map<String, String> params) {
        String requestId = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String gatewayTransactionNo = params.get("vnp_TransactionNo");
        long amount = parseVnpayAmount(params.get("vnp_Amount"));

        if (!vnpayGatewayClient.verify(params)) {
            log.warn(
                    "VNPAY callback invalid signature: txnRef={}, responseCode={}, transactionStatus={}, amount={}, gatewayTransactionNo={}",
                    requestId,
                    responseCode,
                    transactionStatus,
                    amount,
                    gatewayTransactionNo
            );
            return new VnpayCallbackResult("97", "invalid");
        }

        PaymentTransaction transaction = paymentTransactionRepository
                .findByGatewayAndRequestId(PaymentGateway.VNPAY, requestId)
                .orElse(null);
        if (transaction == null) {
            log.warn(
                    "VNPAY callback transaction not found: txnRef={}, responseCode={}, transactionStatus={}, amount={}, gatewayTransactionNo={}",
                    requestId,
                    responseCode,
                    transactionStatus,
                    amount,
                    gatewayTransactionNo
            );
            return new VnpayCallbackResult("01", "invalid");
        }

        if (!isAmountMatched(transaction, amount)) {
            log.warn(
                    "VNPAY callback amount mismatch: txnRef={}, expectedAmount={}, actualAmount={}, responseCode={}, transactionStatus={}, gatewayTransactionNo={}",
                    requestId,
                    transaction.getAmount(),
                    amount,
                    responseCode,
                    transactionStatus,
                    gatewayTransactionNo
            );
            return new VnpayCallbackResult("04", "invalid");
        }

        boolean success = isVnpaySuccessful(params);
        updateTransaction(transaction, success, gatewayTransactionNo, toJson(params));
        log.info(
                "VNPAY callback processed: txnRef={}, responseCode={}, transactionStatus={}, amount={}, gatewayTransactionNo={}, result={}",
                requestId,
                responseCode,
                transactionStatus,
                amount,
                gatewayTransactionNo,
                success ? "SUCCESS" : "FAILED"
        );
        return new VnpayCallbackResult("00", success ? "success" : "failed");
    }

    private boolean isVnpaySuccessful(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
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

    private record VnpayCallbackResult(String responseCode, String frontendStatus) {
    }
}
