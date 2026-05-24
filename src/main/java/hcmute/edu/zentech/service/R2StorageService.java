package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.UploadPresignResponse;
import hcmute.edu.zentech.model.ChatAttachmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class R2StorageService {
    private static final long MAX_REVIEW_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_CHAT_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_CHAT_VIDEO_SIZE_BYTES = 50 * 1024 * 1024;
    private static final long MAX_CHAT_FILE_SIZE_BYTES = 20 * 1024 * 1024;
    private static final Set<String> ALLOWED_REVIEW_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_CHAT_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_CHAT_VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/webm"
    );
    private static final Set<String> ALLOWED_CHAT_FILE_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "application/zip"
    );

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;

    @Value("${cloudflare.r2.presigned-url-expiration}")
    private long expirationMinutes;

    public UploadPresignResponse generateReviewImagePresignedUrl(
            UUID userId,
            String originalFilename,
            String contentType,
            long fileSize
    ) {
        validateReviewImageRequest(contentType, fileSize);

        String fileKey = buildReviewImageKey(userId, originalFilename);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);

        log.info("Generated review image presigned URL for key: {}", fileKey);
        return UploadPresignResponse.builder()
                .presignedUrl(presignedPutObjectRequest.url().toString())
                .fileKey(fileKey)
                .method("PUT")
                .expiresInMinutes(expirationMinutes)
                .requiredHeaders(Map.of("Content-Type", contentType))
                .build();
    }

    public UploadPresignResponse generateChatAttachmentPresignedUrl(
            UUID accountId,
            String originalFilename,
            String contentType,
            long fileSize
    ) {
        validateChatAttachmentRequest(contentType, fileSize);

        String fileKey = buildChatAttachmentKey(accountId, originalFilename);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedPutObjectRequest = s3Presigner.presignPutObject(putObjectPresignRequest);

        log.info("Generated chat attachment presigned URL for key: {}", fileKey);
        return UploadPresignResponse.builder()
                .presignedUrl(presignedPutObjectRequest.url().toString())
                .fileKey(fileKey)
                .method("PUT")
                .expiresInMinutes(expirationMinutes)
                .requiredHeaders(Map.of("Content-Type", contentType))
                .build();
    }

    public void validateUploadedReviewImage(String fileKey, UUID userId) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("imageKey is required");
        }

        String expectedPrefix = getReviewImagePrefix(userId);
        if (!fileKey.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Invalid image key owner");
        }

        HeadObjectResponse headObjectResponse;
        try {
            headObjectResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException("Uploaded image does not exist");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException("Uploaded image does not exist");
            }
            throw e;
        }

        validateReviewImageRequest(headObjectResponse.contentType(), headObjectResponse.contentLength());
    }

    public void validateUploadedChatAttachment(
            String fileKey,
            UUID accountId,
            String expectedContentType,
            long expectedFileSize,
            ChatAttachmentType attachmentType
    ) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("fileKey is required");
        }

        String expectedPrefix = getChatAttachmentPrefix(accountId);
        if (!fileKey.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Invalid chat attachment owner");
        }

        validateChatAttachmentType(expectedContentType, expectedFileSize, attachmentType);

        HeadObjectResponse headObjectResponse;
        try {
            headObjectResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new IllegalArgumentException("Uploaded chat attachment does not exist");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalArgumentException("Uploaded chat attachment does not exist");
            }
            throw e;
        }

        if (!Objects.equals(expectedContentType, headObjectResponse.contentType())) {
            throw new IllegalArgumentException("Chat attachment content type does not match uploaded object");
        }

        if (expectedFileSize != headObjectResponse.contentLength()) {
            throw new IllegalArgumentException("Chat attachment size does not match uploaded object");
        }

        validateChatAttachmentType(
                headObjectResponse.contentType(),
                headObjectResponse.contentLength(),
                attachmentType
        );
    }

    private void validateReviewImageRequest(String contentType, long fileSize) {
        if (!ALLOWED_REVIEW_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WEBP images are allowed");
        }

        if (fileSize <= 0) {
            throw new IllegalArgumentException("Image size must be greater than 0");
        }

        if (fileSize > MAX_REVIEW_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image size must not exceed 5MB");
        }
    }

    private void validateChatAttachmentRequest(String contentType, long fileSize) {
        if (ALLOWED_CHAT_IMAGE_CONTENT_TYPES.contains(contentType)) {
            validateChatAttachmentType(contentType, fileSize, ChatAttachmentType.IMAGE);
            return;
        }

        if (ALLOWED_CHAT_VIDEO_CONTENT_TYPES.contains(contentType)) {
            validateChatAttachmentType(contentType, fileSize, ChatAttachmentType.VIDEO);
            return;
        }

        if (ALLOWED_CHAT_FILE_CONTENT_TYPES.contains(contentType)) {
            validateChatAttachmentType(contentType, fileSize, ChatAttachmentType.FILE);
            return;
        }

        throw new IllegalArgumentException("Unsupported chat attachment content type");
    }

    private void validateChatAttachmentType(String contentType, long fileSize, ChatAttachmentType attachmentType) {
        if (attachmentType == null) {
            throw new IllegalArgumentException("attachmentType is required");
        }

        if (fileSize <= 0) {
            throw new IllegalArgumentException("Chat attachment size must be greater than 0");
        }

        switch (attachmentType) {
            case IMAGE -> {
                if (!ALLOWED_CHAT_IMAGE_CONTENT_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("Only JPEG, PNG, and WEBP images are allowed");
                }
                if (fileSize > MAX_CHAT_IMAGE_SIZE_BYTES) {
                    throw new IllegalArgumentException("Chat image size must not exceed 5MB");
                }
            }
            case VIDEO -> {
                if (!ALLOWED_CHAT_VIDEO_CONTENT_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("Only MP4 and WEBM videos are allowed");
                }
                if (fileSize > MAX_CHAT_VIDEO_SIZE_BYTES) {
                    throw new IllegalArgumentException("Chat video size must not exceed 50MB");
                }
            }
            case FILE -> {
                if (!ALLOWED_CHAT_FILE_CONTENT_TYPES.contains(contentType)) {
                    throw new IllegalArgumentException("Unsupported chat file content type");
                }
                if (fileSize > MAX_CHAT_FILE_SIZE_BYTES) {
                    throw new IllegalArgumentException("Chat file size must not exceed 20MB");
                }
            }
        }
    }

    private String buildReviewImageKey(UUID userId, String originalFilename) {
        return getReviewImagePrefix(userId) + UUID.randomUUID() + "-" + sanitizeFilename(originalFilename);
    }

    private String buildChatAttachmentKey(UUID accountId, String originalFilename) {
        return getChatAttachmentPrefix(accountId) + UUID.randomUUID() + "-" + sanitizeFilename(originalFilename);
    }

    private String getReviewImagePrefix(UUID userId) {
        return "uploads/reviews/" + userId + "/";
    }

    private String getChatAttachmentPrefix(UUID accountId) {
        return "uploads/chat/" + accountId + "/";
    }

    private String sanitizeFilename(String originalFilename) {
        String filename = Optional.ofNullable(originalFilename)
                .map(name -> name.replace("\\", "/"))
                .map(name -> name.substring(name.lastIndexOf("/") + 1))
                .orElse("image");

        String safeFilename = filename.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        return safeFilename.isBlank() ? "image" : safeFilename;
    }

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
