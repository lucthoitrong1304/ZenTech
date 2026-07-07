package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.AffectedUserDetailDto;
import hcmute.edu.zentech.dto.response.ManagementImpactDashboardDto;
import hcmute.edu.zentech.dto.response.ManagementIncidentImpactDto;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessImpactManagementService {

    private final IncidentRepository incidentRepository;
    private final OrderRepository orderRepository;
    private final BusinessEventRepository businessEventRepository;
    private final ImpactAnalysisResultRepository impactAnalysisResultRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AccountUserRepository accountUserRepository;
    private final CustomerRepository customerRepository;
    private final R2StorageService r2StorageService;
    private final AdminAiRealtimeLogPublisher realtimeLogPublisher;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public ManagementIncidentImpactDto calculateAndSaveImpact(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố với ID: " + incidentId));

        // Dùng firstOccurredAt làm điểm bắt đầu — không bị ghi đè khi có occurrence mới
        Instant start = incident.getFirstOccurredAt() != null
                ? incident.getFirstOccurredAt()
                : (incident.getOccurredAt() != null ? incident.getOccurredAt() : incident.getCreatedAt());
        Instant end = incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();
        
        long durationMs = Duration.between(start, end).toMillis();
        long durationMinutes = Math.max(1, durationMs / 60000);

        double actualRevenue = 0.0;
        int actualOrders = 0;
        int expectedOrders = 0;
        double expectedRevenue = 0.0;
        int affectedUsers = 0;
        long checkoutAttempts = 0;
        double attemptedRevenue = 0.0;

        // Kịch bản MoMo gateway error 30 phút - Demo Protection
        boolean isMomoDemo = (incident.getApiPath() != null && incident.getApiPath().contains("/payments/momo/ipn"))
                || (incident.getCode() != null && incident.getCode().equals("INC-DEMO"));

        // Dùng buffer 5 phút trước incident: FE gửi CHECKOUT_START trước khi BE tạo incident vài giây
        // Cần giới hạn bởi thời điểm RESOLVED của incident trước đó cùng API để tránh tính lặp số liệu
        Instant eventStart = start.minus(Duration.ofMinutes(5));
        if (!isMomoDemo) {
            Optional<Incident> lastResolvedOpt = incidentRepository.findFirstByApiPathAndHttpMethodAndStatusOrderByResolvedAtDesc(
                    incident.getApiPath(), incident.getHttpMethod(), IncidentStatus.RESOLVED
            );
            if (lastResolvedOpt.isPresent()) {
                Instant lastResolvedAt = lastResolvedOpt.get().getResolvedAt();
                if (lastResolvedAt != null && lastResolvedAt.isAfter(eventStart) && lastResolvedAt.isBefore(start)) {
                    eventStart = lastResolvedAt;
                }
            }
        }

        if (isMomoDemo) {
            affectedUsers = 4000;
            expectedOrders = 200;
            expectedRevenue = 100000000.0; // 100M VND
            actualOrders = 0;
            actualRevenue = 0.0;
        } else {
            // 1. Tính toán actual orders và revenue
            List<Order> orders = orderRepository.findSuccessfulOrdersBetween(start, end, OrderStatus.COMPLETED);
            actualOrders = orders.size();
            actualRevenue = orders.stream().mapToDouble(Order::getFinalPrice).sum();

            // 2. Uu tien bang chung checkout thuc te trong cua so su co.
            checkoutAttempts = businessEventRepository.countByEventTypeAndCreatedAtBetween(
                    BusinessEventType.CHECKOUT_START, eventStart, end);
            attemptedRevenue = businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                    BusinessEventType.CHECKOUT_START, eventStart, end);

            // 3. Tinh affected users tu CHECKOUT_START, khong dem lan cac event khong lien quan.
            long affectedCount = businessEventRepository.countAffectedUsersByEventTypeBetween(
                    BusinessEventType.CHECKOUT_START, eventStart, end);
            if (affectedCount == 0) {
                // fallback to distinct traceIds of occurrences of this incident
                try {
                    List<String> emails = activityLogRepository.findUserEmailsByTargetTypeAndTargetIdAndSystemArea("Incident", incident.getId().toString());
                    affectedCount = emails != null ? emails.size() : 0;
                } catch (Exception e) {
                    log.error("Failed to query affected user emails from activity log: {}", e.getMessage());
                }
            }
            affectedUsers = (int) affectedCount;
            boolean hasDirectBusinessEvidence = affectedUsers > 0 || checkoutAttempts > 0 || attemptedRevenue > 0;

            // 3. Tính expected baseline (lấy trùng khung giờ của 3 ngày trước)
            double totalHistoricalRevenue = 0.0;
            int totalHistoricalOrders = 0;
            int daysWithData = 0;

            for (int i = 1; i <= 3; i++) {
                Instant histStart = start.minus(Duration.ofDays(i));
                Instant histEnd = end.minus(Duration.ofDays(i));
                List<Order> histOrders = orderRepository.findSuccessfulOrdersBetween(histStart, histEnd, OrderStatus.COMPLETED);
                if (!histOrders.isEmpty()) {
                    totalHistoricalOrders += histOrders.size();
                    totalHistoricalRevenue += histOrders.stream().mapToDouble(Order::getFinalPrice).sum();
                    daysWithData++;
                }
            }

            if (daysWithData > 0 && hasDirectBusinessEvidence) {
                expectedOrders = totalHistoricalOrders / daysWithData;
                expectedRevenue = totalHistoricalRevenue / daysWithData;
            } else if (affectedUsers > 0) {
                // Fallback nếu chưa có dữ liệu lịch sử (active users * conversion rate * AOV)
                double conversionRate = 0.05; // 5% conversion rate
                double aov = 500000.0; // 500k AOV
                expectedOrders = (int) Math.round(affectedUsers * conversionRate);
                expectedRevenue = expectedOrders * aov;
            }
        }
        if (!isMomoDemo) {
            expectedOrders = Math.max(expectedOrders, actualOrders);
            // Dùng cùng eventStart (đã hiệu chỉnh ở trên) để bắt CHECKOUT_START xảy ra trước incident

            // Mỗi CHECKOUT_START = 1 lần cố đặt hàng thực tế → dùng làm minimum cho expectedOrders
            if (checkoutAttempts > 0) {
                expectedOrders = Math.max(expectedOrders, actualOrders + (int) checkoutAttempts);
            }

            if (attemptedRevenue > 0) {
                expectedRevenue = Math.max(expectedRevenue, actualRevenue + attemptedRevenue);
            }
        }

        double revenueLoss = Math.max(0.0, expectedRevenue - actualRevenue);
        int lostOrders = Math.max(0, expectedOrders - actualOrders);

        // Phân cấp mức độ nghiêm trọng dựa trên thất thoát
        IncidentSeverity severity = IncidentSeverity.LOW;
        if (revenueLoss >= 50000000.0) {
            severity = IncidentSeverity.CRITICAL;
        } else if (revenueLoss >= 20000000.0) {
            severity = IncidentSeverity.HIGH;
        } else if (revenueLoss >= 5000000.0) {
            severity = IncidentSeverity.MEDIUM;
        }

        ImpactAnalysisResult result = impactAnalysisResultRepository.findByIncidentId(incident.getId())
                .orElse(new ImpactAnalysisResult());

        result.setIncident(incident);
        result.setActualRevenue(actualRevenue);
        result.setExpectedRevenue(expectedRevenue);
        result.setRevenueLoss(revenueLoss);
        result.setActualOrders(actualOrders);
        result.setExpectedOrders(expectedOrders);
        result.setLostOrders(lostOrders);
        result.setAffectedUsers(affectedUsers);
        result.setSeverity(severity);

        ImpactAnalysisResult savedResult = impactAnalysisResultRepository.save(result);

        return mapToDto(savedResult, durationMinutes);
    }

    @Transactional
    public ManagementImpactDashboardDto getDashboardStats(Instant startDate, Instant endDate) {
        try {
            List<Incident> openIncidents = incidentRepository.findByStatusOrderByCreatedAtDesc(IncidentStatus.OPEN);
            for (Incident inc : openIncidents) {
                calculateAndSaveImpact(inc.getId());
            }
            List<Incident> investigatingIncidents = incidentRepository.findByStatusOrderByCreatedAtDesc(IncidentStatus.INVESTIGATING);
            for (Incident inc : investigatingIncidents) {
                calculateAndSaveImpact(inc.getId());
            }
        } catch (Exception e) {
            log.error("Failed to pre-calculate active incidents for dashboard stats: {}", e.getMessage());
        }

        List<ImpactAnalysisResult> results = impactAnalysisResultRepository.findByIncidentDateRange(startDate, endDate);
        
        double totalLostRevenue = results.stream().mapToDouble(ImpactAnalysisResult::getRevenueLoss).sum();
        int totalLostOrders = results.stream().mapToInt(ImpactAnalysisResult::getLostOrders).sum();
        int totalAffectedUsers = results.stream().mapToInt(ImpactAnalysisResult::getAffectedUsers).sum();
        long totalIncidents = results.size();

        long criticalCount = results.stream().filter(r -> r.getSeverity() == IncidentSeverity.CRITICAL).count();
        long highCount = results.stream().filter(r -> r.getSeverity() == IncidentSeverity.HIGH).count();
        long mediumCount = results.stream().filter(r -> r.getSeverity() == IncidentSeverity.MEDIUM).count();
        long lowCount = results.stream().filter(r -> r.getSeverity() == IncidentSeverity.LOW).count();

        return ManagementImpactDashboardDto.builder()
                .totalLostRevenue(totalLostRevenue)
                .totalLostOrders(totalLostOrders)
                .totalAffectedUsers(totalAffectedUsers)
                .totalIncidentsCount(totalIncidents)
                .criticalIncidentsCount(criticalCount)
                .highIncidentsCount(highCount)
                .mediumIncidentsCount(mediumCount)
                .lowIncidentsCount(lowCount)
                .build();
    }

    @Transactional
    public Page<ManagementIncidentImpactDto> getIncidentsWithImpact(
            String search, Instant startDate, Instant endDate, Pageable pageable
    ) {
        Page<Incident> incidents = incidentRepository.searchIncidents(
                null, null, null, startDate, endDate, search, pageable
        );
        
        List<ManagementIncidentImpactDto> dtos = incidents.getContent().stream()
                .map(incident -> {
                    ImpactAnalysisResult result = impactAnalysisResultRepository.findByIncidentId(incident.getId())
                            .orElse(null);
                    
                    Instant start = incident.getFirstOccurredAt() != null ? incident.getFirstOccurredAt() : (incident.getOccurredAt() != null ? incident.getOccurredAt() : incident.getCreatedAt());
                    Instant end = incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();
                    long durationMs = Duration.between(start, end).toMillis();
                    long durationMinutes = Math.max(1, durationMs / 60000);

                    if (result == null) {
                        // Tự động tính toán và lưu cache nếu chưa có dữ liệu tác động
                        return calculateAndSaveImpact(incident.getId());
                    }
                    // OPEN incident: tính lại để lấy data mới nhất từ business_events
                    boolean isOpen = incident.getResolvedAt() == null;
                    if (isOpen) {
                        return calculateAndSaveImpact(incident.getId());
                    }
                    return mapToDto(result, durationMinutes);
                })
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, incidents.getTotalElements());
    }

    @Transactional
    public ManagementIncidentImpactDto getIncidentImpactDetail(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố với ID: " + incidentId));

        ImpactAnalysisResult result = impactAnalysisResultRepository.findByIncidentId(incidentId).orElse(null);

        Instant start = incident.getFirstOccurredAt() != null ? incident.getFirstOccurredAt() : (incident.getOccurredAt() != null ? incident.getOccurredAt() : incident.getCreatedAt());
        Instant end = incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();
        long durationMs = Duration.between(start, end).toMillis();
        long durationMinutes = Math.max(1, durationMs / 60000);

        // Incident đang OPEN: luôn tính lại vì end = Instant.now() và business_events có thể tăng thêm
        // Incident đã RESOLVED: dùng cache vì cửa sổ thời gian cố định
        boolean isOpen = incident.getResolvedAt() == null;
        if (result == null || isOpen) {
            return calculateAndSaveImpact(incidentId);
        }
        return mapToDto(result, durationMinutes);
    }

    @Transactional
    public ManagementIncidentImpactDto generateAiSummary(UUID incidentId) {
        ManagementIncidentImpactDto dto = getIncidentImpactDetail(incidentId);

        String analyzeUrl = aiBaseUrl + "/management/analyze/impact";
        log.info("Calling ZenTech-AI management impact analysis URL: {}", analyzeUrl);

        Map<String, Object> payload = new HashMap<>();
        payload.put("incidentCode", dto.getIncidentCode());
        payload.put("serviceName", dto.getServiceName() != null ? dto.getServiceName() : "Không xác định");
        payload.put("apiPath", dto.getApiPath() != null ? dto.getApiPath() : "Không xác định");
        payload.put("httpMethod", dto.getHttpMethod() != null ? dto.getHttpMethod() : "UNKNOWN");
        payload.put("statusCode", dto.getStatusCode() != null ? dto.getStatusCode() : 0);
        payload.put("durationMinutes", dto.getDurationMinutes());
        payload.put("actualRevenue", dto.getActualRevenue());
        payload.put("expectedRevenue", dto.getExpectedRevenue());
        payload.put("revenueLoss", dto.getRevenueLoss());
        payload.put("actualOrders", dto.getActualOrders());
        payload.put("expectedOrders", dto.getExpectedOrders());
        payload.put("lostOrders", dto.getLostOrders());
        payload.put("affectedUsers", dto.getAffectedUsers());
        payload.put("severity", dto.getSeverity().toString());

        try {
            realtimeLogPublisher.publishAiInfo("Starting LLM call for management impact analysis: incident_code=" + dto.getIncidentCode());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            addTraceIdHeader(headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(analyzeUrl, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String aiSummary = (String) response.getBody().get("aiSummary");
                dto.setAiSummary(aiSummary);
                realtimeLogPublisher.publishAiInfo("Management impact analysis completed: incident_code=" + dto.getIncidentCode());
                return dto;
            }
        } catch (Exception e) {
            realtimeLogPublisher.publishAiError("Management impact analysis failed: incident_code=" + dto.getIncidentCode(), e);
            log.error("AI management impact analysis failed: {}", e.getMessage(), e);
            throw new RuntimeException("Dịch vụ phân tích AI không khả dụng: " + e.getMessage());
        }

        throw new RuntimeException("Phản hồi từ AI Service không hợp lệ.");
    }

    private ManagementIncidentImpactDto mapToDto(ImpactAnalysisResult result, long durationMinutes) {
        Incident inc = result.getIncident();
        List<AffectedUserDetailDto> allAffected = getAffectedUserDetails(inc.getId());
        List<AffectedUserDetailDto> topAffected = allAffected != null ? allAffected.stream()
                .limit(3)
                .collect(Collectors.toList()) : Collections.emptyList();

        return ManagementIncidentImpactDto.builder()
                .incidentId(inc.getId())
                .incidentCode(inc.getCode())
                .serviceName(inc.getServiceName())
                .apiPath(inc.getApiPath())
                .httpMethod(inc.getHttpMethod())
                .statusCode(inc.getStatusCode())
                .occurredAt(inc.getOccurredAt())
                .firstOccurredAt(inc.getFirstOccurredAt() != null
                        ? inc.getFirstOccurredAt()
                        : inc.getOccurredAt())
                .resolvedAt(inc.getResolvedAt())
                .status(inc.getStatus())
                .durationMinutes(durationMinutes)
                .actualRevenue(result.getActualRevenue())
                .expectedRevenue(result.getExpectedRevenue())
                .revenueLoss(result.getRevenueLoss())
                .actualOrders(result.getActualOrders())
                .expectedOrders(result.getExpectedOrders())
                .lostOrders(result.getLostOrders())
                .affectedUsers(result.getAffectedUsers())
                .severity(result.getSeverity())
                .topAffectedUsers(topAffected)
                .aiSummary(null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AffectedUserDetailDto> getAffectedUserDetails(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sự cố với ID: " + incidentId));

        List<AffectedUserDetailDto> list = new ArrayList<>();
        java.util.Set<String> seenTraceIds = new java.util.HashSet<>();

        // 1. Thêm occurrence đầu tiên từ chính thực thể Incident
        if (incident.getTraceId() != null) {
            String userIdStr = null;
            String email = "Khách vãng lai (Guest)";
            String fullName = "Khách vãng lai (Guest)";
            String avatarUrl = null;
            if (incident.getUser() != null) {
                userIdStr = incident.getUser().getId().toString();
                email = incident.getUser().getEmail();
                fullName = email.split("@")[0];
                try {
                    Optional<Customer> customerOpt = customerRepository.findByUserInfo_Id(incident.getUser().getId());
                    if (customerOpt.isPresent()) {
                        Customer customer = customerOpt.get();
                        fullName = customer.getFullName();
                        avatarUrl = resolveImageUrl(customer.getImageUrl());
                    }
                } catch (Exception e) {
                    log.error("Failed to query customer info: {}", e.getMessage());
                }
            }
            list.add(AffectedUserDetailDto.builder()
                    .userId(userIdStr)
                    .email(email)
                    .fullName(fullName)
                    .traceId(incident.getTraceId())
                    .lastEventAt(incident.getFirstOccurredAt() != null ? incident.getFirstOccurredAt() : (incident.getCreatedAt() != null ? incident.getCreatedAt() : incident.getOccurredAt()))
                    .lastEventUrl(incident.getApiPath())
                    .avatarUrl(avatarUrl)
                    .build());
            seenTraceIds.add(incident.getTraceId());
        }

        // 2. Thêm các occurrences tiếp theo từ ActivityLog (các sự kiện ghi nhận trùng khớp)
        try {
            List<ActivityLog> logs = activityLogRepository.findByTargetTypeAndTargetId("Incident", incident.getId().toString());
            for (ActivityLog logItem : logs) {
                if (logItem.getArea() == ActivityArea.SYSTEM && logItem.getTraceId() != null) {
                    // Loại bỏ log từ admin/staff
                    if (logItem.getUser() != null && logItem.getUser().getRole() != null
                            && logItem.getUser().getRole() != Role.CUSTOMER) {
                        continue;
                    }
                    String tId = logItem.getTraceId();
                    if (!seenTraceIds.contains(tId)) {
                        seenTraceIds.add(tId);
                        
                        String userIdStr = null;
                        String email = "Khách vãng lai (Guest)";
                        String fullName = "Khách vãng lai (Guest)";
                        String avatarUrl = null;
                        
                        if (logItem.getUser() != null) {
                            userIdStr = logItem.getUser().getId().toString();
                            email = logItem.getUser().getEmail();
                            fullName = email.split("@")[0];
                            try {
                                Optional<Customer> customerOpt = customerRepository.findByUserInfo_Id(logItem.getUser().getId());
                                if (customerOpt.isPresent()) {
                                    Customer customer = customerOpt.get();
                                    fullName = customer.getFullName();
                                    avatarUrl = resolveImageUrl(customer.getImageUrl());
                                }
                            } catch (Exception e) {
                                log.error("Failed to query customer info: {}", e.getMessage());
                            }
                        }
                        
                        list.add(AffectedUserDetailDto.builder()
                                .userId(userIdStr)
                                .email(email)
                                .fullName(fullName)
                                .traceId(tId)
                                .lastEventAt(logItem.getCreatedAt())
                                .lastEventUrl(incident.getApiPath())
                                .avatarUrl(avatarUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch occurrences for affected users: {}", e.getMessage());
        }

        // 3. Fallback: Nếu không có occurrences trong logs hệ thống, truy vấn bảng business_events
        if (list.isEmpty()) {
            Instant start = incident.getFirstOccurredAt() != null ? incident.getFirstOccurredAt() : (incident.getOccurredAt() != null ? incident.getOccurredAt() : incident.getCreatedAt());
            Instant end = incident.getResolvedAt() != null ? incident.getResolvedAt() : Instant.now();
            
            List<BusinessEvent> events = businessEventRepository.findByEventTypeAndCreatedAtBetween(
                    BusinessEventType.CHECKOUT_START, start, end);
            
            Map<String, List<BusinessEvent>> groupedEvents = events.stream()
                    .collect(Collectors.groupingBy(e -> e.getUserId() != null ? e.getUserId().toString() : "GUEST-" + e.getTraceId()));

            for (Map.Entry<String, List<BusinessEvent>> entry : groupedEvents.entrySet()) {
                List<BusinessEvent> userEvents = entry.getValue();
                BusinessEvent latestEvent = userEvents.stream()
                        .max(Comparator.comparing(BusinessEvent::getCreatedAt))
                        .orElse(null);

                if (latestEvent == null) continue;

                String userIdStr = null;
                String email = "Khách vãng lai (Guest)";
                String fullName = "Khách vãng lai (Guest)";
                String traceId = latestEvent.getTraceId();
                String avatarUrl = null;

                if (latestEvent.getUserId() != null) {
                    userIdStr = latestEvent.getUserId().toString();
                    Optional<AccountUser> userOpt = accountUserRepository.findById(latestEvent.getUserId());
                    if (userOpt.isPresent()) {
                        AccountUser user = userOpt.get();
                        email = user.getEmail();
                        fullName = user.getEmail().split("@")[0];
                        
                        try {
                            Optional<Customer> customerOpt = customerRepository.findByUserInfo_Id(user.getId());
                            if (customerOpt.isPresent()) {
                                    Customer customer = customerOpt.get();
                                    fullName = customer.getFullName();
                                    avatarUrl = resolveImageUrl(customer.getImageUrl());
                            }
                        } catch (Exception e) {
                            log.error("Failed to query customer info: {}", e.getMessage());
                        }
                    }
                }

                list.add(AffectedUserDetailDto.builder()
                        .userId(userIdStr)
                        .email(email)
                        .fullName(fullName)
                        .traceId(traceId)
                        .lastEventAt(latestEvent.getCreatedAt())
                        .lastEventUrl(incident.getApiPath())
                        .avatarUrl(avatarUrl)
                        .build());
            }
        }

        // Sắp xếp các sự kiện mới xảy ra lên đầu
        list.sort((o1, o2) -> o2.getLastEventAt().compareTo(o1.getLastEventAt()));

        return list;
    }


    private void addTraceIdHeader(HttpHeaders headers) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            headers.set("X-Trace-Id", traceId.trim());
        }
    }

    private String resolveImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.startsWith("http")) {
            return imageUrl;
        }
        return r2StorageService.getPresignedGetUrl(imageUrl);
    }
}
