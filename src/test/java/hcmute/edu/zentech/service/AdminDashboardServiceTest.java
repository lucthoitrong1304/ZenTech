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
import java.util.List;

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
    void rejectsCustomRangeLongerThanNinetyDays() {
        Instant to = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant from = to.minus(91, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.getDashboard("CUSTOM", from, to))
                .isInstanceOf(ResponseStatusException.class);
    }
}
