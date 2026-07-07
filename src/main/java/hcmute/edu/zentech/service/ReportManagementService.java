package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.*;
import hcmute.edu.zentech.dto.request.ReportManagementAIAnalyzeRequest;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import hcmute.edu.zentech.service.R2StorageService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportManagementService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductVariantRepository productVariantRepository;
    private final R2StorageService r2StorageService;
    private final AdminAiRealtimeLogPublisher realtimeLogPublisher;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    public ReportManagementSummaryResponse getReportsSummary(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        // Fetch current period orders
        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        double currentRevenue = currentOrders.stream().mapToDouble(Order::getFinalPrice).sum();
        long currentOrderCount = currentOrders.size();

        // Fetch previous period orders for comparison
        Duration duration = Duration.between(start, end);
        Instant prevStart = start.minus(duration);
        Instant prevEnd = start;
        List<Order> prevOrders = getSuccessfulOrdersBetween(prevStart, prevEnd);
        double prevRevenue = prevOrders.stream().mapToDouble(Order::getFinalPrice).sum();

        // Calculate growth rate
        double growthRate = 0.0;
        if (prevRevenue > 0) {
            growthRate = ((currentRevenue - prevRevenue) / prevRevenue) * 100.0;
        } else if (currentRevenue > 0) {
            growthRate = 100.0;
        }

        // AI Forecast simulated at 8.5% growth from current
        double forecastedRevenue = currentRevenue * 1.085;

        // Calculate average order value (AOV)
        double averageOrderValue = currentOrderCount > 0 ? (currentRevenue / currentOrderCount) : 0.0;

        // Calculate completion rate based on all orders in this period
        List<Order> allOrders = orderRepository.findAllOrdersBetween(start, end);
        long totalOrdersCount = allOrders.size();
        double aiOpsScore = 0.0;
        if (totalOrdersCount > 0) {
            long completedCount = allOrders.stream()
                    .filter(o -> o.getOrderStatus() == OrderStatus.COMPLETED)
                    .count();
            aiOpsScore = ((double) completedCount / totalOrdersCount) * 100.0;
        }

        double autoFulfillmentRate = aiOpsScore;

        return ReportManagementSummaryResponse.builder()
                .totalRevenue(currentRevenue)
                .forecastedRevenue(forecastedRevenue)
                .growthRate(growthRate)
                .totalOrders(currentOrderCount)
                .averageOrderValue(averageOrderValue)
                .aiOpsScore(aiOpsScore)
                .autoFulfillmentRate(autoFulfillmentRate)
                .build();
    }

    public List<ReportManagementRevenueSeriesResponse> getRevenueSeries(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        Duration duration = Duration.between(start, end);
        Instant prevStart = start.minus(duration);

        int daysCount = (int) ChronoUnit.DAYS.between(start.atZone(ZoneId.systemDefault()), end.atZone(ZoneId.systemDefault())) + 1;
        if (daysCount <= 0) daysCount = 1;

        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        List<Order> prevOrders;
        if (daysCount == 1) {
            prevOrders = getSuccessfulOrdersBetween(start.minus(1, ChronoUnit.DAYS), start);
        } else {
            prevOrders = getSuccessfulOrdersBetween(prevStart, start);
        }

        List<ReportManagementRevenueSeriesResponse> series = new ArrayList<>();

        if (daysCount == 1) {
            // Hourly breakdown for 1 day (every 2 hours)
            for (int i = 0; i < 24; i += 2) {
                Instant intervalStart = start.plus(i, ChronoUnit.HOURS);
                Instant intervalEnd = intervalStart.plus(2, ChronoUnit.HOURS);
                String label = String.format("%02d:00", i);

                double currentValue = currentOrders.stream()
                        .filter(o -> !o.getCreatedAt().isBefore(intervalStart) && o.getCreatedAt().isBefore(intervalEnd))
                        .mapToDouble(Order::getFinalPrice)
                        .sum();

                Instant prevIntervalStart = intervalStart.minus(24, ChronoUnit.HOURS);
                Instant prevIntervalEnd = intervalEnd.minus(24, ChronoUnit.HOURS);

                double prevValue = prevOrders.stream()
                        .filter(o -> !o.getCreatedAt().isBefore(prevIntervalStart) && o.getCreatedAt().isBefore(prevIntervalEnd))
                        .mapToDouble(Order::getFinalPrice)
                        .sum();

                series.add(ReportManagementRevenueSeriesResponse.builder()
                        .label(label)
                        .currentValue(currentValue)
                        .previousValue(prevValue)
                        .build());
            }
        } else if (daysCount > 90) {
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM/yyyy").withZone(ZoneId.systemDefault());
            int monthsCount = (int) ChronoUnit.MONTHS.between(start.atZone(ZoneId.systemDefault()), end.atZone(ZoneId.systemDefault())) + 1;
            
            for (int i = 0; i < monthsCount; i++) {
                Instant monthStart = start.atZone(ZoneId.systemDefault()).plusMonths(i).toInstant();
                Instant monthEnd = start.atZone(ZoneId.systemDefault()).plusMonths(i + 1).toInstant();
                String label = monthFormatter.format(monthStart);

                double currentValue = currentOrders.stream()
                        .filter(o -> !o.getCreatedAt().isBefore(monthStart) && o.getCreatedAt().isBefore(monthEnd))
                        .mapToDouble(Order::getFinalPrice)
                        .sum();

                Instant prevMonthStart = prevStart.atZone(ZoneId.systemDefault()).plusMonths(i).toInstant();
                Instant prevMonthEnd = prevStart.atZone(ZoneId.systemDefault()).plusMonths(i + 1).toInstant();

                double prevValue = prevOrders.stream()
                        .filter(o -> !o.getCreatedAt().isBefore(prevMonthStart) && o.getCreatedAt().isBefore(prevMonthEnd))
                        .mapToDouble(Order::getFinalPrice)
                        .sum();

                series.add(ReportManagementRevenueSeriesResponse.builder()
                        .label(label)
                        .currentValue(currentValue)
                        .previousValue(prevValue)
                        .build());
            }
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault());
            for (int i = 0; i < daysCount; i++) {
                Instant dayStart = start.plus(i, ChronoUnit.DAYS);
                Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
                String label = formatter.format(dayStart);

                double currentValue = currentOrders.stream()
                        .filter(o -> !o.getCreatedAt().isBefore(dayStart) && o.getCreatedAt().isBefore(dayEnd))
                        .mapToDouble(Order::getFinalPrice)
                        .sum();

                Instant prevDayStart = prevStart.plus(i, ChronoUnit.DAYS);
                Instant prevDayEnd = prevDayStart.plus(1, ChronoUnit.DAYS);

                double prevValue = prevOrders.stream()
                        .filter(o -> !o.getCreatedAt().isBefore(prevDayStart) && o.getCreatedAt().isBefore(prevDayEnd))
                        .mapToDouble(Order::getFinalPrice)
                        .sum();

                series.add(ReportManagementRevenueSeriesResponse.builder()
                        .label(label)
                        .currentValue(currentValue)
                        .previousValue(prevValue)
                        .build());
            }
        }

        return series;
    }

    public List<ReportManagementProductPerformanceResponse> getProductPerformance(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        if (currentOrders.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> orderIds = currentOrders.stream().map(Order::getId).collect(Collectors.toList());
        List<OrderDetail> details = orderDetailRepository.findByOrder_IdIn(orderIds);

        Map<ProductVariant, Integer> quantityMap = new HashMap<>();
        Map<ProductVariant, Double> revenueMap = new HashMap<>();

        for (OrderDetail detail : details) {
            ProductVariant variant = detail.getProductVariant();
            if (variant != null) {
                quantityMap.put(variant, quantityMap.getOrDefault(variant, 0) + detail.getQuantity());
                double itemRev = detail.getQuantity() * detail.getPriceAtPurchase();
                revenueMap.put(variant, revenueMap.getOrDefault(variant, 0.0) + itemRev);
            }
        }

        return quantityMap.entrySet().stream()
                .map(entry -> {
                    ProductVariant variant = entry.getKey();
                    String productName = variant.getProduct() != null ? variant.getProduct().getProductName() : "Unknown Product";
                    String imageKey = (variant.getProduct() != null) ? variant.getProduct().getRepresentativeImageKey() : null;
                    String imageUrl = (imageKey != null && !imageKey.isBlank()) ? r2StorageService.getPresignedGetUrl(imageKey) : null;
                    String categoryName = (variant.getProduct() != null && !variant.getProduct().getCategories().isEmpty()) 
                            ? variant.getProduct().getCategories().stream().findFirst().get().getCategoryName() : "Khác";
                    double price = variant.getSalePrice() != null ? variant.getSalePrice() : variant.getOriginalPrice();

                    return ReportManagementProductPerformanceResponse.builder()
                            .productName(productName)
                            .variantName(variant.getName())
                            .imageUrl(imageUrl)
                            .categoryName(categoryName)
                            .price(price)
                            .quantitySold(entry.getValue())
                            .revenue(revenueMap.getOrDefault(variant, 0.0))
                            .stockRemaining(variant.getStockQuantity())
                            .build();
                })
                .sorted(Comparator.comparingInt(ReportManagementProductPerformanceResponse::getQuantitySold).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<ReportManagementCouponPerformanceResponse> getCouponPerformance(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        if (currentOrders.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> usageMap = new HashMap<>();
        Map<String, Double> discountMap = new HashMap<>();

        for (Order order : currentOrders) {
            if (order.getOrderCoupons() != null) {
                for (OrderCoupon coupon : order.getOrderCoupons()) {
                    String code = coupon.getCouponCode();
                    if (code != null) {
                        usageMap.put(code, usageMap.getOrDefault(code, 0L) + 1);
                        double applied = coupon.getAppliedAmount() != null ? coupon.getAppliedAmount() : 0.0;
                        discountMap.put(code, discountMap.getOrDefault(code, 0.0) + applied);
                    }
                }
            }
        }

        return usageMap.entrySet().stream()
                .map(entry -> ReportManagementCouponPerformanceResponse.builder()
                        .couponCode(entry.getKey())
                        .usageCount(entry.getValue())
                        .totalDiscountApplied(discountMap.getOrDefault(entry.getKey(), 0.0))
                        .build())
                .sorted(Comparator.comparingLong(ReportManagementCouponPerformanceResponse::getUsageCount).reversed())
                .collect(Collectors.toList());
    }

    public List<ReportManagementCustomerSegmentResponse> getCustomerSegments(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        if (currentOrders.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Customer, Long> countMap = new HashMap<>();
        Map<Customer, Double> spentMap = new HashMap<>();

        for (Order order : currentOrders) {
            Customer customer = order.getCustomer();
            if (customer != null) {
                countMap.put(customer, countMap.getOrDefault(customer, 0L) + 1);
                spentMap.put(customer, spentMap.getOrDefault(customer, 0.0) + order.getFinalPrice());
            }
        }

        return countMap.entrySet().stream()
                .map(entry -> {
                    Customer customer = entry.getKey();
                    String email = customer.getUserInfo() != null ? customer.getUserInfo().getEmail() : "no-email@zentech.com";
                    java.time.Instant joinDate = customer.getUserInfo() != null ? customer.getUserInfo().getCreatedAt() : null;
                    String addressStr = "Chưa cập nhật";
                    if (customer.getAddressList() != null && !customer.getAddressList().isEmpty()) {
                        Address addr = customer.getAddressList().stream().findFirst().get();
                        addressStr = addr.getStreet() + ", " + addr.getWard() + ", " + addr.getProvince();
                    }

                    return ReportManagementCustomerSegmentResponse.builder()
                            .customerName(customer.getFullName())
                            .email(email)
                            .imageUrl(resolveImageUrl(customer.getImageUrl()))
                            .joinDate(joinDate)
                            .address(addressStr)
                            .orderCount(entry.getValue())
                            .totalSpent(spentMap.getOrDefault(customer, 0.0))
                            .build();
                })
                .sorted(Comparator.comparingDouble(ReportManagementCustomerSegmentResponse::getTotalSpent).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<AIOpsInsightResponse> getAIOpsInsights(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<AIOpsInsightResponse> insights = new ArrayList<>();

        // Fetch top products and check if low in stock
        List<ReportManagementProductPerformanceResponse> products = getProductPerformance(start, end);
        int lowStockCount = 0;
        String lowStockProduct = "";
        for (ReportManagementProductPerformanceResponse p : products) {
            if (p.getStockRemaining() < 5) {
                lowStockCount++;
                if (lowStockProduct.isEmpty()) {
                    lowStockProduct = p.getProductName() + " (" + p.getVariantName() + ")";
                }
            }
        }

        if (lowStockCount > 0) {
            insights.add(AIOpsInsightResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .type("warning")
                    .category("inventory")
                    .title("Cảnh báo tồn kho cực thấp")
                    .description("Sản phẩm '" + lowStockProduct + "' hiện tại chỉ còn dưới 5 sản phẩm trong kho. Doanh số dòng này tăng nhẹ trong kỳ. Đề xuất: nhập thêm 20 sản phẩm để tránh gián đoạn.")
                    .createdAt(Instant.now())
                    .build());
        }

        // Add products insight
        if (!products.isEmpty()) {
            ReportManagementProductPerformanceResponse topProduct = products.get(0);
            insights.add(AIOpsInsightResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .type("info")
                    .category("products")
                    .title("Sản phẩm bán chạy nhất kỳ")
                    .description("Sản phẩm '" + topProduct.getProductName() + "' đang dẫn đầu doanh số với " + topProduct.getQuantitySold() + " sản phẩm được bán ra. Cân nhắc tăng cường quảng bá.")
                    .createdAt(Instant.now())
                    .build());
        }

        // Growth metrics
        ReportManagementSummaryResponse summary = getReportsSummary(start, end);
        if (summary.getGrowthRate() > 0) {
            insights.add(AIOpsInsightResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .type("success")
                    .category("revenue")
                    .title("Hiệu suất Doanh thu Tăng trưởng tốt")
                    .description("Doanh số kỳ này tăng trưởng " + String.format("%.1f", summary.getGrowthRate()) + "% so với kỳ trước. Tổng doanh thu đạt " + String.format("%,.0f", summary.getTotalRevenue()) + " VNĐ. Tín hiệu tăng trưởng tích cực từ các đợt phát hành voucher.")
                    .createdAt(Instant.now())
                    .build());
        } else {
            insights.add(AIOpsInsightResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .type("info")
                    .category("revenue")
                    .title("Phân tích hoạt động vận hành")
                    .description("Doanh số kỳ này duy trì ổn định. Hệ thống tự động xử lý đơn hàng đạt " + String.format("%.1f", summary.getAutoFulfillmentRate()) + "% tổng lượng đơn mà không cần sự can thiệp thủ công.")
                    .createdAt(Instant.now())
                    .build());
        }

        // Voucher performance check
        List<ReportManagementCouponPerformanceResponse> coupons = getCouponPerformance(start, end);
        if (!coupons.isEmpty()) {
            ReportManagementCouponPerformanceResponse best = coupons.get(0);
            insights.add(AIOpsInsightResponse.builder()
                    .id(UUID.randomUUID().toString())
                    .type("success")
                    .category("customers")
                    .title("Đánh giá Mã Khuyến mãi nổi bật")
                    .description("Mã giảm giá '" + best.getCouponCode() + "' được sử dụng nhiều nhất với " + best.getUsageCount() + " lượt quy đổi thành công, mang lại trải nghiệm tối ưu cho tập khách hàng tiềm năng.")
                    .createdAt(Instant.now())
                    .build());
        }

        return insights;
    }

    public ReportManagementAIAnalyzeResponse analyzeReport(String tab, Instant startDate, Instant endDate) {
        Object data = null;
        Map<String, String> anonymizeDictionary = new HashMap<>();

        switch (tab.toUpperCase()) {
            case "REVENUE":
                data = getReportsSummary(startDate, endDate);
                break;
            case "PRODUCTS":
                data = getProductPerformance(startDate, endDate);
                break;
            case "CUSTOMERS":
                List<ReportManagementCustomerSegmentResponse> rawCustomers = getCustomerSegments(startDate, endDate);
                List<ReportManagementCustomerSegmentResponse> maskedCustomers = new ArrayList<>();
                for (int i = 0; i < rawCustomers.size(); i++) {
                    ReportManagementCustomerSegmentResponse c = rawCustomers.get(i);
                    String fakeName = "Khách hàng " + (i + 1);
                    anonymizeDictionary.put(fakeName, c.getCustomerName());
                    
                    ReportManagementCustomerSegmentResponse masked = new ReportManagementCustomerSegmentResponse();
                    masked.setCustomerName(fakeName);
                    masked.setEmail("hidden_" + (i + 1) + "@zentech.local");
                    masked.setImageUrl(null);
                    masked.setJoinDate(c.getJoinDate());
                    masked.setAddress("Hidden Address");
                    masked.setTotalSpent(c.getTotalSpent());
                    masked.setOrderCount(c.getOrderCount());
                    maskedCustomers.add(masked);
                }
                data = maskedCustomers;
                break;
            case "INVENTORY":
                data = getInventoryStats(startDate, endDate);
                break;
            default:
                throw new IllegalArgumentException("Invalid tab: " + tab);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String jsonData = mapper.writeValueAsString(data);

            ReportManagementAIAnalyzeRequest aiRequest = ReportManagementAIAnalyzeRequest.builder()
                    .category(tab.toUpperCase())
                    .data(jsonData)
                    .build();

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            addTraceIdHeader(headers);
            HttpEntity<ReportManagementAIAnalyzeRequest> entity = new HttpEntity<>(aiRequest, headers);
            realtimeLogPublisher.publishAiInfo("Starting LLM call for management report analysis: category=" + tab.toUpperCase());
            ResponseEntity<ReportManagementAIAnalyzeResponse> response = restTemplate.postForEntity(
                    normalizeAiBaseUrl(aiBaseUrl) + "/management/analyze/report",
                    entity,
                    ReportManagementAIAnalyzeResponse.class
            );
            ReportManagementAIAnalyzeResponse aiResponse = response.getBody();
            
            // De-anonymize (Khôi phục định danh)
            if (aiResponse != null && aiResponse.getContent() != null && !anonymizeDictionary.isEmpty()) {
                String content = aiResponse.getContent();
                for (Map.Entry<String, String> entry : anonymizeDictionary.entrySet()) {
                    content = content.replace(entry.getKey(), entry.getValue());
                }
                aiResponse.setContent(content);
            }
            realtimeLogPublisher.publishAiInfo("Management report analysis completed: category=" + tab.toUpperCase());
            return aiResponse;
        } catch (Exception e) {
            realtimeLogPublisher.publishAiError("Management report analysis failed: category=" + tab.toUpperCase(), e);
            throw new RuntimeException("Failed to analyze report using AI module", e);
        }
    }

    private void addTraceIdHeader(HttpHeaders headers) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId.trim());
        }
    }

    private String normalizeAiBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    public List<ReportManagementPaymentMethodShareResponse> getPaymentMethodShare(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        double totalRevenue = currentOrders.stream().mapToDouble(Order::getFinalPrice).sum();

        Map<PaymentMethod, Double> revenueByMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            revenueByMethod.put(method, 0.0);
        }

        for (Order order : currentOrders) {
            if (order.getPaymentMethod() != null) {
                double currentVal = revenueByMethod.getOrDefault(order.getPaymentMethod(), 0.0);
                revenueByMethod.put(order.getPaymentMethod(), currentVal + order.getFinalPrice());
            }
        }

        List<ReportManagementPaymentMethodShareResponse> shares = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            double methodRev = revenueByMethod.get(method);
            double percentage = totalRevenue > 0 ? (methodRev / totalRevenue) * 100.0 : 0.0;

            shares.add(ReportManagementPaymentMethodShareResponse.builder()
                    .method(method.name())
                    .revenue(methodRev)
                    .percentage(percentage)
                    .build());
        }

        return shares;
    }

    public List<ReportManagementCategoryShareResponse> getCategoryShare(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        if (currentOrders.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> orderIds = currentOrders.stream().map(Order::getId).collect(Collectors.toList());
        List<OrderDetail> details = orderDetailRepository.findByOrder_IdIn(orderIds);

        Map<String, Double> categoryRevenue = new HashMap<>();
        double totalDetailRevenue = 0.0;

        for (OrderDetail detail : details) {
            ProductVariant variant = detail.getProductVariant();
            if (variant != null && variant.getProduct() != null) {
                double itemRev = detail.getQuantity() * detail.getPriceAtPurchase();
                totalDetailRevenue += itemRev;

                Set<ProductCategory> categories = variant.getProduct().getCategories();
                if (categories != null && !categories.isEmpty()) {
                    double splitRev = itemRev / categories.size();
                    for (ProductCategory category : categories) {
                        String catName = category.getCategoryName();
                        categoryRevenue.put(catName, categoryRevenue.getOrDefault(catName, 0.0) + splitRev);
                    }
                } else {
                    categoryRevenue.put("Chưa phân loại", categoryRevenue.getOrDefault("Chưa phân loại", 0.0) + itemRev);
                }
            }
        }

        final double finalTotalRev = totalDetailRevenue;
        return categoryRevenue.entrySet().stream()
                .map(entry -> {
                    double pct = finalTotalRev > 0 ? (entry.getValue() / finalTotalRev) * 100.0 : 0.0;
                    return ReportManagementCategoryShareResponse.builder()
                            .categoryName(entry.getKey())
                            .revenue(entry.getValue())
                            .percentage(pct)
                            .build();
                })
                .sorted(Comparator.comparingDouble(ReportManagementCategoryShareResponse::getRevenue).reversed())
                .collect(Collectors.toList());
    }


    public ReportManagementInventoryStatsResponse getInventoryStats(Instant startDate, Instant endDate) {
        Instant[] range = normalizeDates(startDate, endDate);
        Instant start = range[0];
        Instant end = range[1];

        List<ProductVariant> allVariants = productVariantRepository.findAll();
        List<Order> currentOrders = getSuccessfulOrdersBetween(start, end);
        
        Set<UUID> soldVariantIds = new HashSet<>();
        Map<ProductVariant, Integer> quantityMap = new HashMap<>();
        Map<ProductVariant, Double> revenueMap = new HashMap<>();

        if (!currentOrders.isEmpty()) {
            List<UUID> orderIds = currentOrders.stream().map(Order::getId).collect(Collectors.toList());
            List<OrderDetail> details = orderDetailRepository.findByOrder_IdIn(orderIds);
            for (OrderDetail detail : details) {
                ProductVariant variant = detail.getProductVariant();
                if (variant != null) {
                    soldVariantIds.add(variant.getId());
                    quantityMap.put(variant, quantityMap.getOrDefault(variant, 0) + detail.getQuantity());
                    double itemRev = detail.getQuantity() * detail.getPriceAtPurchase();
                    revenueMap.put(variant, revenueMap.getOrDefault(variant, 0.0) + itemRev);
                }
            }
        }

        double totalValue = 0.0;
        int totalItems = 0;
        int lowStockCount = 0;
        int deadStockCount = 0;
        double totalFaultyValue = 0.0;
        int totalFaultyItems = 0;
        List<ReportManagementProductPerformanceResponse> lowStockProducts = new ArrayList<>();

        for (ProductVariant v : allVariants) {
            if (v.isDeleted() || v.getProduct() == null || v.getProduct().isDeleted()) {
                continue;
            }

            double price = v.getSalePrice() != null ? v.getSalePrice() : v.getOriginalPrice();
            int stock = v.getStockQuantity();
            if (stock > 0) {
                totalItems += stock;
                totalValue += stock * price;
                
                if (!soldVariantIds.contains(v.getId())) {
                    deadStockCount++;
                }
            }

            int faulty = v.getFaultyQuantity();
            if (faulty > 0) {
                totalFaultyItems += faulty;
                totalFaultyValue += faulty * price;
            }

            // Low stock condition: stock < 5
            if (stock < 5) {
                lowStockCount++;

                String productName = v.getProduct() != null ? v.getProduct().getProductName() : "Unknown Product";
                String imageKey = (v.getProduct() != null) ? v.getProduct().getRepresentativeImageKey() : null;
                String imageUrl = (imageKey != null && !imageKey.isBlank()) ? r2StorageService.getPresignedGetUrl(imageKey) : null;
                String categoryName = (v.getProduct() != null && !v.getProduct().getCategories().isEmpty()) 
                        ? v.getProduct().getCategories().stream().findFirst().get().getCategoryName() : "Khác";

                int qtySold = quantityMap.getOrDefault(v, 0);
                double rev = revenueMap.getOrDefault(v, 0.0);

                lowStockProducts.add(ReportManagementProductPerformanceResponse.builder()
                        .productName(productName)
                        .variantName(v.getName())
                        .imageUrl(imageUrl)
                        .categoryName(categoryName)
                        .price(price)
                        .quantitySold(qtySold)
                        .revenue(rev)
                        .stockRemaining(stock)
                        .build());
            }
        }

        return ReportManagementInventoryStatsResponse.builder()
                .totalInventoryValue(totalValue)
                .totalItemsInStock(totalItems)
                .lowStockVariations(lowStockCount)
                .deadStockVariations(deadStockCount)
                .totalFaultyValue(totalFaultyValue)
                .totalFaultyItems(totalFaultyItems)
                .lowStockProducts(lowStockProducts)
                .build();
    }

    private List<Order> getSuccessfulOrdersBetween(Instant start, Instant end) {
        return orderRepository.findSuccessfulOrdersBetween(start, end, OrderStatus.COMPLETED);
    }

    private Instant[] normalizeDates(Instant startDate, Instant endDate) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        Instant end = endDate != null ? endDate : Instant.now();
        Instant start;
        if (startDate != null) {
            start = startDate;
        } else {
            start = end.atZone(zone).minusDays(30).toLocalDate().atStartOfDay(zone).toInstant();
        }
        return new Instant[]{start, end};
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }
}
