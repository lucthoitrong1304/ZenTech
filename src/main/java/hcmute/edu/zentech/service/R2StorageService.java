package hcmute.edu.zentech.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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
     * Delete File
     * @param fileKey : file key
     * */
    public void deleteFile(String fileKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Đã dọn dẹp thành công file cũ trên R2: {}", fileKey);
        } catch (Exception e) {
            log.error("Lỗi khi xóa file trên R2 với key [{}]: {}", fileKey, e.getMessage(), e);
        }
    }

    /**
     * HÀM GET ONE: Lấy Presigned URL cho 1 file cụ thể
     * @param fileKey Đường dẫn chính xác tới file (VD: "Alpha65.../image.webp")
     * @return Presigned URL (có thời hạn) để FE hiển thị ảnh
     */
    public String getPresignedGetUrl(String fileKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expirationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);

            return presignedGetObjectRequest.url().toString();
        } catch (Exception e) {
            log.error("Lỗi khi tạo Presigned GET URL cho key [{}]: {}", fileKey, e.getMessage());
            return null;
        }
    }

    /**
     * HÀM GET ALL: Quét 1 thư mục (Prefix) và trả về list các Presigned URL
     * @param folderPrefix Tên thư mục (VD: "Alpha65 & Power Strip Bundle - Image/")
     * @return Danh sách các Presigned URL của tất cả file trong thư mục đó
     */
    public List<String> getAllPresignedUrlsInFolder(String folderPrefix) {
        try {
            String finalPrefix = folderPrefix.endsWith("/") ? folderPrefix : folderPrefix + "/";

            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(finalPrefix)
                    .build();

            ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);

            return listRes.contents().stream()
                    .map(S3Object::key)
                    .filter(key -> !key.equals(finalPrefix))
                    .map(this::getPresignedGetUrl)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi khi quét thư mục [{}] trên R2: {}", folderPrefix, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * HÀM GET RAW KEYS: Quét thư mục và lấy danh sách Object Key gốc để lưu Database
     * @param folderPrefix Tên thư mục (VD: "Alpha65 & Power Strip Bundle - Image/")
     * @return Danh sách các chuỗi Object Key (VD: ["folder/img1.jpg", "folder/img2.jpg"])
     */
    public List<String> getAllObjectKeysInFolder(String folderPrefix) {
        try {
            String finalPrefix = folderPrefix.endsWith("/") ? folderPrefix : folderPrefix + "/";

            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(finalPrefix)
                    .build();

            ListObjectsV2Response listRes = s3Client.listObjectsV2(listReq);

            return listRes.contents().stream()
                    .map(S3Object::key)
                    .filter(key -> !key.equals(finalPrefix))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Lỗi khi lấy Object Keys từ R2 cho folder [{}]: {}", folderPrefix, e.getMessage());
            return new ArrayList<>();
        }
    }
}
