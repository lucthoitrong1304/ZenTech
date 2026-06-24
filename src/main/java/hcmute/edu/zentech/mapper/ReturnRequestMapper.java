package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.ReturnRequestResponse;
import hcmute.edu.zentech.model.ReturnRequest;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReturnRequestMapper {
    private final R2StorageService r2StorageService;
    
    public ReturnRequestResponse toResponse(ReturnRequest request) {
        if (request == null) {
            return null;
        }

        UUID orderId = request.getOrder() != null ? request.getOrder().getId() : null;
        String customerName = request.getOrder() != null && request.getOrder().getCustomer() != null 
                ? request.getOrder().getCustomer().getFullName() : null;

        List<String> urls = new ArrayList<>();
        if (request.getProofFileKeys() != null && !request.getProofFileKeys().isBlank()) {
            for (String key : request.getProofFileKeys().split(",")) {
                String trimmedKey = key.trim();
                if (!trimmedKey.isEmpty()) {
                    urls.add(r2StorageService.getPresignedGetUrl(trimmedKey));
                }
            }
        }

        return ReturnRequestResponse.builder()
                .id(request.getId())
                .orderId(orderId)
                .customerName(customerName)
                .reason(request.getReason())
                .details(request.getDetails())
                .proofFileKeys(request.getProofFileKeys())
                .proofFileUrls(urls)
                .status(request.getStatus())
                .resellable(request.isResellable())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
