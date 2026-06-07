package hcmute.edu.zentech.service.payment;

import hcmute.edu.zentech.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class VnpayGatewayClient {
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Value("${vnpay.pay-url:${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}}")
    private String payUrl;

    @Value("${vnpay.tmn-code:${VNPAY_TMN_CODE:}}")
    private String tmnCode;

    @Value("${vnpay.hash-secret:${VNPAY_HASH_SECRET:}}")
    private String hashSecret;

    @Value("${app.public-api-base-url:${APP_PUBLIC_API_BASE_URL:http://localhost:8080}}")
    private String publicApiBaseUrl;

    public PaymentGatewayCreateResult createPayment(Order order, String clientIp) {
        requireConfigured();

        long amount = Math.round(order.getFinalPrice());
        String txnRef = order.getId().toString();
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime expireAt = now.plusMinutes(15);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + txnRef);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", publicApiBaseUrl + "/api/payments/vnpay/return");
        params.put("vnp_IpAddr", normalizeClientIp(clientIp));
        params.put("vnp_CreateDate", VNPAY_DATE_FORMAT.format(now));
        params.put("vnp_ExpireDate", VNPAY_DATE_FORMAT.format(expireAt));

        String hashData = buildQuery(params);
        String secureHash = PaymentHashUtils.hmacSha512(hashData, hashSecret);
        String paymentUrl = payUrl + "?" + hashData + "&vnp_SecureHash=" + secureHash;
        return new PaymentGatewayCreateResult(paymentUrl, hashData);
    }

    public boolean verify(Map<String, String> input) {
        requireConfigured();
        String providedHash = input.get("vnp_SecureHash");
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }

        Map<String, String> signedParams = input.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, TreeMap::new));
        String expectedHash = PaymentHashUtils.hmacSha512(buildQuery(signedParams), hashSecret);
        return expectedHash.equalsIgnoreCase(providedHash);
    }

    public String requestIdFromOrderId(UUID orderId) {
        return orderId.toString();
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String normalizeClientIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp;
    }

    private void requireConfigured() {
        if (tmnCode == null || tmnCode.isBlank() || hashSecret == null || hashSecret.isBlank()) {
            throw new IllegalStateException("VNPAY_TMN_CODE and VNPAY_HASH_SECRET must be configured");
        }
    }
}
