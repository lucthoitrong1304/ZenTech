package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ChatConversationListQueryRequest;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ConversationStatus;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.dto.response.ChatStaffResponse;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.model.NotificationType;
import hcmute.edu.zentech.service.NotificationService;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatMessageType;
import hcmute.edu.zentech.repository.ChatMessageRepository;
import hcmute.edu.zentech.repository.ConversationParticipantRepository;
import hcmute.edu.zentech.repository.ConversationRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatConversationService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final List<ConversationStatus> ACTIVE_STATUSES = List.of(
            ConversationStatus.BOT_CONSULTING,
            ConversationStatus.WAITING_FOR_AGENT,
            ConversationStatus.AGENT_HANDLING
    );

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final EmployeeRepository employeeRepository;
    private final ChatParticipantService chatParticipantService;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ConversationResponse createOrGetCurrentCustomerConversation() {
        // Lấy khách hàng hiện tại
        Customer customer = chatParticipantService.getCurrentCustomer();

        // Hàm map chỉ được gọi khi hàm find trả về 1 đối tượng conversation.
        return conversationRepository.findFirstByCustomer_IdAndStatusInOrderByUpdatedAtDesc(customer.getId(), ACTIVE_STATUSES)
                .map(this::toConversationResponse)
                .orElseGet(() -> createConversation(customer));
    }

    @Transactional
    public ConversationResponse createNewCustomerConversation() {
        Customer customer = chatParticipantService.getCurrentCustomer();
        return createConversation(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> getMyConversations(int page, int size) {
        Customer customer = chatParticipantService.getCurrentCustomer();
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), defaultSort());
        Page<Conversation> conversationPage = conversationRepository.findByCustomer_IdOrderByUpdatedAtDesc(
                customer.getId(),
                pageable
        );
        return PageResponse.from(conversationPage, conversationPage.getContent().stream()
                .map(this::toConversationResponse)
                .toList());
    }

    // Logic lấy toàn bộ cuộc hội thoại phía nhân viên
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> getManagementConversations(ChatConversationListQueryRequest request) {
        chatParticipantService.getCurrentStaffIdentity();

        Pageable pageable = PageRequest.of(
                normalizePage(request.getPage()),
                normalizeSize(request.getSize()),
                defaultSort()
        );
        Page<Conversation> conversationPage = conversationRepository.searchManagementConversations(
                request.getStatus(),
                normalizeKeyword(request.getKeyword()),
                pageable
        );
        return PageResponse.from(conversationPage, conversationPage.getContent().stream()
                .map(this::toConversationResponse)
                .toList());
    }

    @Transactional
    public ConversationResponse requestAgent(UUID conversationId) {
        Customer customer = chatParticipantService.getCurrentCustomer();
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId());

        if (conversation.getStatus() != ConversationStatus.BOT_CONSULTING) {
            return toConversationResponse(conversation);
        }

        return moveToAgentQueue(conversation, customer);
    }

    @Transactional
    public Optional<ConversationResponse> requestAgentFromAi(UUID conversationId) {
        Conversation conversation = getConversation(conversationId);
        if (conversation.getStatus() != ConversationStatus.BOT_CONSULTING) {
            return Optional.empty();
        }

        return Optional.of(moveToAgentQueue(conversation, conversation.getCustomer()));
    }

    private ConversationResponse moveToAgentQueue(Conversation conversation, Customer customer) {
        UUID conversationId = conversation.getId();
        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversation.setUpdatedAt(Instant.now());
        Conversation savedConversation = conversationRepository.save(conversation);
        ConversationResponse response = toConversationResponse(savedConversation);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);

        List<Role> staffRoles = List.of(Role.EMPLOYEE, Role.MANAGER, Role.OWNER, Role.ADMIN);
        String customerName = customer != null && customer.getFullName() != null && !customer.getFullName().isBlank()
                ? customer.getFullName()
                : "khách hàng";
        employeeRepository.findActiveStaff(staffRoles).forEach(staff -> {
            notificationService.createNotification(
                    staff.getUserInfo().getId(),
                    "Yêu cầu hỗ trợ mới",
                    "Khách hàng " + customerName + " đang cần nhân viên hỗ trợ.",
                    NotificationType.AGENT_REQUEST,
                    conversationId
            );
        });

        return response;
    }

    @Transactional
    public ConversationResponse claimConversation(UUID conversationId) {
        ChatParticipantService.StaffIdentity staff = chatParticipantService.getCurrentStaffIdentity();
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);

        chatParticipantService.addOrUpdateParticipant(
                conversation,
                staff.participantType(),
                staff.referenceId(),
                ParticipantStatus.ACTIVE
        );
        chatParticipantService.makeBotSilent(conversation);
        conversation.setStatus(ConversationStatus.AGENT_HANDLING);
        conversation.setUpdatedAt(Instant.now());

        Conversation savedConversation = conversationRepository.save(conversation);

        // Lấy tên nhân viên
        String staffName = "Nhân viên";
        if (staff.participantType() == ParticipantType.EMPLOYEE || staff.participantType() == ParticipantType.EXPERT) {
            staffName = employeeRepository.findById(staff.referenceId())
                    .map(hcmute.edu.zentech.model.Employee::getFullName)
                    .orElse("Nhân viên");
        }

        // Tạo system message
        ChatMessage systemMessage = ChatMessage.builder()
                .conversation(savedConversation)
                .participant(null)
                .messageType(ChatMessageType.SYSTEM)
                .content("Nhân viên " + staffName + " đã tiếp nhận cuộc trò chuyện")
                .createdAt(Instant.now())
                .build();
        chatMessageRepository.save(systemMessage);

        ConversationResponse response = toConversationResponse(savedConversation);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);

        // Broadcast system message
        ChatMessageResponse systemMsgResponse = chatMapper.toChatMessageResponse(systemMessage);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, systemMsgResponse);

        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
        return response;
    }

    @Transactional
    public ConversationResponse joinSilent(UUID conversationId) {
        ChatParticipantService.StaffIdentity staff = chatParticipantService.getCurrentStaffIdentity();
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);

        chatParticipantService.addOrUpdateParticipant(
                conversation,
                staff.participantType(),
                staff.referenceId(),
                ParticipantStatus.SILENT
        );
        ConversationResponse response = toConversationResponse(conversation);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
        return response;
    }

    @Transactional
    public ConversationResponse leaveConversation(UUID conversationId) {
        ChatParticipantService.StaffIdentity staff = chatParticipantService.getCurrentStaffIdentity();
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);

        ConversationParticipant participant = chatParticipantService.getActiveParticipant(
                conversationId,
                staff.accountId()
        );
        participant.setStatus(ParticipantStatus.LEFT);
        participant.setLeftAt(Instant.now());
        participantRepository.save(participant);

        boolean hasActiveStaff = participantRepository
                .findByConversation_IdAndStatus(conversationId, ParticipantStatus.ACTIVE)
                .stream()
                .anyMatch(this::isStaffParticipant);

        conversation.setStatus(hasActiveStaff ? ConversationStatus.AGENT_HANDLING : ConversationStatus.WAITING_FOR_AGENT);
        conversation.setUpdatedAt(Instant.now());
        Conversation savedConversation = conversationRepository.save(conversation);

        ChatMessage systemMessage = ChatMessage.builder()
                .conversation(savedConversation)
                .participant(null)
                .messageType(ChatMessageType.SYSTEM)
                .content("Nhân viên " + resolveStaffName(staff) + " đã rời cuộc trò chuyện")
                .createdAt(Instant.now())
                .build();
        chatMessageRepository.save(systemMessage);

        ConversationResponse response = toConversationResponse(savedConversation);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, chatMapper.toChatMessageResponse(systemMessage));
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
        return response;
    }

    @Transactional
    public ConversationResponse closeConversation(UUID conversationId) {
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.findCurrentCustomer()
                .ifPresentOrElse(
                        customer -> chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId()),
                        chatParticipantService::getCurrentStaffIdentity
                );

        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setClosedAt(Instant.now());
        conversation.setUpdatedAt(Instant.now());
        chatParticipantService.markActiveParticipantsLeft(conversation);

        ConversationResponse response = toConversationResponse(conversationRepository.save(conversation));
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
        return response;
    }

    @Transactional
    public ConversationResponse transferConversation(UUID conversationId, UUID targetAccountId) {
        ChatParticipantService.StaffIdentity currentStaff = chatParticipantService.getCurrentStaffIdentity();
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);
        
        chatParticipantService.getActiveParticipant(conversationId, currentStaff.accountId());

        if (targetAccountId == null) {
            conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
            chatParticipantService.addOrUpdateParticipant(
                    conversation,
                    currentStaff.participantType(),
                    currentStaff.referenceId(),
                    ParticipantStatus.LEFT
            );

            List<Role> staffRoles = List.of(Role.EMPLOYEE, Role.MANAGER, Role.OWNER, Role.ADMIN);
            employeeRepository.findActiveStaff(staffRoles).forEach(staff -> {
                if (!staff.getUserInfo().getId().equals(currentStaff.accountId())) {
                    notificationService.createNotification(
                            staff.getUserInfo().getId(),
                            "Cuộc hội thoại được chuyển giao",
                            "Một cuộc hội thoại vừa được chuyển vào hàng đợi chung.",
                            NotificationType.CONVERSATION_TRANSFER,
                            conversationId
                    );
                }
            });
        } else {
            // Find target staff
            var targetEmployee = employeeRepository.findByUserInfo_Id(targetAccountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", "accountId", targetAccountId));
            Role targetRole = targetEmployee.getUserInfo().getRole();
            ParticipantType targetType = targetRole == Role.EMPLOYEE ? ParticipantType.EMPLOYEE : ParticipantType.EXPERT;

            conversation.setStatus(ConversationStatus.AGENT_HANDLING);
            chatParticipantService.addOrUpdateParticipant(
                    conversation,
                    currentStaff.participantType(),
                    currentStaff.referenceId(),
                    ParticipantStatus.LEFT
            );
            chatParticipantService.addOrUpdateParticipant(
                    conversation,
                    targetType,
                    targetEmployee.getId(),
                    ParticipantStatus.ACTIVE
            );

            notificationService.createNotification(
                    targetAccountId,
                    "Bạn được chỉ định một cuộc hội thoại",
                    "Một cuộc hội thoại vừa được chuyển giao cho bạn.",
                    NotificationType.CONVERSATION_TRANSFER,
                    conversationId
            );
        }

        conversation.setUpdatedAt(Instant.now());
        ConversationResponse response = toConversationResponse(conversationRepository.save(conversation));
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
        return response;
    }

    @Transactional
    public ConversationResponse reopenConversation(UUID conversationId) {
        Customer customer = chatParticipantService.getCurrentCustomer();
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId());

        if (conversation.getStatus() != ConversationStatus.CLOSED) {
            throw new AccessDeniedException("Conversation is not closed");
        }

        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversation.setClosedAt(null);
        conversation.setUpdatedAt(Instant.now());
        
        chatParticipantService.makeBotSilent(conversation);
        
        ConversationResponse response = toConversationResponse(conversationRepository.save(conversation));
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ChatStaffResponse> getActiveStaffList() {
        chatParticipantService.getCurrentStaffIdentity();
        List<Role> staffRoles = List.of(Role.EMPLOYEE, Role.MANAGER, Role.OWNER, Role.ADMIN);
        return employeeRepository.findActiveStaff(staffRoles).stream()
                .map(e -> ChatStaffResponse.builder()
                        .accountId(e.getUserInfo().getId())
                        .fullName(e.getFullName())
                        .imageUrl(e.getImageUrl())
                        .role(e.getUserInfo().getRole().name())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Conversation getConversationForCustomer(UUID conversationId) {
        Customer customer = chatParticipantService.getCurrentCustomer();
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.ensureCustomerOwnsConversation(conversation, customer.getId());
        return conversation;
    }

    @Transactional(readOnly = true)
    public Conversation getConversationForParticipant(UUID conversationId, UUID accountId) {
        Conversation conversation = getConversation(conversationId);
        chatParticipantService.getActiveParticipant(conversationId, accountId);
        return conversation;
    }

    // Tạo 1 cuộc hội thoại mới
    private ConversationResponse createConversation(Customer customer) {
        Conversation conversation = Conversation.builder()
                .customer(customer)
                .status(ConversationStatus.BOT_CONSULTING)
                .title("New conversation")
                .build();
        Conversation savedConversation = conversationRepository.save(conversation);

        participantRepository.save(ConversationParticipant.builder()
                .conversation(savedConversation)
                .userType(ParticipantType.CUSTOMER)
                .referenceId(customer.getId())
                .status(ParticipantStatus.ACTIVE)
                .build());
        participantRepository.save(ConversationParticipant.builder()
                .conversation(savedConversation)
                .userType(ParticipantType.BOT)
                .referenceId(ChatParticipantService.BOT_REFERENCE_ID)
                .status(ParticipantStatus.ACTIVE)
                .build());

        return toConversationResponse(savedConversation);
    }

    private Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        List<ConversationParticipant> participants = participantRepository.findByConversation_Id(conversation.getId());
        return chatMapper.toConversationResponse(conversation, participants);
    }

    private boolean isStaffParticipant(ConversationParticipant participant) {
        return participant.getUserType() == ParticipantType.EMPLOYEE || participant.getUserType() == ParticipantType.EXPERT;
    }

    private String resolveStaffName(ChatParticipantService.StaffIdentity staff) {
        if (staff.participantType() == ParticipantType.EMPLOYEE || staff.participantType() == ParticipantType.EXPERT) {
            return employeeRepository.findById(staff.referenceId())
                    .map(hcmute.edu.zentech.model.Employee::getFullName)
                    .orElse("Nhân viên");
        }
        return "Nhân viên";
    }

    private void ensureNotClosed(Conversation conversation) {
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new AccessDeniedException("Conversation is closed");
        }
    }

    private Sort defaultSort() {
        return Sort.by(Sort.Order.desc("updatedAt"));
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

        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }
}
