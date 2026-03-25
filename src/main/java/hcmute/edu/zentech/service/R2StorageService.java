package hcmute.edu.zentech.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class R2StorageService {
    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.presigned-url-expiration}")
    private long expirationMinutes;

    /**
     * Hàm tạo đường dẫn để đẩy ảnh lên cloudflare
     * @param originalFilename: Tên name gốc
     * @param contentType: MimeType (PNG, JPG, JPEG,...)
     * **/
    public Map<String, String> generatePresignedUrl(String originalFilename, String contentType) {
        // Tạo file key mới để tránh người dùng gửi ảnh trùng name
        String fileKey = "uploads/" + UUID.randomUUID() + "-" + originalFilename;

        // Khởi tạo request
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(contentType)
                .build();

        // Khởi tạo Presign (thời hạn link đẩy file lên lưu trữ)
        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        // Tạo link trả về cho FE
        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);

        Map<String, String> response = new HashMap<>();
        response.put("presignedUrl", presignedPutObjectRequest.url().toString());
        response.put("fileKey", fileKey);

        log.info("Đã tạo Presigned URL thành công cho file: {}", fileKey);
        return response;
    }

    /**
     * Delete Image
     * @param fileKey : file key
     * */
    public void deleteImage(String fileKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Đã dọn dẹp thành công ảnh cũ trên R2: {}", fileKey);
        } catch (Exception e) {
            log.error("Lỗi khi xóa ảnh trên R2 với key [{}]: {}", fileKey, e.getMessage(), e);
        }
    }
}
