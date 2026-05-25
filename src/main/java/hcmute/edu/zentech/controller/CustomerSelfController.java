package hcmute.edu.zentech.controller;

import hcmute.edu.zentech.dto.request.CustomerAddressRequest;
import hcmute.edu.zentech.dto.request.UpdateMyProfileRequest;
import hcmute.edu.zentech.dto.response.ApiResponse;
import hcmute.edu.zentech.dto.response.CustomerAddressResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderDetailResponse;
import hcmute.edu.zentech.dto.response.CustomerOrderHistoryResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherResponse;
import hcmute.edu.zentech.dto.response.MyProfileResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.model.CustomerVoucherStatus;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.service.CustomerSelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
public class CustomerSelfController {
    private final CustomerSelfService customerSelfService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.getMyProfile()));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateMyProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.updateMyProfile(request)));
    }

    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<CustomerAddressResponse>>> getMyAddresses() {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.getMyAddresses()));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> createMyAddress(
            @Valid @RequestBody CustomerAddressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.createMyAddress(request)));
    }

    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> updateMyAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody CustomerAddressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.updateMyAddress(addressId, request)));
    }

    @PatchMapping("/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<CustomerAddressResponse>> setMyDefaultAddress(@PathVariable UUID addressId) {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.setMyDefaultAddress(addressId)));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyAddress(@PathVariable UUID addressId) {
        customerSelfService.deleteMyAddress(addressId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<CustomerOrderHistoryResponse>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                customerSelfService.getMyOrders(page, size, sort, status)
        ));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<CustomerOrderDetailResponse>> getMyOrderDetail(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(customerSelfService.getMyOrderDetail(orderId)));
    }

    @GetMapping("/vouchers")
    public ResponseEntity<ApiResponse<PageResponse<CustomerVoucherResponse>>> getMyVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "issuedAt,desc") String sort,
            @RequestParam(required = false) CustomerVoucherStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                customerSelfService.getMyVouchers(page, size, sort, status)
        ));
    }
}
