package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.aspect.TrackActivity;
import hcmute.edu.zentech.model.ActivityAction;
import hcmute.edu.zentech.model.ActivityArea;
import hcmute.edu.zentech.model.ActivitySeverity;
import hcmute.edu.zentech.dto.request.OrderCreateRequest;
import hcmute.edu.zentech.dto.request.OrderUpdateRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.OrderManagementDetailResponse;
import hcmute.edu.zentech.dto.response.OrderManagementSummaryResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.model.PaymentStatus;
import hcmute.edu.zentech.service.OrderManagementService;
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

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/orders")
@RequiredArgsConstructor
public class OrderManagementController {
    private final OrderManagementService orderManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderManagementSummaryResponse>>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus orderStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderManagementService.getOrders(page, size, sort, keyword, orderStatus, paymentStatus, startDate, endDate)
        ));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderManagementDetailResponse>> getOrderDetail(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderManagementService.getOrderDetail(orderId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderManagementDetailResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderManagementService.createOrder(request)));
    }

    @PatchMapping("/{orderId}")
    @TrackActivity(action = ActivityAction.UPDATE_ORDER_STATUS, area = ActivityArea.MANAGEMENT, module = "ORDER", targetType = "ORDER", severity = ActivitySeverity.IMPORTANT, summary = "Cập nhật trạng thái đơn hàng")
    public ResponseEntity<ApiResponse<OrderManagementDetailResponse>> updateOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderManagementService.updateOrder(orderId, request)));
    }

    @DeleteMapping("/{orderId}")
    @TrackActivity(action = ActivityAction.CANCEL_ORDER, area = ActivityArea.MANAGEMENT, module = "ORDER", targetType = "ORDER", severity = ActivitySeverity.IMPORTANT, summary = "Hủy đơn hàng")
    public ResponseEntity<ApiResponse<OrderManagementDetailResponse>> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderManagementService.cancelOrder(orderId)));
    }
}
