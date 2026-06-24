package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.CouponRequest;
import hcmute.edu.zentech.dto.request.IssueVoucherRequest;
import hcmute.edu.zentech.dto.response.CouponResponse;
import hcmute.edu.zentech.dto.response.CustomerVoucherDetailResponse;
import hcmute.edu.zentech.dto.response.MarketingStatsResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.CouponManagementMapper;
import hcmute.edu.zentech.model.Coupon;
import hcmute.edu.zentech.model.CouponType;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.CustomerVoucher;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.CouponRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.CustomerVoucherRepository;
import hcmute.edu.zentech.repository.OrderCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponManagementService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "code,asc";

    private final CouponRepository couponRepository;
    private final CustomerVoucherRepository customerVoucherRepository;
    private final CustomerRepository customerRepository;
    private final OrderCouponRepository orderCouponRepository;
    private final CouponManagementMapper couponManagementMapper;

    public PageResponse<CouponResponse> getCoupons(
            int page,
            int size,
            String sort,
            String keyword,
            CouponType type,
            Boolean active
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildSort(sort));
        Page<Coupon> couponPage = couponRepository.searchCoupons(
                normalizeKeyword(keyword),
                type,
                active,
                pageable
        );

        List<CouponResponse> content = couponPage.getContent().stream()
                .map(couponManagementMapper::toResponse)
                .toList();

        return PageResponse.from(couponPage, content);
    }

    public CouponResponse getCouponDetail(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", couponId));
        return couponManagementMapper.toResponse(coupon);
    }

    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsByCode(code)) {
            throw new RuntimeException("Coupon code already exists");
        }

        Coupon coupon = couponManagementMapper.toEntity(request);
        Coupon saved = couponRepository.save(coupon);
        return couponManagementMapper.toResponse(saved);
    }

    @Transactional
    public CouponResponse updateCoupon(UUID couponId, CouponRequest request) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", couponId));

        String code = request.getCode().trim().toUpperCase();
        if (couponRepository.existsCodeExcludingId(code, couponId)) {
            throw new RuntimeException("Coupon code already exists");
        }

        couponManagementMapper.updateEntity(request, coupon);
        return couponManagementMapper.toResponse(coupon);
    }

    @Transactional
    public void deleteCoupon(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", couponId));

        if (customerVoucherRepository.existsUsedVoucherByCouponId(couponId)) {
            throw new RuntimeException("Cannot delete this coupon because it has already been used by customers. Please deactivate it instead.");
        }

        customerVoucherRepository.deleteVouchersByCouponId(couponId);
        couponRepository.delete(coupon);
    }

    @Transactional
    public CouponResponse toggleCouponActive(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", couponId));
        coupon.setActive(!coupon.isActive());
        return couponManagementMapper.toResponse(coupon);
    }

    public PageResponse<CustomerVoucherDetailResponse> getCustomerVouchers(
            int page,
            int size,
            String sort,
            String keyword,
            String couponCode,
            String status
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), buildSortCustomerVoucher(sort));
        Page<CustomerVoucher> cvPage = customerVoucherRepository.searchCustomerVouchers(
                normalizeKeyword(keyword),
                normalizeKeyword(couponCode),
                normalizeKeyword(status),
                Instant.now(),
                pageable
        );

        List<CustomerVoucherDetailResponse> content = cvPage.getContent().stream()
                .map(couponManagementMapper::toCustomerVoucherResponse)
                .toList();

        return PageResponse.from(cvPage, content);
    }

    @Transactional
    public void issueVouchers(IssueVoucherRequest request) {
        Coupon coupon = couponRepository.findById(request.getCouponId())
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "id", request.getCouponId()));

        if (!coupon.isActive()) {
            throw new RuntimeException("Cannot issue inactive coupon");
        }

        if (coupon.getEndAt() != null && coupon.getEndAt().isBefore(Instant.now())) {
            throw new RuntimeException("Cannot issue expired coupon");
        }

        if (request.getCustomerIds() != null && !request.getCustomerIds().isEmpty()) {
            for (UUID customerId : request.getCustomerIds()) {
                Customer customer = customerRepository.findById(customerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
                
                if (customerVoucherRepository.existsByCustomer_IdAndCoupon_Id(customer.getId(), coupon.getId())) {
                    continue;
                }

                CustomerVoucher customerVoucher = new CustomerVoucher();
                customerVoucher.setCustomer(customer);
                customerVoucher.setCoupon(coupon);
                customerVoucherRepository.save(customerVoucher);
            }
        } else if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));
            
            if (customerVoucherRepository.existsByCustomer_IdAndCoupon_Id(customer.getId(), coupon.getId())) {
                throw new RuntimeException("Khách hàng đã sở hữu mã giảm giá này rồi");
            }

            CustomerVoucher customerVoucher = new CustomerVoucher();
            customerVoucher.setCustomer(customer);
            customerVoucher.setCoupon(coupon);
            customerVoucherRepository.save(customerVoucher);
        } else {
            List<Customer> allCustomers = customerRepository.findByUserInfo_Role(Role.CUSTOMER);
            for (Customer customer : allCustomers) {
                if (customerVoucherRepository.existsByCustomer_IdAndCoupon_Id(customer.getId(), coupon.getId())) {
                    continue;
                }
                CustomerVoucher customerVoucher = new CustomerVoucher();
                customerVoucher.setCustomer(customer);
                customerVoucher.setCoupon(coupon);
                customerVoucherRepository.save(customerVoucher);
            }
        }
    }

    @Transactional
    public void revokeVoucher(UUID customerVoucherId) {
        CustomerVoucher voucher = customerVoucherRepository.findById(customerVoucherId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerVoucher", "id", customerVoucherId));

        if (voucher.getUsedAt() != null) {
            throw new RuntimeException("Cannot revoke a voucher that has already been used by the customer.");
        }

        customerVoucherRepository.delete(voucher);
    }

    public MarketingStatsResponse getMarketingStats() {
        long totalCoupons = couponRepository.count();
        
        long activeCoupons = couponRepository.findAll().stream()
                .filter(Coupon::isActive)
                .filter(c -> c.getEndAt() == null || c.getEndAt().isAfter(Instant.now()))
                .count();

        Double totalDiscountGiven = orderCouponRepository.sumAllAppliedAmount();
        
        long totalVouchersIssued = customerVoucherRepository.count();
        long totalVouchersUsed = customerVoucherRepository.findAll().stream()
                .filter(cv -> cv.getUsedAt() != null)
                .count();

        double redemptionRate = totalVouchersIssued == 0 ? 0.0 : ((double) totalVouchersUsed / totalVouchersIssued) * 100.0;

        return MarketingStatsResponse.builder()
                .totalCoupons(totalCoupons)
                .activeCoupons(activeCoupons)
                .totalDiscountGiven(totalDiscountGiven != null ? totalDiscountGiven : 0.0)
                .redemptionRate(redemptionRate)
                .build();
    }

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Sort buildSort(String sort) {
        String sortValue = (sort == null || sort.isBlank()) ? DEFAULT_SORT : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "asc";

        Map<String, String> sortableFields = Map.of(
                "code", "code",
                "type", "type",
                "discountValue", "discountValue",
                "startAt", "startAt",
                "endAt", "endAt",
                "active", "active"
        );
        String mappedField = sortableFields.getOrDefault(requestedField, "code");
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, mappedField), new Sort.Order(Sort.Direction.ASC, "id"));
    }

    private Sort buildSortCustomerVoucher(String sort) {
        String sortValue = (sort == null || sort.isBlank()) ? "issuedAt,desc" : sort;
        String[] parts = sortValue.split(",", 2);
        String requestedField = parts[0].trim();
        String directionValue = parts.length > 1 ? parts[1].trim() : "desc";

        Map<String, String> sortableFields = Map.of(
                "issuedAt", "issuedAt",
                "usedAt", "usedAt"
        );
        String mappedField = sortableFields.getOrDefault(requestedField, "issuedAt");
        Sort.Direction direction = "desc".equalsIgnoreCase(directionValue) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direction, mappedField), new Sort.Order(Sort.Direction.ASC, "id"));
    }
}
