package hcmute.edu.zentech.service;

import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.ConversationParticipantRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.security.CustomUserDetails;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatParticipantService {
    public static final UUID BOT_REFERENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final ConversationParticipantRepository participantRepository;

    // Hàm lấy id của user đang đăng nhập vào hệ thống
    public UUID getCurrentAccountId() {
        UUID accountId = SecurityContextUtils.getCurrentUserId();
        if (accountId == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return accountId;
    }

    // Lấy thông tin khách hàng hiện tại.
    public Customer getCurrentCustomer() {
        UUID accountId = getCurrentAccountId();
        return customerRepository.findByUserInfo_Id(accountId)
                .orElseThrow(() -> new AccessDeniedException("Only customers can access this chat"));
    }

    public Optional<Customer> findCurrentCustomer() {
        UUID accountId = getCurrentAccountId();
        return customerRepository.findByUserInfo_Id(accountId);
    }

    // Xác định vai trò người dùng nếu không phải là khách hàng
    public StaffIdentity getCurrentStaffIdentity() {
        CustomUserDetails currentUser = SecurityContextUtils.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication is required");
        }

        Role role = resolveRole(currentUser);
        if (!isStaffRole(role)) {
            throw new AccessDeniedException("Only staff can access management chat");
        }

        UUID accountId = currentUser.getId();
        UUID referenceId = employeeRepository.findByUserInfo_Id(accountId)
                .map(Employee::getId)
                .orElse(accountId);
        ParticipantType participantType = role == Role.EMPLOYEE ? ParticipantType.EMPLOYEE : ParticipantType.EXPERT;

        return new StaffIdentity(accountId, referenceId, participantType);
    }

    public ConversationParticipant addOrUpdateParticipant(
            Conversation conversation,
            ParticipantType userType,
            UUID referenceId,
            ParticipantStatus status
    ) {
        ConversationParticipant participant = participantRepository
                .findByConversation_IdAndReferenceId(conversation.getId(), referenceId)
                .orElseGet(() -> ConversationParticipant.builder()
                        .conversation(conversation)
                        .userType(userType)
                        .referenceId(referenceId)
                        .build());

        participant.setUserType(userType);
        participant.setStatus(status);
        if (status == ParticipantStatus.LEFT) {
            participant.setLeftAt(Instant.now());
        } else {
            participant.setLeftAt(null);
        }
        return participantRepository.save(participant);
    }

    public ConversationParticipant getActiveParticipant(UUID conversationId, UUID accountId) {
        UUID referenceId = resolveReferenceId(accountId); // Lấy id của khách hàng hoặc employee => Nếu có không nghĩa là bot
        ConversationParticipant participant = participantRepository
                .findByConversation_IdAndReferenceId(conversationId, referenceId)
                .orElseThrow(() -> new AccessDeniedException("You are not a participant in this conversation"));

        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            throw new AccessDeniedException("Only active participants can send messages");
        }
        return participant;
    }

    // Check xem người dùng truy cập vô hội thoại này có đúng là chủ sở hữu hội thoại hay không
    public void ensureCustomerOwnsConversation(Conversation conversation, UUID customerId) {
        if (conversation.getCustomer() == null || !customerId.equals(conversation.getCustomer().getId())) {
            throw new AccessDeniedException("You can only access your own conversations");
        }
    }

    public void makeBotSilent(Conversation conversation) {
        participantRepository.findByConversation_IdAndUserType(conversation.getId(), ParticipantType.BOT)
                .ifPresent(bot -> {
                    bot.setStatus(ParticipantStatus.SILENT);
                    bot.setLeftAt(null);
                    participantRepository.save(bot);
                });
    }

    public void markActiveParticipantsLeft(Conversation conversation) {
        participantRepository.findByConversation_IdAndStatus(conversation.getId(), ParticipantStatus.ACTIVE)
                .forEach(participant -> {
                    participant.setStatus(ParticipantStatus.LEFT);
                    participant.setLeftAt(Instant.now());
                    participantRepository.save(participant);
                });
    }

    // Tìm kiếm id người dùng
    private UUID resolveReferenceId(UUID accountId) {
        return customerRepository.findByUserInfo_Id(accountId)
                .map(Customer::getId)
                .or(() -> employeeRepository.findByUserInfo_Id(accountId).map(Employee::getId))
                .orElse(accountId);
    }

    public Optional<UUID> resolveAccountId(ParticipantType userType, UUID referenceId) {
        if (userType == ParticipantType.CUSTOMER) {
            return customerRepository.findById(referenceId).map(c -> c.getUserInfo().getId());
        } else if (userType == ParticipantType.EMPLOYEE || userType == ParticipantType.EXPERT) {
            return employeeRepository.findById(referenceId).map(e -> e.getUserInfo().getId());
        }
        return Optional.empty(); // System, Bot, etc.
    }

    private Role resolveRole(CustomUserDetails currentUser) {
        return currentUser.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .map(Role::valueOf)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "accountId", currentUser.getId()));
    }

    private boolean isStaffRole(Role role) {
        return role == Role.EMPLOYEE || role == Role.MANAGER || role == Role.OWNER || role == Role.ADMIN;
    }

    public record StaffIdentity(UUID accountId, UUID referenceId, ParticipantType participantType) {
    }
}
