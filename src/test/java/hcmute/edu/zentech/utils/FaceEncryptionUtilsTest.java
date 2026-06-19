package hcmute.edu.zentech.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class FaceEncryptionUtilsTest {

    private FaceEncryptionUtils faceEncryptionUtils;

    @BeforeEach
    void setUp() {
        faceEncryptionUtils = new FaceEncryptionUtils();
        ReflectionTestUtils.setField(faceEncryptionUtils, "secretKey", "zentechSecretKeyMustBe32BytesLong!");
    }

    @Test
    void testEncryptDecryptSuccess() {
        String plainText = "[[0.12, -0.43, 0.98], [-0.01, 0.44, 0.12]]";
        String encrypted = faceEncryptionUtils.encrypt(plainText);
        
        assertNotNull(encrypted);
        assertNotEquals(plainText, encrypted);

        String decrypted = faceEncryptionUtils.decrypt(encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    void testEncryptDecryptWithEmptyAndNull() {
        assertNull(faceEncryptionUtils.encrypt(null));
        assertNull(faceEncryptionUtils.encrypt(""));
        assertNull(faceEncryptionUtils.decrypt(null));
        assertNull(faceEncryptionUtils.decrypt(""));
    }

    @Test
    void testDecryptInvalidDataThrowsException() {
        assertThrows(RuntimeException.class, () -> {
            faceEncryptionUtils.decrypt("invalidBase64Data");
        });
    }
}
