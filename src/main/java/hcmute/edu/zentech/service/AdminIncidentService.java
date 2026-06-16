package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.IncidentCreateRequest;
import hcmute.edu.zentech.dto.request.IncidentUpdateRequest;
import hcmute.edu.zentech.dto.response.AiAnalysisResponseDto;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.IncidentMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminIncidentService {

    private final IncidentRepository incidentRepository;
    private final TicketRepository ticketRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AccountUserRepository accountUserRepository;
    private final IncidentMapper incidentMapper;
    private final LokiService lokiService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;


    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    private synchronized String generateIncidentCode() {
        long count = incidentRepository.countAllIncidents();
        return String.format("INC-%04d", count + 1);
    }

    public List<IncidentResponseDto> getIncidents(IncidentStatus status) {
        List<Incident> incidents;
        if (status == null) {
            incidents = incidentRepository.findAll();
            // Sort by createdAt desc
            incidents.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        } else {
            incidents = incidentRepository.findByStatusOrderByCreatedAtDesc(status);
        }

        return incidents.stream()
                .map(incident -> {
                    AiAnalysis analysis = aiAnalysisRepository.findByIncidentId(incident.getId()).orElse(null);
                    String ticketCode = ticketRepository.findByIncidentId(incident.getId())
                            .map(Ticket::getCode)
                            .orElse(null);
                    return incidentMapper.toResponseDto(incident, analysis, ticketCode);
                })
                .collect(Collectors.toList());
    }

    public IncidentResponseDto getIncidentById(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + id));
        AiAnalysis analysis = aiAnalysisRepository.findByIncidentId(incident.getId()).orElse(null);
        String ticketCode = ticketRepository.findByIncidentId(incident.getId())
                .map(Ticket::getCode)
                .orElse(null);
        return incidentMapper.toResponseDto(incident, analysis, ticketCode);
    }

    @Transactional
    public IncidentResponseDto createIncident(IncidentCreateRequest request) {
        AccountUser user = null;
        if (request.getUserId() != null) {
            user = accountUserRepository.findById(request.getUserId()).orElse(null);
        }

        Incident incident = Incident.builder()
                .code(generateIncidentCode())
                .traceId(request.getTraceId())
                .user(user)
                .serviceName(request.getServiceName())
                .apiPath(request.getApiPath())
                .httpMethod(request.getHttpMethod())
                .statusCode(request.getStatusCode())
                .errorMessage(request.getErrorMessage())
                .stackTrace(request.getStackTrace())
                .severity(request.getSeverity())
                .status(request.getStatus())
                .assignee(request.getAssignee())
                .occurredAt(Instant.now())
                .build();

        Incident saved = incidentRepository.save(incident);
        IncidentResponseDto response = incidentMapper.toResponseDto(saved, null, null);
        try {
            messagingTemplate.convertAndSend("/topic/admin.incidents", response);
        } catch (Exception e) {
            log.error("Failed to send websocket notification for incident: {}", saved.getCode(), e);
        }
        return response;
    }

    @Transactional
    public IncidentResponseDto updateIncident(UUID id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + id));

        if (request.getStatus() != null) {
            incident.setStatus(request.getStatus());
            if (request.getStatus() == IncidentStatus.RESOLVED) {
                incident.setResolvedAt(Instant.now());
                
                // Đồng bộ cập nhật trạng thái RESOLVED cho Ticket liên kết nếu có
                ticketRepository.findByIncidentId(incident.getId()).ifPresent(ticket -> {
                    if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
                        ticket.setStatus(TicketStatus.RESOLVED);
                        ticket.setResolvedAt(Instant.now());
                        ticketRepository.save(ticket);
                        log.info("Automatically resolved linked ticket: {}", ticket.getCode());
                    }
                });
            } else {
                incident.setResolvedAt(null);
            }
        }
        if (request.getSeverity() != null) {
            incident.setSeverity(request.getSeverity());
        }
        if (request.getAssignee() != null) {
            incident.setAssignee(request.getAssignee());
        }

        Incident saved = incidentRepository.save(incident);
        AiAnalysis analysis = aiAnalysisRepository.findByIncidentId(saved.getId()).orElse(null);
        String ticketCode = ticketRepository.findByIncidentId(saved.getId())
                .map(Ticket::getCode)
                .orElse(null);
        IncidentResponseDto response = incidentMapper.toResponseDto(saved, analysis, ticketCode);
        try {
            messagingTemplate.convertAndSend("/topic/admin.incidents", response);
        } catch (Exception e) {
            log.error("Failed to send websocket notification for incident: {}", saved.getCode(), e);
        }
        return response;
    }

    @Transactional
    public Incident createIncidentFromException(Throwable throwable, String traceId, String apiPath, String httpMethod, int statusCode, String serviceName, UUID userId) {
        if (traceId != null && !traceId.trim().isEmpty()) {
            Optional<Incident> existing = incidentRepository.findByTraceId(traceId.trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        AccountUser user = null;
        if (userId != null) {
            user = accountUserRepository.findById(userId).orElse(null);
        }

        String code = generateIncidentCode();
        Incident incident = Incident.builder()
                .code(code)
                .traceId(traceId)
                .user(user)
                .serviceName(serviceName)
                .apiPath(apiPath)
                .httpMethod(httpMethod)
                .statusCode(statusCode)
                .errorMessage(throwable != null ? throwable.getMessage() : "Unexpected system error")
                .stackTrace(getStackTraceAsString(throwable))
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.OPEN)
                .occurredAt(Instant.now())
                .build();

        log.info("Automatically creating incident {} for traceId: {}", code, traceId);
        Incident saved = incidentRepository.save(incident);
        try {
            IncidentResponseDto response = incidentMapper.toResponseDto(saved, null, null);
            messagingTemplate.convertAndSend("/topic/admin.incidents", response);
        } catch (Exception e) {
            log.error("Failed to send websocket notification for automatically created incident: {}", saved.getCode(), e);
        }
        return saved;
    }

    public AiAnalysisResponseDto analyzeIncident(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + incidentId));

        // 1. Lấy logs liên quan từ Loki qua LokiService
        List<Map<String, Object>> lokiLogs = lokiService.queryLogs("ALL", "", incident.getTraceId(), 100);

        // 2. Lấy activity logs của user liên quan trước thời điểm lỗi
        List<ActivityLog> activityLogs = new ArrayList<>();
        if (incident.getUser() != null) {
            activityLogs = activityLogRepository.findByUserOrderByCreatedAtDesc(incident.getUser());
        }

        // 3. Định dạng logs và activity logs để gửi sang FastAPI
        List<String> logLines = lokiLogs.stream()
                .map(logMap -> String.format("[%s] [%s] %s - %s",
                        logMap.getOrDefault("timestamp", ""),
                        logMap.getOrDefault("level", ""),
                        logMap.getOrDefault("category", ""),
                        logMap.getOrDefault("message", "")))
                .collect(Collectors.toList());

        List<Map<String, Object>> activityLogList = activityLogs.stream()
                .limit(20) // giới hạn 20 dòng để tránh payload quá lớn
                .map(act -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("action", act.getAction() != null ? act.getAction().toString() : "");
                    map.put("area", act.getArea() != null ? act.getArea().toString() : "");
                    map.put("severity", act.getSeverity() != null ? act.getSeverity().toString() : "");
                    map.put("summary", act.getSummary() != null ? act.getSummary() : "");
                    map.put("description", act.getDescription() != null ? act.getDescription() : "");
                    map.put("timestamp", act.getCreatedAt() != null ? act.getCreatedAt().toString() : "");
                    return map;
                })
                .collect(Collectors.toList());

        // 4. Chuẩn bị payload gửi sang FastAPI
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> incInfo = new HashMap<>();
        incInfo.put("code", incident.getCode());
        incInfo.put("service_name", incident.getServiceName() != null ? incident.getServiceName() : "");
        incInfo.put("api_path", incident.getApiPath() != null ? incident.getApiPath() : "");
        incInfo.put("http_method", incident.getHttpMethod() != null ? incident.getHttpMethod() : "");
        incInfo.put("status_code", incident.getStatusCode() != null ? incident.getStatusCode() : 0);
        incInfo.put("error_message", incident.getErrorMessage() != null ? incident.getErrorMessage() : "");
        incInfo.put("stack_trace", incident.getStackTrace() != null ? incident.getStackTrace() : "");
        incInfo.put("severity", incident.getSeverity().toString());

        payload.put("incident", incInfo);
        payload.put("logs", logLines);
        payload.put("activity_logs", activityLogList);

        String analyzeUrl = aiBaseUrl + "/admin/incidents/analyze";
        log.info("Calling ZenTech-AI analysis URL: {}", analyzeUrl);

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(analyzeUrl, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                AiAnalysis analysis = aiAnalysisRepository.findByIncidentId(incidentId)
                        .orElse(new AiAnalysis());
                analysis.setIncident(incident);
                analysis.setSummary((String) body.get("summary"));
                analysis.setRootCause((String) body.get("root_cause"));

                String sevSuggestion = (String) body.get("severity_suggestion");
                if (sevSuggestion != null) {
                    try {
                        analysis.setSeveritySuggestion(IncidentSeverity.valueOf(sevSuggestion.toUpperCase()));
                    } catch (Exception e) {
                        analysis.setSeveritySuggestion(IncidentSeverity.LOW);
                    }
                }
                analysis.setSolutionSuggestion((String) body.get("solution_suggestion"));

                Object scoreObj = body.get("confidence_score");
                if (scoreObj instanceof Number) {
                    analysis.setConfidenceScore(((Number) scoreObj).doubleValue());
                } else {
                    analysis.setConfidenceScore(0.85);
                }

                AiAnalysis savedAnalysis = aiAnalysisRepository.save(analysis);
                return incidentMapper.toAiAnalysisDto(savedAnalysis);
            }
        } catch (Exception e) {
            log.error("AI analysis failed for incident {}: {}", incidentId, e.getMessage(), e);
            throw new RuntimeException("Dịch vụ phân tích AI không khả dụng: " + e.getMessage());
        }

        throw new RuntimeException("Phản hồi từ AI Service không hợp lệ.");
    }

    private String getStackTraceAsString(Throwable throwable) {
        if (throwable == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
