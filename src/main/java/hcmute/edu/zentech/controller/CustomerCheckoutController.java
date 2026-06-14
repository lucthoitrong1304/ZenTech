package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.CheckoutRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CheckoutResponse;
import hcmute.edu.zentech.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
public class CustomerCheckoutController {
    private final CheckoutService checkoutService;

    @PostMapping("/checkout")
    @TrackActivity(action = ActivityAction.CHECKOUT_COMPLETED, failureAction = ActivityAction.CHECKOUT_FAILED, area = ActivityArea.CUSTOMER, module = "CHECKOUT", targetType = "ORDER", severity = ActivitySeverity.CRITICAL, summary = "Khách hàng đặt hàng", logOnFailure = true)
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                checkoutService.checkout(request, getClientIp(httpServletRequest))
        ));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
