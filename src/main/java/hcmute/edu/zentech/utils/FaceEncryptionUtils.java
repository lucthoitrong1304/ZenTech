package hcmute.edu.zentech.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class FaceEncryptionUtils {

    @Value("${zentech.security.face-encryption-key:zentechSecretKeyMustBe32BytesLong!}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // in bits
    private static final int IV_LENGTH = 12; // in bytes

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            byte[] keyBytes = deriveKey(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] encryptedBytes = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, encryptedBytes, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, encryptedBytes, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during face descriptor encryption", e);
        }
    }

    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return null;
        }
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(cipherTextBase64);
            if (encryptedBytes.length < IV_LENGTH) {
                throw new IllegalArgumentException("Ciphertext is too short");
            }

            byte[] keyBytes = deriveKey(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(encryptedBytes, IV_LENGTH, encryptedBytes.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Error occurred during face descriptor decryption", e);
        }
    }

    private byte[] deriveKey(String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        return sha.digest(key.getBytes("UTF-8"));
    }
}
