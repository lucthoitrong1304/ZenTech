package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.UploadPresignRequest;
import hcmute.edu.zentech.dto.response.UploadPresignResponse;
import hcmute.edu.zentech.model.UploadPurpose;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {
    private final R2StorageService r2StorageService;

    public UploadPresignResponse createPresignedUploadUrl(UploadPresignRequest request) {
        UUID currentUserId = SecurityContextUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        if (request.getPurpose() == UploadPurpose.PRODUCT_REVIEW) {
            return r2StorageService.generateReviewImagePresignedUrl(
                    currentUserId,
                    request.getOriginalFilename(),
                    request.getContentType(),
                    request.getFileSize()
            );
        }

        if (request.getPurpose() == UploadPurpose.PRODUCT_REVIEW_VIDEO) {
            return r2StorageService.generateReviewVideoPresignedUrl(
                    currentUserId,
                    request.getOriginalFilename(),
                    request.getContentType(),
                    request.getFileSize()
            );
        }

        if (request.getPurpose() == UploadPurpose.CHAT_ATTACHMENT) {
            return r2StorageService.generateChatAttachmentPresignedUrl(
                    currentUserId,
                    request.getOriginalFilename(),
                    request.getContentType(),
                    request.getFileSize()
            );
        }

        if (request.getPurpose() == UploadPurpose.CUSTOMER_AVATAR) {
            return r2StorageService.generateCustomerAvatarPresignedUrl(
                    currentUserId,
                    request.getOriginalFilename(),
                    request.getContentType(),
                    request.getFileSize()
            );
        }

        throw new IllegalArgumentException("Unsupported upload purpose");
    }
}

