package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.response.AdminStatisticsResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.IncidentRepository;
import hcmute.edu.zentech.repository.TicketRepository;
import hcmute.edu.zentech.repository.projection.AccountSummaryProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceTest {
    @Mock private AdminLogService adminLogService;
    @Mock private IncidentRepository incidentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AccountUserRepository accountUserRepository;
    @Mock private R2StorageService r2StorageService;

    private AdminStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatisticsService(
                adminLogService,
                incidentRepository,
                ticketRepository,
                accountUserRepository,
                r2StorageService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "dashboardZoneId", "Asia/Ho_Chi_Minh");
        lenient().when(ticketRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
        lenient().when(incidentRepository.findByOccurredAtBetween(any(), any())).thenReturn(List.of());
    }

    @Test
    void aggregatesErrorsNormalizesApisAndDeduplicatesIncidentTrace() {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        when(adminLogService.getLogs(eq("ERROR"), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of(log("ERROR", "FRONTEND", "ZT-1", now,
                        "{\"eventType\":\"HttpRequestFailed\",\"method\":\"GET\","
                                + "\"apiPath\":\"/api/orders/123?view=full\",\"statusCode\":500,"
                                + "\"traceId\":\"ZT-1\"}")));
        when(adminLogService.getLogs(eq("WARN"), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of(log("WARN", "FRONTEND", "ZT-2", now,
                        "{\"eventType\":\"HttpRequestFailed\",\"method\":\"GET\","
                                + "\"apiPath\":\"/api/orders/456\",\"statusCode\":500,"
                                + "\"traceId\":\"ZT-2\"}")));
        when(incidentRepository.findByOccurredAtBetween(any(), any())).thenReturn(List.of(
                Incident.builder()
                        .traceId("ZT-1")
                        .apiPath("/api/orders/123")
                        .httpMethod("GET")
                        .statusCode(500)
                        .serviceName("BACKEND")
                        .occurredAt(now)
                        .build()
        ));

        AdminStatisticsResponse response = service.getStatistics("7D", null, null);

        assertThat(response.isLogsAvailable()).isTrue();
        assertThat(response.isPartialData()).isFalse();
        assertThat(response.getTotalErrors()).isEqualTo(1);
        assertThat(response.getIncidentsInPeriod()).isEqualTo(1);
        assertThat(response.getTopApis()).hasSize(1);
        assertThat(response.getTopApis().getFirst().getEndpoint()).isEqualTo("/api/orders/:id");
        assertThat(response.getTopApis().getFirst().getErrorCount()).isEqualTo(2);
    }

    @Test
    void splitsSaturatedLokiWindowsAndDeduplicatesBoundaryLogs() {
        Instant from = Instant.parse("2026-06-25T00:00:00Z");
        Instant to = from.plusSeconds(10);
        Map<String, Object> boundaryLog = log("ERROR", "BACKEND", "ZT-SPLIT", from.plusSeconds(2),
                "{\"eventType\":\"RuntimeError\",\"traceId\":\"ZT-SPLIT\"}");
        List<Map<String, Object>> saturated = IntStream.range(0, 5000)
                .mapToObj(index -> {
                    Map<String, Object> item = new HashMap<>(boundaryLog);
                    item.put("id", "SATURATED-" + index);
                    return item;
                })
                .toList();
        AtomicInteger errorRequests = new AtomicInteger();
        when(adminLogService.getLogs(eq("ERROR"), any(), any(), any(Integer.class), any(), any()))
                .thenAnswer(ignored -> errorRequests.getAndIncrement() == 0
                        ? saturated
                        : List.of(boundaryLog));
        when(adminLogService.getLogs(eq("WARN"), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of());

        AdminStatisticsResponse response = service.getStatistics("CUSTOM", from, to);

        assertThat(errorRequests.get()).isEqualTo(3);
        assertThat(response.isPartialData()).isFalse();
        assertThat(response.getTotalErrors()).isEqualTo(1);
    }
    @Test
    void computesTicketCohortRateAndEnrichesAffectedUser() {
        Instant now = Instant.now().minus(1, ChronoUnit.MINUTES);
        UUID userId = UUID.randomUUID();
        when(adminLogService.getLogs(eq("ERROR"), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of(log("ERROR", "FRONTEND", "ZT-3", now,
                        "{\"eventType\":\"RuntimeError\",\"traceId\":\"ZT-3\",\"userId\":\"" + userId
                                + "\",\"userEmail\":\"customer@example.com\",\"userRole\":\"CUSTOMER\"}")));
        when(adminLogService.getLogs(eq("WARN"), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of());
        when(ticketRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of(
                Ticket.builder().status(TicketStatus.RESOLVED).build(),
                Ticket.builder().status(TicketStatus.OPEN).build()
        ));
        AccountSummaryProjection projection = mock(AccountSummaryProjection.class);
        when(projection.getId()).thenReturn(userId);
        when(projection.getEmail()).thenReturn("customer@example.com");
        when(projection.getDisplayName()).thenReturn("Nguyễn Văn A");
        when(projection.getRole()).thenReturn(Role.CUSTOMER);
        when(projection.getImageUrl()).thenReturn("avatars/customer.png");
        when(accountUserRepository.findAccountSummariesByIds(List.of(userId))).thenReturn(List.of(projection));
        when(r2StorageService.getPresignedGetUrl("avatars/customer.png")).thenReturn("https://cdn/avatar.png");

        AdminStatisticsResponse response = service.getStatistics("7D", null, null);

        assertThat(response.getTicketsCreated()).isEqualTo(2);
        assertThat(response.getTicketsResolved()).isEqualTo(1);
        assertThat(response.getTicketResolutionRate()).isEqualTo(50);
        assertThat(response.getTopAffectedUsers().getFirst().getDisplayName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.getTopAffectedUsers().getFirst().getAvatarUrl()).isEqualTo("https://cdn/avatar.png");
    }

    @Test
    void rejectsCustomRangeLongerThanNinetyDays() {
        Instant to = Instant.now().minus(1, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> service.getStatistics("CUSTOM", to.minus(91, ChronoUnit.DAYS), to))
                .isInstanceOf(ResponseStatusException.class);
    }

    private Map<String, Object> log(
            String level,
            String category,
            String traceId,
            Instant timestamp,
            String context
    ) {
        Map<String, Object> log = new HashMap<>();
        log.put("level", level);
        log.put("category", category);
        log.put("traceId", traceId);
        log.put("timestamp", timestamp);
        log.put("details", "Stack: " + context);
        return log;
    }
}
