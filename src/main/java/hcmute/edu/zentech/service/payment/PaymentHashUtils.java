package hcmute.edu.zentech.service.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

final class PaymentHashUtils {
    private PaymentHashUtils() {
    }

    static String hmacSha512(String data, String secret) {
        return hmac("HmacSHA512", data, secret);
    }

    static String hmacSha256(String data, String secret) {
        return hmac("HmacSHA256", data, secret);
    }

    private static String hmac(String algorithm, String data, String secret) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign payment payload", ex);
        }
    }
}
