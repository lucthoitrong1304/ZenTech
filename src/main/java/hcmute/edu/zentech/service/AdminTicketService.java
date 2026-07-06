package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.TicketCreateRequest;
import hcmute.edu.zentech.dto.response.TicketResponseDto;
import hcmute.edu.zentech.dto.response.CustomerTicketStatusResponse;
import hcmute.edu.zentech.dto.response.IncidentResponseDto;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.TicketMapper;
import hcmute.edu.zentech.mapper.IncidentMapper;
import hcmute.edu.zentech.model.*;
import hcmute.edu.zentech.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTicketService {

    private final TicketRepository ticketRepository;
    private final IncidentRepository incidentRepository;
    private final AccountUserRepository accountUserRepository;
    private final TicketMapper ticketMapper;
    private final IncidentMapper incidentMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActivityLogService activityLogService;
    private final TicketAudienceService ticketAudienceService;
    private final NotificationService notificationService;

    private synchronized String generateTicketCode() {
        long count = ticketRepository.countAllTickets();
        return String.format("TCK-%04d", count + 1);
    }

    public Page<TicketResponseDto> getTickets(
            TicketStatus status,
            TicketPriority priority,
            String assigneeEmail,
            Instant startDate,
            Instant endDate,
            String search,
            Pageable pageable
    ) {
        Page<Ticket> tickets = ticketRepository.searchTickets(
                status, priority, assigneeEmail, startDate, endDate, search, pageable
        );
        return tickets.map(ticket -> ticketMapper.toResponseDto(ticket, ticketAudienceService.getAffectedUserEmails(ticket)));
    }

    public TicketResponseDto getTicketById(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + id));
        return ticketMapper.toResponseDto(ticket, ticketAudienceService.getAffectedUserEmails(ticket));
    }

    private void notifyCustomerTicketStatusChanged() {
        try {
            messagingTemplate.convertAndSend("/topic/customer.tickets", java.util.Map.of("type", "TICKET_UPDATED"));
        } catch (Exception e) {
            log.error("Failed to send customer ticket status refresh notification", e);
        }
    }
    @Transactional
    public TicketResponseDto createTicket(TicketCreateRequest request, UUID createdById) {
        AccountUser createdBy = null;
        if (createdById != null) {
            createdBy = accountUserRepository.findById(createdById).orElse(null);
        }

        AccountUser assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = accountUserRepository.findById(request.getAssigneeId()).orElse(null);
        }

        Incident incident = null;
        if (request.getIncidentId() != null) {
            incident = incidentRepository.findById(request.getIncidentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố với ID: " + request.getIncidentId()));
        }

        // Kiểm tra xem Incident này đã có Ticket chưa
        if (incident != null) {
            ticketRepository.findByIncidentId(incident.getId()).ifPresent(existingTicket -> {
                throw new IllegalStateException("Sự cố này đã được liên kết với Ticket: " + existingTicket.getCode());
            });
            if (incident.getUser() != null) {
                createdBy = incident.getUser();
            }
            if (incident.getAssignee() != null && !incident.getAssignee().isBlank() && assignee == null) {
                assignee = accountUserRepository.findByEmailIgnoreCase(incident.getAssignee().trim()).orElse(null);
            }
        }

        String images = request.getImages();
        if ((images == null || images.isBlank()) && incident != null) {
            images = incident.getImages();
        }

        Ticket ticket = Ticket.builder()
                .code(generateTicketCode())
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(request.getStatus())
                .assignee(assignee)
                .createdBy(createdBy)
                .incident(incident)
                .images(images)
                .createdAt(Instant.now())
                .build();

        // Nếu tạo Ticket từ Incident và gán trạng thái khác RESOLVED/CLOSED, tự động chuyển Incident sang INVESTIGATING
        if (incident != null && request.getStatus() != TicketStatus.RESOLVED && request.getStatus() != TicketStatus.CLOSED) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
            if (assignee != null) {
                // Đồng bộ tên người phụ trách cho Incident
                incident.setAssignee(assignee.getEmail());
            }
            incidentRepository.save(incident);
            
            // Gửi websocket sự cố cập nhật
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, ticket.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        Ticket saved = ticketRepository.save(ticket);
        
        // Ghi log hoạt động tạo ticket mới
        activityLogService.log(
                createdById,
                ActivityArea.ADMIN,
                "TICKET",
                ActivityAction.CREATE_TICKET,
                ActivitySeverity.INFO,
                "Ticket",
                saved.getId().toString(),
                saved.getCode(),
                "Tạo Ticket hỗ trợ mới: " + saved.getCode(),
                null
        );

        // Nếu tạo Ticket từ Incident và gán trạng thái khác RESOLVED/CLOSED, tự động chuyển Incident sang INVESTIGATING
        if (incident != null && request.getStatus() != TicketStatus.RESOLVED && request.getStatus() != TicketStatus.CLOSED) {
            incident.setStatus(IncidentStatus.INVESTIGATING);
            if (assignee != null) {
                // Đồng bộ tên người phụ trách cho Incident
                incident.setAssignee(assignee.getEmail());
            }
            incidentRepository.save(incident);
            
            // Gửi websocket sự cố cập nhật
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, ticket.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved, ticketAudienceService.getAffectedUserEmails(saved));
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
            notifyCustomerTicketStatusChanged();
            
            // Gửi thông báo quả chuông cho các khách hàng bị ảnh hưởng
            String friendlyTitle = getFriendlyTicketTitle(saved.getTitle());
            sendNotificationToAffectedUsers(
                saved,
                "Đội kỹ thuật đang khắc phục",
                "Sự cố: \"" + friendlyTitle + "\" đã được tiếp nhận và đang được đội ngũ kỹ thuật xử lý."
            );
        } catch (Exception e) {
            log.error("Failed to send ticket create websocket notification", e);
        }
        return response;
    }

    @Transactional
    public TicketResponseDto updateTicketStatus(UUID id, TicketStatus status) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + id));

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(status);
        boolean incidentUpdated = false;
        Incident incidentToNotify = null;

        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());

            // Đồng bộ trạng thái RESOLVED cho Incident liên kết
            if (ticket.getIncident() != null) {
                Incident incident = ticket.getIncident();
                if (incident.getStatus() != IncidentStatus.RESOLVED) {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(Instant.now());
                    incidentRepository.save(incident);
                    log.info("Automatically resolved linked incident: {}", incident.getCode());

                    // Ghi log hoạt động đồng bộ
                    activityLogService.log(
                            null,
                            ActivityArea.SYSTEM,
                            "INCIDENT",
                            ActivityAction.RESOLVE_INCIDENT,
                            ActivitySeverity.INFO,
                            "Incident",
                            incident.getId().toString(),
                            incident.getCode(),
                            "Hệ thống tự động đóng sự cố liên kết: " + incident.getCode() + " do đóng Ticket",
                            null
                    );

                    incidentUpdated = true;
                    incidentToNotify = incident;
                }
            }
        } else {
            ticket.setResolvedAt(null);
            
            // Nếu mở lại Ticket, cũng mở lại Incident
            if (ticket.getIncident() != null) {
                Incident incident = ticket.getIncident();
                if (incident.getStatus() == IncidentStatus.RESOLVED) {
                    incident.setStatus(IncidentStatus.INVESTIGATING);
                    incident.setResolvedAt(null);
                    incidentRepository.save(incident);
                    log.info("Automatically reopened linked incident: {}", incident.getCode());

                    // Ghi log hoạt động đồng bộ
                    activityLogService.log(
                            null,
                            ActivityArea.SYSTEM,
                            "INCIDENT",
                            ActivityAction.UPDATE_INCIDENT,
                            ActivitySeverity.INFO,
                            "Incident",
                            incident.getId().toString(),
                            incident.getCode(),
                            "Hệ thống tự động mở lại sự cố liên kết: " + incident.getCode() + " do mở lại Ticket",
                            null
                    );

                    incidentUpdated = true;
                    incidentToNotify = incident;
                }
            }
        }

        Ticket saved = ticketRepository.save(ticket);

        // Ghi log hoạt động đổi trạng thái Ticket
        if (status != oldStatus) {
            ActivityAction act = (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) ? ActivityAction.CLOSE_TICKET : ActivityAction.UPDATE_TICKET_STATUS;
            activityLogService.log(
                    null,
                    ActivityArea.ADMIN,
                    "TICKET",
                    act,
                    ActivitySeverity.INFO,
                    "Ticket",
                    saved.getId().toString(),
                    saved.getCode(),
                    "Cập nhật trạng thái Ticket " + saved.getCode() + " thành " + status,
                    null
            );
        }

        // Notify incident update if changed
        if (incidentUpdated && incidentToNotify != null) {
            try {
                IncidentResponseDto incDto = incidentMapper.toResponseDto(incidentToNotify, null, saved.getCode());
                messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
            } catch (Exception e) {
                log.error("Failed to send incident update websocket notification", e);
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved, ticketAudienceService.getAffectedUserEmails(saved));
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
            notifyCustomerTicketStatusChanged();
            
            // Gửi thông báo quả chuông khi sự cố được khắc phục xong
            if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
                String friendlyTitle = getFriendlyTicketTitle(saved.getTitle());
                sendNotificationToAffectedUsers(
                    saved,
                    "Sự cố đã được khắc phục",
                    "Sự cố \"" + friendlyTitle + "\" đã xử lý xong. Bạn có thể thực hiện lại giao dịch."
                );
            }
        } catch (Exception e) {
            log.error("Failed to send ticket status update websocket notification", e);
        }
        return response;
    }

    @Transactional
    public TicketResponseDto updateTicketAssignee(UUID id, UUID assigneeId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Ticket hỗ trợ với ID: " + id));

        AccountUser oldAssignee = ticket.getAssignee();
        AccountUser assignee = null;
        if (assigneeId != null) {
            assignee = accountUserRepository.findById(assigneeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + assigneeId));
        }

        ticket.setAssignee(assignee);
        Ticket saved = ticketRepository.save(ticket);

        // Ghi log hoạt động phân công ticket
        if (!java.util.Objects.equals(oldAssignee, assignee)) {
            activityLogService.log(
                    null,
                    ActivityArea.ADMIN,
                    "TICKET",
                    ActivityAction.ASSIGN_TICKET,
                    ActivitySeverity.INFO,
                    "Ticket",
                    saved.getId().toString(),
                    saved.getCode(),
                    "Phân công Ticket " + saved.getCode() + " cho " + (assignee == null ? "chưa phân công" : assignee.getEmail()),
                    null
            );
        }

        // Đồng bộ ngược lại cho Incident nếu có liên kết
        if (saved.getIncident() != null) {
            Incident incident = saved.getIncident();
            String newAssigneeEmail = assignee != null ? assignee.getEmail() : null;
            if (!java.util.Objects.equals(incident.getAssignee(), newAssigneeEmail)) {
                incident.setAssignee(newAssigneeEmail);
                incidentRepository.save(incident);
                log.info("Synchronized assignee from Ticket {} to Incident {}", saved.getCode(), incident.getCode());

                // Ghi log hoạt động đồng bộ
                activityLogService.log(
                        null,
                        ActivityArea.SYSTEM,
                        "INCIDENT",
                        ActivityAction.UPDATE_INCIDENT,
                        ActivitySeverity.INFO,
                        "Incident",
                        incident.getId().toString(),
                        incident.getCode(),
                        "Hệ thống tự động phân công sự cố liên kết " + incident.getCode() + " cho " + (newAssigneeEmail == null ? "chưa phân công" : newAssigneeEmail),
                        null
                );

                // Gửi websocket cập nhật incident
                try {
                    IncidentResponseDto incDto = incidentMapper.toResponseDto(incident, null, saved.getCode());
                    messagingTemplate.convertAndSend("/topic/admin.incidents", incDto);
                } catch (Exception e) {
                    log.error("Failed to send incident update websocket notification", e);
                }
            }
        }

        TicketResponseDto response = ticketMapper.toResponseDto(saved, ticketAudienceService.getAffectedUserEmails(saved));
        try {
            messagingTemplate.convertAndSend("/topic/admin.tickets", response);
            notifyCustomerTicketStatusChanged();
        } catch (Exception e) {
            log.error("Failed to send ticket assignee update websocket notification", e);
        }
        return response;
    }

    private String getFriendlyTicketTitle(String title) {
        if (title == null) return "";
        String friendly = title;
        if (friendly.contains("Cannot create MoMo payment") || friendly.toLowerCase().contains("momo")) {
            friendly = friendly.replace("Cannot create MoMo payment", "Không thể khởi tạo thanh toán qua ví MoMo");
        }
        if (friendly.contains("checkout") || friendly.contains("Cannot checkout")) {
            friendly = friendly.replace("Cannot checkout", "Lỗi tiến trình đặt hàng & thanh toán (Checkout)");
        }
        if (friendly.contains("login") || friendly.contains("auth")) {
            friendly = friendly.replace("login", "Đăng nhập hệ thống").replace("auth", "Xác thực tài khoản");
        }
        friendly = friendly.replace("Sửa lỗi sự cố", "Khắc phục lỗi");
        
        // Loại bỏ các mã tiền tố kỹ thuật khi hiển thị cho khách hàng
        friendly = friendly.replaceAll("(?i)(?:Sửa lỗi sự cố|Khắc phục lỗi)\\s+INC-\\d+:\\s*", "");
        return friendly;
    }

    private void sendNotificationToAffectedUsers(Ticket ticket, String title, String content) {
        try {
            List<String> emails = ticketAudienceService.getAffectedUserEmails(ticket);
            for (String email : emails) {
                accountUserRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                    notificationService.createNotification(
                            user.getId(),
                            title,
                            content,
                            hcmute.edu.zentech.model.NotificationType.SYSTEM,
                            ticket.getId()
                    );
                });
            }
        } catch (Exception e) {
            log.error("Failed to send notification to affected users for ticket " + ticket.getCode(), e);
        }
    }
}


