package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.IncidentCreateRequest;
import hcmute.edu.zentech.dto.request.IncidentUpdateRequest;
import hcmute.edu.zentech.dto.response.AiAnalysisResponseDto;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.IncidentMapper;
import hcmute.edu.zentech.mapper.TicketMapper;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private final AccountUserRepository accountUserRepository;
    private final IncidentMapper incidentMapper;
    private final TicketMapper ticketMapper;
    private final LokiService lokiService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActivityLogService activityLogService;

    @Value("${app.ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;


    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    private synchronized String generateIncidentCode() {
        long count = incidentRepository.countAllIncidents();
        return String.format("INC-%04d", count + 1);
    }

    public Page<IncidentResponseDto> getIncidents(
            IncidentStatus status,
            IncidentSeverity severity,
            String assignee,
            Instant startDate,
            Instant endDate,
            String search,
            Pageable pageable
    ) {
        Page<Incident> incidents = incidentRepository.searchIncidents(
                status, severity, assignee, startDate, endDate, search, pageable
        );
        return incidents.map(incident -> {
            String ticketCode = ticketRepository.findByIncidentId(incident.getId())
                    .map(Ticket::getCode)
                    .orElse(null);
            String firstEmail = incident.getUser() != null ? incident.getUser().getEmail() : null;
            List<String> affectedEmails = getAffectedUserEmails(incident.getId(), firstEmail);
            List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(incident);
            return incidentMapper.toResponseDto(incident, (AiAnalysisResponseDto) null, ticketCode, affectedEmails, occurrences);
        });
    }

    public IncidentResponseDto getIncidentById(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + id));
        String ticketCode = ticketRepository.findByIncidentId(incident.getId())
                .map(Ticket::getCode)
                .orElse(null);
        String firstEmail = incident.getUser() != null ? incident.getUser().getEmail() : null;
        List<String> affectedEmails = getAffectedUserEmails(incident.getId(), firstEmail);
        List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(incident);
        return incidentMapper.toResponseDto(incident, (AiAnalysisResponseDto) null, ticketCode, affectedEmails, occurrences);
    }

    private String normalizeErrorMessage(String errorMsg) {
        if (errorMsg == null) return "";
        // Replace UUIDs
        String result = errorMsg.replaceAll("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b", "{id}");
        // Replace numeric IDs
        result = result.replaceAll("\\b\\d+\\b", "{id}");
        return result.trim();
    }

    private Optional<Incident> findDuplicateIncident(String apiPath, String httpMethod, String errorMessage) {
        if (apiPath == null || httpMethod == null || errorMessage == null) {
            return Optional.empty();
        }
        List<IncidentStatus> activeStatuses = List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING);
        List<Incident> candidates = incidentRepository.findByApiPathAndHttpMethodAndStatusInOrderByCreatedAtDesc(
                apiPath, httpMethod, activeStatuses
        );
        String targetNormalized = normalizeErrorMessage(errorMessage);
        for (Incident cand : candidates) {
            if (targetNormalized.equalsIgnoreCase(normalizeErrorMessage(cand.getErrorMessage()))) {
                return Optional.of(cand);
            }
        }
        return Optional.empty();
    }

    private List<String> getAffectedUserEmails(UUID incidentId, String firstOccurrenceUserEmail) {
        Set<String> emails = new LinkedHashSet<>();
        if (firstOccurrenceUserEmail != null) {
            boolean isCustomer = accountUserRepository.findByEmailIgnoreCase(firstOccurrenceUserEmail.trim())
                    .map(u -> u.getRole() == Role.CUSTOMER)
                    .orElse(false);
            if (isCustomer) {
                emails.add(firstOccurrenceUserEmail);
            }
        }
        try {
            java.util.List<String> systemUserEmails = activityLogRepository.findUserEmailsByTargetTypeAndTargetIdAndSystemArea("Incident", incidentId.toString());
            emails.addAll(systemUserEmails);
        } catch (Exception e) {
            log.error("Failed to fetch affected user emails for incident {}: {}", incidentId, e.getMessage());
        }
        return new ArrayList<>(emails);
    }

    private List<IncidentResponseDto.OccurrenceDto> getIncidentOccurrences(Incident incident) {
        List<IncidentResponseDto.OccurrenceDto> occurrences = new ArrayList<>();
        if (incident == null) return occurrences;
        
        java.util.Set<String> seenTraceIds = new java.util.HashSet<>();
        
        // Add first occurrence
        if (incident.getTraceId() != null) {
            occurrences.add(IncidentResponseDto.OccurrenceDto.builder()
                    .traceId(incident.getTraceId())
                    .occurredAt(incident.getCreatedAt() != null ? incident.getCreatedAt() : incident.getOccurredAt())
                    .userEmail(incident.getUser() != null ? incident.getUser().getEmail() : null)
                    .build());
            seenTraceIds.add(incident.getTraceId());
        }
                
        // Add subsequent occurrences — only from CUSTOMER users to exclude admin/staff actions
        try {
            List<ActivityLog> logs = activityLogRepository.findByTargetTypeAndTargetId("Incident", incident.getId().toString());
            for (ActivityLog logItem : logs) {
                if (logItem.getArea() == ActivityArea.SYSTEM && logItem.getTraceId() != null) {
                    // Skip logs from non-customer users (admin, staff, etc.)
                    if (logItem.getUser() != null && logItem.getUser().getRole() != null
                            && logItem.getUser().getRole() != Role.CUSTOMER) {
                        continue;
                    }
                    String tId = logItem.getTraceId();
                    if (!seenTraceIds.contains(tId)) {
                        seenTraceIds.add(tId);
                        occurrences.add(IncidentResponseDto.OccurrenceDto.builder()
                                .traceId(tId)
                                .occurredAt(logItem.getCreatedAt())
                                .userEmail(logItem.getUser() != null ? logItem.getUser().getEmail() : null)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch occurrences for incident: {}", e.getMessage());
        }
        
        occurrences.sort((o1, o2) -> o2.getOccurredAt().compareTo(o1.getOccurredAt()));
        return occurrences;
    }

    private java.util.List<String> getAffectedUserEmailsForTicket(Ticket ticket) {
        java.util.Set<String> emails = new java.util.LinkedHashSet<>();
        if (ticket.getCreatedBy() != null && ticket.getCreatedBy().getEmail() != null) {
            emails.add(ticket.getCreatedBy().getEmail());
        }
        if (ticket.getIncident() != null) {
            Incident incident = ticket.getIncident();
            if (incident.getUser() != null && incident.getUser().getEmail() != null) {
                emails.add(incident.getUser().getEmail());
            }
            emails.addAll(getAffectedUserEmails(incident.getId(), null));
        }
        return new java.util.ArrayList<>(emails);
    }

    @Transactional
    public IncidentResponseDto createIncident(IncidentCreateRequest request) {
        AccountUser user = null;
        if (request.getUserId() != null) {
            user = accountUserRepository.findById(request.getUserId()).orElse(null);
        }

        // Deduplication check
        Optional<Incident> duplicate = findDuplicateIncident(request.getApiPath(), request.getHttpMethod(), request.getErrorMessage());
        if (duplicate.isPresent()) {
            Incident dup = duplicate.get();
            dup.setOccurredAt(Instant.now());
            Incident saved = incidentRepository.save(dup);
            
            boolean mdcSet = false;
            if (request.getTraceId() != null && org.slf4j.MDC.get("traceId") == null) {
                org.slf4j.MDC.put("traceId", request.getTraceId());
                mdcSet = true;
            }
            try {
                activityLogService.log(
                        user != null ? user.getId() : null,
                        ActivityArea.SYSTEM,
                        "INCIDENT",
                        ActivityAction.UPDATE_INCIDENT,
                        ActivitySeverity.INFO,
                        "Incident",
                        saved.getId().toString(),
                        saved.getCode(),
                        "Sự cố trùng lặp tiếp tục xảy ra: " + saved.getCode(),
                        null
                );
            } finally {
                if (mdcSet) {
                    org.slf4j.MDC.remove("traceId");
                }
            }

            String ticketCode = ticketRepository.findByIncidentId(saved.getId())
                    .map(Ticket::getCode)
                    .orElse(null);
            String firstEmail = saved.getUser() != null ? saved.getUser().getEmail() : null;
            List<String> affectedEmails = getAffectedUserEmails(saved.getId(), firstEmail);
            List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(saved);
            IncidentResponseDto response = incidentMapper.toResponseDto(saved, (AiAnalysisResponseDto) null, ticketCode, affectedEmails, occurrences);
            try {
                messagingTemplate.convertAndSend("/topic/admin.incidents", response);
            } catch (Exception e) {
                log.error("Failed to send websocket notification for duplicate incident: {}", saved.getCode(), e);
            }
            return response;
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
                .images(request.getImages())
                .occurredAt(Instant.now())
                .build();

        Incident saved = incidentRepository.save(incident);
        
        activityLogService.log(
                user != null ? user.getId() : null,
                ActivityArea.ADMIN,
                "INCIDENT",
                ActivityAction.CREATE_INCIDENT,
                ActivitySeverity.IMPORTANT,
                "Incident",
                saved.getId().toString(),
                saved.getCode(),
                "Tạo sự cố mới: " + saved.getCode(),
                null
        );

        String firstEmail = saved.getUser() != null ? saved.getUser().getEmail() : null;
        List<String> affectedEmails = getAffectedUserEmails(saved.getId(), firstEmail);
        List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(saved);
        IncidentResponseDto response = incidentMapper.toResponseDto(saved, (AiAnalysisResponseDto) null, null, affectedEmails, occurrences);
        try {
            messagingTemplate.convertAndSend("/topic/admin.incidents", response);
            messagingTemplate.convertAndSend("/topic/admin.incidents.new", response);
        } catch (Exception e) {
            log.error("Failed to send websocket notification for incident: {}", saved.getCode(), e);
        }
        return response;
    }

    @Transactional
    public IncidentResponseDto updateIncident(UUID id, IncidentUpdateRequest request) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + id));

        IncidentStatus oldStatus = incident.getStatus();
        String oldAssignee = incident.getAssignee();

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

                        // Ghi log hoạt động đồng bộ ticket
                        activityLogService.log(
                                null,
                                ActivityArea.SYSTEM,
                                "TICKET",
                                ActivityAction.UPDATE_TICKET_STATUS,
                                ActivitySeverity.INFO,
                                "Ticket",
                                ticket.getId().toString(),
                                ticket.getCode(),
                                "Hệ thống tự động đóng Ticket liên kết: " + ticket.getCode() + " do đóng Incident",
                                null
                        );
                    }
                });
            } else {
                incident.setResolvedAt(null);

                // Cải tiến 1: Đồng bộ Reopen
                if (oldStatus == IncidentStatus.RESOLVED) {
                    ticketRepository.findByIncidentId(incident.getId()).ifPresent(ticket -> {
                        if (ticket.getStatus() == TicketStatus.RESOLVED) {
                            ticket.setStatus(TicketStatus.IN_PROGRESS);
                            ticket.setResolvedAt(null);
                            ticketRepository.save(ticket);
                            log.info("Automatically reopened linked ticket: {}", ticket.getCode());

                            // Ghi log hoạt động đồng bộ ticket
                            activityLogService.log(
                                    null,
                                    ActivityArea.SYSTEM,
                                    "TICKET",
                                    ActivityAction.UPDATE_TICKET_STATUS,
                                    ActivitySeverity.INFO,
                                    "Ticket",
                                    ticket.getId().toString(),
                                    ticket.getCode(),
                                    "Hệ thống tự động mở lại Ticket liên kết: " + ticket.getCode() + " do mở lại Incident",
                                    null
                            );

                            // Gửi websocket cập nhật ticket
                            try {
                                TicketResponseDto tckDto = ticketMapper.toResponseDto(ticket, getAffectedUserEmailsForTicket(ticket));
                                messagingTemplate.convertAndSend("/topic/admin.tickets", tckDto);
                            } catch (Exception e) {
                                log.error("Failed to send ticket update websocket notification", e);
                            }
                        }
                    });
                }
            }
        }
        if (request.getSeverity() != null) {
            incident.setSeverity(request.getSeverity());
        }
        if (request.getAssignee() != null) {
            String newAssigneeEmail = request.getAssignee().trim();
            String resolvedAssignee = newAssigneeEmail.isEmpty() ? null : newAssigneeEmail;
            incident.setAssignee(resolvedAssignee);

            // Đồng bộ sang Ticket liên kết nếu có
            ticketRepository.findByIncidentId(incident.getId()).ifPresent(ticket -> {
                AccountUser ticketAssignee = null;
                if (resolvedAssignee != null && !resolvedAssignee.equalsIgnoreCase("UNASSIGNED")) {
                    ticketAssignee = accountUserRepository.findByEmailIgnoreCase(resolvedAssignee).orElse(null);
                }
                if (!java.util.Objects.equals(ticket.getAssignee(), ticketAssignee)) {
                    ticket.setAssignee(ticketAssignee);
                    ticketRepository.save(ticket);
                    log.info("Synchronized assignee from Incident {} to Ticket {}", incident.getCode(), ticket.getCode());

                    // Ghi log hoạt động đồng bộ phân công ticket
                    activityLogService.log(
                            null,
                            ActivityArea.SYSTEM,
                            "TICKET",
                            ActivityAction.ASSIGN_TICKET,
                            ActivitySeverity.INFO,
                            "Ticket",
                            ticket.getId().toString(),
                            ticket.getCode(),
                            "Hệ thống tự động phân công Ticket liên kết: " + ticket.getCode() + " cho " + (resolvedAssignee == null ? "chưa phân công" : resolvedAssignee),
                            null
                    );

                    // Gửi websocket cập nhật ticket
                    try {
                        TicketResponseDto tckDto = ticketMapper.toResponseDto(ticket, getAffectedUserEmailsForTicket(ticket));
                        messagingTemplate.convertAndSend("/topic/admin.tickets", tckDto);
                    } catch (Exception e) {
                        log.error("Failed to send ticket update websocket notification", e);
                    }
                }
            });
        }

        Incident saved = incidentRepository.save(incident);

        // Ghi nhận nhật ký hoạt động cập nhật incident
        if (request.getStatus() != null && request.getStatus() != oldStatus) {
            ActivityAction act = request.getStatus() == IncidentStatus.RESOLVED ? ActivityAction.RESOLVE_INCIDENT : ActivityAction.UPDATE_INCIDENT;
            activityLogService.log(
                    null,
                    ActivityArea.ADMIN,
                    "INCIDENT",
                    act,
                    ActivitySeverity.INFO,
                    "Incident",
                    saved.getId().toString(),
                    saved.getCode(),
                    "Cập nhật trạng thái sự cố " + saved.getCode() + " thành " + request.getStatus(),
                    null
            );
        }
        if (request.getAssignee() != null) {
            String newAssigneeEmail = request.getAssignee().trim();
            String resolvedAssignee = newAssigneeEmail.isEmpty() ? null : newAssigneeEmail;
            if (!java.util.Objects.equals(oldAssignee, resolvedAssignee)) {
                activityLogService.log(
                        null,
                        ActivityArea.ADMIN,
                        "INCIDENT",
                        ActivityAction.UPDATE_INCIDENT,
                        ActivitySeverity.INFO,
                        "Incident",
                        saved.getId().toString(),
                        saved.getCode(),
                        "Phân công sự cố " + saved.getCode() + " cho " + (resolvedAssignee == null ? "chưa phân công" : resolvedAssignee),
                        null
                );
            }
        }

        String ticketCode = ticketRepository.findByIncidentId(saved.getId())
                .map(Ticket::getCode)
                .orElse(null);
        String firstEmail = saved.getUser() != null ? saved.getUser().getEmail() : null;
        List<String> affectedEmails = getAffectedUserEmails(saved.getId(), firstEmail);
        List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(saved);
        IncidentResponseDto response = incidentMapper.toResponseDto(saved, (AiAnalysisResponseDto) null, ticketCode, affectedEmails, occurrences);
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

        String errMsg = throwable != null ? throwable.getMessage() : "Unexpected system error";

        // Deduplication check
        Optional<Incident> duplicate = findDuplicateIncident(apiPath, httpMethod, errMsg);
        if (duplicate.isPresent()) {
            Incident dup = duplicate.get();
            dup.setOccurredAt(Instant.now());
            Incident saved = incidentRepository.save(dup);

            activityLogService.log(
                    user != null ? user.getId() : null,
                    ActivityArea.SYSTEM,
                    "INCIDENT",
                    ActivityAction.UPDATE_INCIDENT,
                    ActivitySeverity.INFO,
                    "Incident",
                    saved.getId().toString(),
                    saved.getCode(),
                    "Sự cố trùng lặp tiếp tục xảy ra: " + saved.getCode(),
                    null
            );

            try {
                String ticketCode = ticketRepository.findByIncidentId(saved.getId())
                        .map(Ticket::getCode)
                        .orElse(null);
                String firstEmail = saved.getUser() != null ? saved.getUser().getEmail() : null;
                List<String> affectedEmails = getAffectedUserEmails(saved.getId(), firstEmail);
                List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(saved);
                IncidentResponseDto response = incidentMapper.toResponseDto(saved, (AiAnalysisResponseDto) null, ticketCode, affectedEmails, occurrences);
                messagingTemplate.convertAndSend("/topic/admin.incidents", response);
            } catch (Exception e) {
                log.error("Failed to send websocket notification for duplicate incident: {}", saved.getCode(), e);
            }
            return saved;
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
                .errorMessage(errMsg)
                .stackTrace(getStackTraceAsString(throwable))
                .severity(statusCode >= 500 ? IncidentSeverity.CRITICAL : IncidentSeverity.MEDIUM)
                .status(IncidentStatus.OPEN)
                .occurredAt(Instant.now())
                .build();

        log.info("Automatically creating incident {} for traceId: {}", code, traceId);
        Incident saved = incidentRepository.save(incident);

        activityLogService.log(
                user != null ? user.getId() : null,
                ActivityArea.SYSTEM,
                "INCIDENT",
                ActivityAction.CREATE_INCIDENT,
                statusCode >= 500 ? ActivitySeverity.CRITICAL : ActivitySeverity.WARNING,
                "Incident",
                saved.getId().toString(),
                saved.getCode(),
                "Hệ thống tự động ghi nhận sự cố mới: " + saved.getCode(),
                null
        );

        try {
            String firstEmail = saved.getUser() != null ? saved.getUser().getEmail() : null;
            List<String> affectedEmails = getAffectedUserEmails(saved.getId(), firstEmail);
            List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(saved);
            IncidentResponseDto response = incidentMapper.toResponseDto(saved, (AiAnalysisResponseDto) null, null, affectedEmails, occurrences);
            messagingTemplate.convertAndSend("/topic/admin.incidents", response);
            messagingTemplate.convertAndSend("/topic/admin.incidents.new", response);
        } catch (Exception e) {
            log.error("Failed to send websocket notification for automatically created incident: {}", saved.getCode(), e);
        }
        return saved;
    }

    public AiAnalysisResponseDto analyzeIncident(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + incidentId));

        // 1. Lấy logs liên quan từ Loki qua LokiService cho TẤT CẢ các traceId (tất cả phiên lỗi)
        List<IncidentResponseDto.OccurrenceDto> occurrences = getIncidentOccurrences(incident);
        List<Map<String, Object>> allLokiLogs = new ArrayList<>();
        java.util.Set<String> processedTraceIds = new java.util.HashSet<>();
        for (IncidentResponseDto.OccurrenceDto occ : occurrences) {
            if (occ.getTraceId() != null && !processedTraceIds.contains(occ.getTraceId())) {
                processedTraceIds.add(occ.getTraceId());
                try {
                    List<Map<String, Object>> logs = lokiService.queryLogs("ALL", "", occ.getTraceId(), 50);
                    if (logs != null) {
                        allLokiLogs.addAll(logs);
                    }
                } catch (Exception e) {
                    log.error("Failed to query Loki logs for traceId {}: {}", occ.getTraceId(), e.getMessage());
                }
            }
        }

        // 2. Lấy activity logs của TẤT CẢ các khách hàng bị ảnh hưởng trước thời điểm lỗi
        String firstEmail = incident.getUser() != null ? incident.getUser().getEmail() : null;
        List<String> affectedEmails = getAffectedUserEmails(incident.getId(), firstEmail);
        List<ActivityLog> allActivityLogs = new ArrayList<>();
        for (String email : affectedEmails) {
            if (email != null) {
                try {
                    accountUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                        List<ActivityLog> userLogs = activityLogRepository.findByUserOrderByCreatedAtDesc(user);
                        if (userLogs != null) {
                            allActivityLogs.addAll(userLogs.stream().limit(10).collect(Collectors.toList()));
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to fetch activity logs for user {}: {}", email, e.getMessage());
                }
            }
        }

        // 3. Định dạng logs và activity logs để gửi sang FastAPI
        List<String> logLines = allLokiLogs.stream()
                .map(logMap -> String.format("[%s] [%s] %s - %s",
                        logMap.getOrDefault("timestamp", ""),
                        logMap.getOrDefault("level", ""),
                        logMap.getOrDefault("category", ""),
                        logMap.getOrDefault("message", "")))
                .collect(Collectors.toList());

        List<Map<String, Object>> activityLogList = allActivityLogs.stream()
                .map(act -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("user_email", act.getUser() != null ? act.getUser().getEmail() : "SYSTEM");
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
        log.info("Calling ZenTech-AI analysis URL (aggregated, on-the-fly): {}", analyzeUrl);

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(analyzeUrl, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                IncidentSeverity severitySuggestion = IncidentSeverity.LOW;
                String sevSuggestion = (String) body.get("severity_suggestion");
                if (sevSuggestion != null) {
                    try {
                        severitySuggestion = IncidentSeverity.valueOf(sevSuggestion.toUpperCase());
                    } catch (Exception e) {
                        severitySuggestion = IncidentSeverity.LOW;
                    }
                }

                double confidenceScore = 0.85;
                Object scoreObj = body.get("confidence_score");
                if (scoreObj instanceof Number) {
                    confidenceScore = ((Number) scoreObj).doubleValue();
                }

                return AiAnalysisResponseDto.builder()
                        .incidentId(incidentId)
                        .summary((String) body.get("summary"))
                        .rootCause((String) body.get("root_cause"))
                        .severitySuggestion(severitySuggestion)
                        .solutionSuggestion((String) body.get("solution_suggestion"))
                        .confidenceScore(confidenceScore)
                        .createdAt(Instant.now())
                        .build();
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
