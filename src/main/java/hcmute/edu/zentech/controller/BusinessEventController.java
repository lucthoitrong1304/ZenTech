package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.BusinessEventRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.service.BusinessEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-events")
@RequiredArgsConstructor
public class BusinessEventController {
    private final BusinessEventService businessEventService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> recordEvent(@Valid @RequestBody BusinessEventRequest request) {
        businessEventService.recordEvent(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
