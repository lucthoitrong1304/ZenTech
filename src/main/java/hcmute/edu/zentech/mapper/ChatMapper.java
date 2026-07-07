package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.ChatAttachmentResponse;
import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.ChatRecommendedProductResponse;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.dto.response.ParticipantResponse;
import hcmute.edu.zentech.dto.response.TransferRequestResponse;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.ChatMessageAttachment;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.TransferRequest;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.CustomerRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import hcmute.edu.zentech.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatMapper {
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final AccountUserRepository accountUserRepository;
    private final R2StorageService r2StorageService;

    public ConversationResponse toConversationResponse(
            Conversation conversation,
            List<ConversationParticipant> participants
    ) {
        Customer customer = conversation.getCustomer();
        AccountUser account = customer != null ? customer.getUserInfo() : null;

        return ConversationResponse.builder()
                .id(conversation.getId())
                .customerId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getFullName() : null)
                .customerEmail(account != null ? account.getEmail() : null)
                .status(conversation.getStatus())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .closedAt(conversation.getClosedAt())
                .participants(participants == null ? List.of() : participants.stream()
                        .map(this::toParticipantResponse)
                        .toList())
                .build();
    }

    public ParticipantResponse toParticipantResponse(ConversationParticipant participant) {
        String displayName = "Unknown";
        String avatarUrl = null;
        String email = null;

        if (participant.getUserType() == ParticipantType.BOT) {
            displayName = "ZenTech AI";
        } else if (participant.getUserType() == ParticipantType.CUSTOMER) {
            displayName = customerRepository.findById(participant.getReferenceId())
                    .map(Customer::getFullName)
                    .orElse("Khách hàng");
        } else if (participant.getUserType() == ParticipantType.EMPLOYEE || participant.getUserType() == ParticipantType.EXPERT) {
            Employee employee = employeeRepository.findById(participant.getReferenceId()).orElse(null);
            if (employee != null) {
                displayName = employee.getFullName();
                avatarUrl = employee.getImageUrl();
                AccountUser account = employee.getUserInfo();
                email = account != null ? account.getEmail() : null;
            } else {
                email = accountUserRepository.findById(participant.getReferenceId())
                        .map(AccountUser::getEmail)
                        .orElse(null);
                displayName = "Nhân viên hỗ trợ";
            }
        }

        return ParticipantResponse.builder()
                .id(participant.getId())
                .userType(participant.getUserType())
                .referenceId(participant.getReferenceId())
                .email(email)
                .status(participant.getStatus())
                .joinedAt(participant.getJoinedAt())
                .leftAt(participant.getLeftAt())
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .build();
    }

    public ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        ConversationParticipant participant = message.getParticipant();

        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .participantId(participant != null ? participant.getId() : null)
                .senderType(participant != null ? participant.getUserType() : null)
                .senderReferenceId(participant != null ? participant.getReferenceId() : null)
                .messageType(message.getMessageType())
                .content(message.getContent())
                .attachments(message.getAttachments() == null ? List.of() : message.getAttachments().stream()
                        .map(this::toChatAttachmentResponse)
                        .toList())
                .recommendedProducts(message.getRecommendedProducts() == null ? List.of() : message.getRecommendedProducts().stream()
                        .map(item -> ChatRecommendedProductResponse.builder()
                                .productId(item.getProductId())
                                .variantId(item.getVariantId())
                                .name(item.getName())
                                .imageUrl(r2StorageService.getPublicUrl(item.getImageKey()))
                                .price(item.getPrice())
                                .originalPrice(item.getOriginalPrice())
                                .salePrice(item.getSalePrice())
                                .stock(item.getStock())
                                .productUrl("/products/" + item.getProductId())
                                .build())
                        .toList())
                .createdAt(message.getCreatedAt())
                .deletedAt(message.getDeletedAt())
                .build();
    }

    public ChatAttachmentResponse toChatAttachmentResponse(ChatMessageAttachment attachment) {
        return ChatAttachmentResponse.builder()
                .id(attachment.getId())
                .fileKey(attachment.getFileKey())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .attachmentType(attachment.getAttachmentType())
                .sortOrder(attachment.getSortOrder())
                .build();
    }

    public TransferRequestResponse toTransferRequestResponse(TransferRequest request) {
        return TransferRequestResponse.builder()
                .id(request.getId())
                .conversationId(request.getConversation().getId())
                .requestedBy(request.getRequestedBy())
                .requestedTo(request.getRequestedTo())
                .reason(request.getReason())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .resolvedAt(request.getResolvedAt())
                .build();
    }
}
