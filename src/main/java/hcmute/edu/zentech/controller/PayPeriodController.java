package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.model.PayPeriod;
import hcmute.edu.zentech.service.PayPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/pay-periods")
@RequiredArgsConstructor
public class PayPeriodController {
    private final PayPeriodService payPeriodService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PayPeriod>>> getAllPeriods() {
        return ResponseEntity.ok(ApiResponse.success(payPeriodService.getAllPeriods()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayPeriod>> createPeriod(@RequestBody PayPeriod period) {
        return ResponseEntity.status(201).body(ApiResponse.success(payPeriodService.createPeriod(period)));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<PayPeriod>> toggleLock(
            @PathVariable UUID id,
            @RequestParam boolean lock
    ) {
        return ResponseEntity.ok(ApiResponse.success(payPeriodService.toggleLock(id, lock)));
    }
}
