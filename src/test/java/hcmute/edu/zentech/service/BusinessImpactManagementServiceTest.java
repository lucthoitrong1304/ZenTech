package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.ManagementIncidentImpactDto;
import hcmute.edu.zentech.model.BusinessEventType;
import hcmute.edu.zentech.model.ImpactAnalysisResult;
import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.model.Order;
import hcmute.edu.zentech.model.OrderStatus;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.ActivityLogRepository;
import hcmute.edu.zentech.repository.BusinessEventRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.ImpactAnalysisResultRepository;
import hcmute.edu.zentech.repository.IncidentRepository;
import hcmute.edu.zentech.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessImpactManagementServiceTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BusinessEventRepository businessEventRepository;
    @Mock private ImpactAnalysisResultRepository impactAnalysisResultRepository;
    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private AccountUserRepository accountUserRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private R2StorageService r2StorageService;
    @Mock private AdminAiRealtimeLogPublisher realtimeLogPublisher;

    private BusinessImpactManagementService service;

    @BeforeEach
    void setUp() {
        service = new BusinessImpactManagementService(
                incidentRepository,
                orderRepository,
                businessEventRepository,
                impactAnalysisResultRepository,
                activityLogRepository,
                accountUserRepository,
                customerRepository,
                r2StorageService,
                realtimeLogPublisher
        );

        lenient().when(incidentRepository.findFirstByApiPathAndHttpMethodAndStatusOrderByResolvedAtDesc(
                any(), any(), eq(IncidentStatus.RESOLVED)
        )).thenReturn(Optional.empty());
        lenient().when(impactAnalysisResultRepository.findByIncidentId(any())).thenReturn(Optional.empty());
        lenient().when(impactAnalysisResultRepository.save(any(ImpactAnalysisResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(activityLogRepository.findUserEmailsByTargetTypeAndTargetIdAndSystemArea(any(), any()))
                .thenReturn(List.of());
        lenient().when(activityLogRepository.findByTargetTypeAndTargetId(any(), any()))
                .thenReturn(List.of());
        lenient().when(businessEventRepository.findByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(List.of());
    }

    @Test
    void returnsZeroImpactWhenThereIsNoBusinessEvidence() {
        Incident incident = incident("INC-0001", "/api/customers/me/checkout");
        stubIncident(incident);
        stubOrderWindows(List.of(), List.of(), List.of(), List.of());
        when(businessEventRepository.countAffectedUsersByEventTypeBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.countByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0.0);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getAffectedUsers()).isZero();
        assertThat(result.getLostOrders()).isZero();
        assertThat(result.getRevenueLoss()).isZero();
        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.LOW);
    }

    @Test
    void usesAovFallbackOnlyWhenAffectedUsersExist() {
        Incident incident = incident("INC-0002", "/api/customers/me/checkout");
        stubIncident(incident);
        stubOrderWindows(List.of(), List.of(), List.of(), List.of());
        when(businessEventRepository.countAffectedUsersByEventTypeBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(10L);
        when(businessEventRepository.countByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0.0);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getAffectedUsers()).isEqualTo(10);
        assertThat(result.getExpectedOrders()).isEqualTo(1);
        assertThat(result.getRevenueLoss()).isEqualTo(500000.0);
    }

    @Test
    void usesCheckoutAttemptAmountAsRevenueLossEvidence() {
        Incident incident = incident("INC-0003", "/api/customers/me/checkout");
        stubIncident(incident);
        stubOrderWindows(List.of(), List.of(), List.of(), List.of());
        when(businessEventRepository.countAffectedUsersByEventTypeBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(1L);
        when(businessEventRepository.countByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(1L);
        when(businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(8315000.0);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getLostOrders()).isEqualTo(1);
        assertThat(result.getRevenueLoss()).isEqualTo(8315000.0);
        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.MEDIUM);
    }

    @Test
    void usesHistoricalBaselineWhenItIsHigherThanActualRevenue() {
        Incident incident = incident("INC-0004", "/api/customers/me/checkout");
        stubIncident(incident);
        stubOrderWindows(
                List.of(order(500000.0)),
                List.of(order(1000000.0)),
                List.of(order(2000000.0)),
                List.of(order(3000000.0))
        );
        when(businessEventRepository.countAffectedUsersByEventTypeBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(1L);
        when(businessEventRepository.countByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0.0);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getActualRevenue()).isEqualTo(500000.0);
        assertThat(result.getExpectedRevenue()).isEqualTo(2000000.0);
        assertThat(result.getRevenueLoss()).isEqualTo(1500000.0);
    }

    @Test
    void ignoresHistoricalBaselineWhenThereIsNoDirectBusinessEvidence() {
        Incident incident = incident("INC-0006", "/api/products");
        stubIncident(incident);
        stubOrderWindows(
                List.of(),
                List.of(order(2000000.0)),
                List.of(order(3000000.0)),
                List.of(order(4000000.0))
        );
        when(businessEventRepository.countAffectedUsersByEventTypeBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.countByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0.0);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getAffectedUsers()).isZero();
        assertThat(result.getExpectedRevenue()).isZero();
        assertThat(result.getRevenueLoss()).isZero();
        assertThat(result.getLostOrders()).isZero();
    }

    @Test
    void doesNotReportLossWhenActualRevenueMeetsBaseline() {
        Incident incident = incident("INC-0005", "/api/customers/me/checkout");
        stubIncident(incident);
        stubOrderWindows(
                List.of(order(1000000.0)),
                List.of(order(500000.0)),
                List.of(),
                List.of()
        );
        when(businessEventRepository.countAffectedUsersByEventTypeBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.countByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0L);
        when(businessEventRepository.sumAmountByEventTypeAndCreatedAtBetween(
                eq(BusinessEventType.CHECKOUT_START), any(), any()
        )).thenReturn(0.0);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getRevenueLoss()).isZero();
        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.LOW);
    }

    @Test
    void preservesMomoDemoImpact() {
        Incident incident = incident("INC-DEMO", "/payments/momo/ipn");
        stubIncident(incident);

        ManagementIncidentImpactDto result = service.calculateAndSaveImpact(incident.getId());

        assertThat(result.getAffectedUsers()).isEqualTo(4000);
        assertThat(result.getLostOrders()).isEqualTo(200);
        assertThat(result.getRevenueLoss()).isEqualTo(100000000.0);
        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
    }

    private void stubIncident(Incident incident) {
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
    }

    @SafeVarargs
    private void stubOrderWindows(List<Order>... windows) {
        when(orderRepository.findSuccessfulOrdersBetween(any(), any(), eq(OrderStatus.COMPLETED)))
                .thenReturn(windows[0], windows[1], windows[2], windows[3]);
    }

    private Incident incident(String code, String apiPath) {
        Instant now = Instant.parse("2026-06-28T12:00:00Z");
        return Incident.builder()
                .id(UUID.randomUUID())
                .code(code)
                .apiPath(apiPath)
                .httpMethod("POST")
                .status(IncidentStatus.RESOLVED)
                .severity(IncidentSeverity.LOW)
                .firstOccurredAt(now.minus(30, ChronoUnit.MINUTES))
                .occurredAt(now.minus(30, ChronoUnit.MINUTES))
                .createdAt(now.minus(30, ChronoUnit.MINUTES))
                .resolvedAt(now)
                .build();
    }

    private Order order(double finalPrice) {
        Order order = new Order();
        order.setFinalPrice(finalPrice);
        return order;
    }
}
