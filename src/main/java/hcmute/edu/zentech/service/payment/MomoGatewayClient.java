package hcmute.edu.zentech.service.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderDetail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MomoGatewayClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${momo.create-url:${MOMO_CREATE_URL:https://test-payment.momo.vn/v2/gateway/api/create}}")
    private String createUrl;

    @Value("${momo.partner-code:${MOMO_PARTNER_CODE:}}")
    private String partnerCode;

    @Value("${momo.access-key:${MOMO_ACCESS_KEY:}}")
    private String accessKey;

    @Value("${momo.secret-key:${MOMO_SECRET_KEY:}}")
    private String secretKey;

    @Value("${momo.request-type:${MOMO_REQUEST_TYPE:payWithMethod}}")
    private String requestType;

    @Value("${app.public-api-base-url:${APP_PUBLIC_API_BASE_URL:http://localhost:8080}}")
    private String publicApiBaseUrl;

    public MomoGatewayClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public PaymentGatewayCreateResult createPayment(Order order, List<OrderDetail> orderDetails) {
        requireConfigured();

        long amount = Math.round(order.getFinalPrice());
        String orderId = order.getId().toString();
        String requestId = requestIdFromOrderId(order.getId());
        String orderInfo = "Thanh toan don hang " + orderId;
        String redirectUrl = publicApiBaseUrl + "/api/payments/momo/return";
        String ipnUrl = publicApiBaseUrl + "/api/payments/momo/ipn";
        String extraData = Base64.getEncoder().encodeToString(("{\"orderId\":\"" + orderId + "\"}").getBytes(StandardCharsets.UTF_8));
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", partnerCode);
        payload.put("partnerName", "ZenTech");
        payload.put("storeId", "ZenTechStore");
        payload.put("requestType", requestType);
        payload.put("ipnUrl", ipnUrl);
        payload.put("redirectUrl", redirectUrl);
        payload.put("orderId", orderId);
        payload.put("amount", amount);
        payload.put("lang", "vi");
        payload.put("orderInfo", orderInfo);
        payload.put("requestId", requestId);
        payload.put("extraData", extraData);
        payload.put("items", toMomoItems(orderDetails));
        payload.put("userInfo", toUserInfo(order));
        payload.put("signature", PaymentHashUtils.hmacSha256(rawSignature, secretKey));

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(createUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("MoMo create payment failed with status " + response.statusCode());
            }

            Map<String, Object> responseBody = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Object payUrl = responseBody.get("payUrl");
            if (payUrl == null || payUrl.toString().isBlank()) {
                throw new IllegalStateException("MoMo did not return payUrl");
            }
            return new PaymentGatewayCreateResult(payUrl.toString(), response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create MoMo payment", ex);
        }
    }

    public boolean verify(Map<String, String> input) {
        requireConfigured();
        String providedSignature = input.get("signature");
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + value(input, "amount")
                + "&extraData=" + value(input, "extraData")
                + "&message=" + value(input, "message")
                + "&orderId=" + value(input, "orderId")
                + "&orderInfo=" + value(input, "orderInfo")
                + "&orderType=" + value(input, "orderType")
                + "&partnerCode=" + value(input, "partnerCode")
                + "&payType=" + value(input, "payType")
                + "&requestId=" + value(input, "requestId")
                + "&responseTime=" + value(input, "responseTime")
                + "&resultCode=" + value(input, "resultCode")
                + "&transId=" + value(input, "transId");
        String expectedSignature = PaymentHashUtils.hmacSha256(rawSignature, secretKey);
        return expectedSignature.equalsIgnoreCase(providedSignature);
    }

    public String requestIdFromOrderId(UUID orderId) {
        return "ZT" + orderId.toString().replace("-", "");
    }

    private List<Map<String, Object>> toMomoItems(List<OrderDetail> orderDetails) {
        return orderDetails.stream()
                .map(item -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", item.getProductVariant().getId().toString());
                    value.put("name", item.getProductVariant().getProduct().getProductName());
                    value.put("price", Math.round(item.getPriceAtPurchase()));
                    value.put("currency", "VND");
                    value.put("quantity", item.getQuantity());
                    value.put("unit", "piece");
                    value.put("totalPrice", Math.round(item.getPriceAtPurchase() * item.getQuantity()));
                    return value;
                })
                .toList();
    }

    private Map<String, Object> toUserInfo(Order order) {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        if (order.getCustomer() != null) {
            userInfo.put("name", order.getCustomer().getFullName());
            if (order.getCustomer().getUserInfo() != null) {
                userInfo.put("email", order.getCustomer().getUserInfo().getEmail());
            }
        }
        if (order.getAddress() != null) {
            userInfo.put("phoneNumber", order.getAddress().getPhoneNumber());
        }
        return userInfo;
    }

    private String value(Map<String, String> input, String key) {
        return input.getOrDefault(key, "");
    }

    private void requireConfigured() {
        if (partnerCode == null || partnerCode.isBlank()
                || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("MOMO_PARTNER_CODE, MOMO_ACCESS_KEY and MOMO_SECRET_KEY must be configured");
        }
    }
}
