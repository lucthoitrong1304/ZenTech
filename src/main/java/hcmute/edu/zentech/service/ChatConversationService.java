package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ChatConversationListQueryRequest;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ConversationStatus;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.repository.ConversationParticipantRepository;
import hcmute.edu.zentech.repository.ConversationRepository;
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
    private final ChatParticipantService chatParticipantService;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;

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
        ensureNotClosed(conversation);

        conversation.setStatus(ConversationStatus.WAITING_FOR_AGENT);
        conversation.setUpdatedAt(Instant.now());
        Conversation savedConversation = conversationRepository.save(conversation);
        ConversationResponse response = toConversationResponse(savedConversation);
        messagingTemplate.convertAndSend("/topic/management.chat.queue", response);
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

        ConversationResponse response = toConversationResponse(conversationRepository.save(conversation));
        messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response);
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
        return response;
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
