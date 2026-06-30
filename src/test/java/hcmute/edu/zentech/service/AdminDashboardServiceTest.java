package hcmute.edu.zentech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import hcmute.edu.zentech.dto.response.AdminDashboardResponse;
import hcmute.edu.zentech.model.Incident;
import hcmute.edu.zentech.monitoring.HostResourceMetricsProvider;
import hcmute.edu.zentech.model.IncidentSeverity;
import hcmute.edu.zentech.model.IncidentStatus;
import hcmute.edu.zentech.repository.IncidentRepository;
import hcmute.edu.zentech.repository.TicketRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private AdminLogService adminLogService;
    @Mock private HostResourceMetricsProvider hostResourceMetricsProvider;
    @Mock private PrometheusQueryService prometheusQueryService;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                incidentRepository,
                ticketRepository,
                adminLogService,
                new ObjectMapper(),
                hostResourceMetricsProvider,
                prometheusQueryService
        );
        ReflectionTestUtils.setField(service, "dashboardZoneId", "Asia/Ho_Chi_Minh");

    }

    @Test
    void marksDashboardCriticalAndPrioritizesCriticalIncident() {
        when(ticketRepository.findByStatusIn(any())).thenReturn(List.of());
        when(incidentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
        when(incidentRepository.findByResolvedAtBetween(any(), any())).thenReturn(List.of());
        when(adminLogService.getLogs(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of());

        Instant now = Instant.now();
        Incident critical = Incident.builder()
                .code("INC-0002")
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.OPEN)
                .errorMessage("Checkout failed")
                .createdAt(now.minus(3, ChronoUnit.HOURS))
                .firstOccurredAt(now.minus(3, ChronoUnit.HOURS))
                .build();
        Incident medium = Incident.builder()
                .code("INC-0001")
                .severity(IncidentSeverity.MEDIUM)
                .status(IncidentStatus.INVESTIGATING)
                .errorMessage("Search degraded")
                .assignee("admin@zentech.local")
                .createdAt(now.minus(1, ChronoUnit.DAYS))
                .firstOccurredAt(now.minus(1, ChronoUnit.DAYS))
                .build();
        when(incidentRepository.findByStatusNot(IncidentStatus.RESOLVED))
                .thenReturn(List.of(medium, critical));

        AdminDashboardResponse response = service.getDashboard("7D", null, null);

        assertThat(response.getHealth()).isEqualTo("CRITICAL");
        assertThat(response.getMetrics().getOpenIncidents()).isEqualTo(2);
        assertThat(response.getMetrics().getHighPriorityIncidents()).isEqualTo(1);
        assertThat(response.getPriorityIncidents().getFirst().getCode()).isEqualTo("INC-0002");
    }

    @Test
    void prioritizesServicesWithErrorsAndKeepsTheActualLatestLevel() {
        when(ticketRepository.findByStatusIn(any())).thenReturn(List.of());
        when(incidentRepository.findByStatusNot(IncidentStatus.RESOLVED)).thenReturn(List.of());
        when(incidentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
        when(incidentRepository.findByResolvedAtBetween(any(), any())).thenReturn(List.of());

        Instant now = Instant.now();
        when(adminLogService.getLogs(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of(
                        log("WARN", "FRONTEND", "Frontend warning 1", now.minus(5, ChronoUnit.MINUTES)),
                        log("WARN", "FRONTEND", "Frontend warning 2", now.minus(4, ChronoUnit.MINUTES)),
                        log("WARN", "FRONTEND", "Frontend warning 3", now.minus(3, ChronoUnit.MINUTES)),
                        log("ERROR", "BACKEND", "Backend error", now.minus(2, ChronoUnit.MINUTES)),
                        log("WARN", "BACKEND", "Backend recovered with warning", now.minus(1, ChronoUnit.MINUTES))
                ));

        AdminDashboardResponse response = service.getDashboard("7D", null, null);

        assertThat(response.getTopServices()).extracting(AdminDashboardResponse.ServiceErrorItem::getService)
                .containsExactly("BACKEND", "FRONTEND");
        assertThat(response.getTopServices().getFirst().getErrorOccurrences()).isEqualTo(1);
        assertThat(response.getTopServices().getFirst().getLatestIssueLevel()).isEqualTo("WARN");
        assertThat(response.getTopServices().getFirst().getLatestIssueTitle())
                .isEqualTo("Backend recovered with warning");
    }
    @Test
    void keepsNewAndResolvedIncidentCountsSeparateWhenOldIncidentIsResolvedToday() {
        when(ticketRepository.findByStatusIn(any())).thenReturn(List.of());
        when(incidentRepository.findByStatusNot(IncidentStatus.RESOLVED)).thenReturn(List.of());
        when(adminLogService.getLogs(any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(List.of());

        Instant now = Instant.now();
        Incident resolvedOldIncident = Incident.builder()
                .code("INC-OLD")
                .severity(IncidentSeverity.MEDIUM)
                .status(IncidentStatus.RESOLVED)
                .createdAt(now.minus(3, ChronoUnit.DAYS))
                .firstOccurredAt(now.minus(3, ChronoUnit.DAYS))
                .resolvedAt(now.minus(10, ChronoUnit.MINUTES))
                .build();

        when(incidentRepository.findByCreatedAtBetween(any(), any())).thenReturn(List.of());
        when(incidentRepository.findByResolvedAtBetween(any(), any())).thenReturn(List.of(resolvedOldIncident));

        AdminDashboardResponse response = service.getDashboard("TODAY", null, null);

        assertThat(response.getMetrics().getIncidentsResolvedInPeriod()).isEqualTo(1);
        assertThat(response.getMetrics().getIncidentsCreatedInPeriod()).isZero();
        assertThat(response.getMetrics().getIncidentResolutionRate()).isZero();
    }
    @Test
    void rejectsCustomRangeLongerThanNinetyDays() {
        Instant to = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant from = to.minus(91, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.getDashboard("CUSTOM", from, to))
                .isInstanceOf(ResponseStatusException.class);
    }
    private Map<String, Object> log(String level, String category, String message, Instant timestamp) {
        Map<String, Object> log = new HashMap<>();
        log.put("level", level);
        log.put("category", category);
        log.put("message", message);
        log.put("details", "");
        log.put("timestamp", timestamp);
        return log;
    }
}
