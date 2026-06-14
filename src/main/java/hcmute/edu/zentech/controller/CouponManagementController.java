package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.CouponRequest;
import hcmute.edu.zentech.dto.request.IssueVoucherRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CouponResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherDetailResponse;
import hcmute.edu.zentech.dto.response.MarketingStatsResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.CouponType;
import hcmute.edu.zentech.service.CouponManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/coupons")
@RequiredArgsConstructor
public class CouponManagementController {
    private final CouponManagementService couponManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CouponResponse>>> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "code,asc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CouponType type,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                couponManagementService.getCoupons(page, size, sort, keyword, type, active)
        ));
    }

    @GetMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponDetail(@PathVariable UUID couponId) {
        return ResponseEntity.ok(ApiResponse.success(couponManagementService.getCouponDetail(couponId)));
    }

    @PostMapping
    @TrackActivity(action = ActivityAction.CREATE_COUPON, area = ActivityArea.MANAGEMENT, module = "MARKETING", targetType = "COUPON", severity = ActivitySeverity.IMPORTANT, summary = "Tạo mã giảm giá")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CouponRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponManagementService.createCoupon(request)));
    }

    @PatchMapping("/{couponId}")
    @TrackActivity(action = ActivityAction.UPDATE_COUPON, area = ActivityArea.MANAGEMENT, module = "MARKETING", targetType = "COUPON", severity = ActivitySeverity.IMPORTANT, summary = "Cập nhật mã giảm giá")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable UUID couponId,
            @Valid @RequestBody CouponRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponManagementService.updateCoupon(couponId, request)));
    }

    @DeleteMapping("/{couponId}")
    @TrackActivity(action = ActivityAction.DELETE_COUPON, area = ActivityArea.MANAGEMENT, module = "MARKETING", targetType = "COUPON", severity = ActivitySeverity.CRITICAL, summary = "Xóa mã giảm giá")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable UUID couponId) {
        couponManagementService.deleteCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{couponId}/toggle-active")
    @TrackActivity(action = ActivityAction.UPDATE_COUPON, area = ActivityArea.MANAGEMENT, module = "MARKETING", targetType = "COUPON", severity = ActivitySeverity.IMPORTANT, summary = "Bật/tắt mã giảm giá")
    public ResponseEntity<ApiResponse<CouponResponse>> toggleCouponActive(@PathVariable UUID couponId) {
        return ResponseEntity.ok(ApiResponse.success(couponManagementService.toggleCouponActive(couponId)));
    }

    @GetMapping("/vouchers")
    public ResponseEntity<ApiResponse<PageResponse<CustomerVoucherDetailResponse>>> getCustomerVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "issuedAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String couponCode,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                couponManagementService.getCustomerVouchers(page, size, sort, keyword, couponCode, status)
        ));
    }

    @PostMapping("/vouchers/issue")
    @TrackActivity(action = ActivityAction.ISSUE_VOUCHER, area = ActivityArea.MANAGEMENT, module = "MARKETING", targetType = "VOUCHER", severity = ActivitySeverity.IMPORTANT, summary = "Phát voucher")
    public ResponseEntity<ApiResponse<Void>> issueVouchers(
            @Valid @RequestBody IssueVoucherRequest request
    ) {
        couponManagementService.issueVouchers(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/vouchers/{customerVoucherId}")
    @TrackActivity(action = ActivityAction.REVOKE_VOUCHER, area = ActivityArea.MANAGEMENT, module = "MARKETING", targetType = "VOUCHER", severity = ActivitySeverity.IMPORTANT, summary = "Thu hồi voucher")
    public ResponseEntity<ApiResponse<Void>> revokeVoucher(@PathVariable UUID customerVoucherId) {
        couponManagementService.revokeVoucher(customerVoucherId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<MarketingStatsResponse>> getMarketingStats() {
        return ResponseEntity.ok(ApiResponse.success(couponManagementService.getMarketingStats()));
    }
}
