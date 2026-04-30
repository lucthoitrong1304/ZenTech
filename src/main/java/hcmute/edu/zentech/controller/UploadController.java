package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.UploadPresignRequest;
import hcmute.edu.zentech.dto.response.UploadPresignResponse;
import hcmute.edu.zentech.service.UploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploadService;

    @PostMapping("/presign")
    public ResponseEntity<UploadPresignResponse> createPresignedUploadUrl(
            @Valid @RequestBody UploadPresignRequest request
    ) {
        return ResponseEntity.ok(uploadService.createPresignedUploadUrl(request));
    }
}
