package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.TransferRequestCreateRequest;
import hcmute.edu.zentech.dto.request.TransferRequestUpdateRequest;
import hcmute.edu.zentech.dto.response.TransferRequestResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationStatus;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.TransferRequest;
import hcmute.edu.zentech.model.TransferRequestStatus;
import hcmute.edu.zentech.repository.ConversationRepository;
import hcmute.edu.zentech.repository.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferRequestService {
    private final TransferRequestRepository transferRequestRepository;
    private final ConversationRepository conversationRepository;
    private final ChatParticipantService chatParticipantService;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public TransferRequestResponse createTransferRequest(UUID conversationId, TransferRequestCreateRequest request) {
        ChatParticipantService.StaffIdentity staff = chatParticipantService.getCurrentStaffIdentity();
        Conversation conversation = getConversation(conversationId);
        ensureNotClosed(conversation);

        transferRequestRepository.findFirstByConversation_IdAndStatusOrderByCreatedAtDesc(
                conversationId,
                TransferRequestStatus.PENDING
        ).ifPresent(existing -> {
            throw new IllegalArgumentException("Conversation already has a pending transfer request");
        });

        TransferRequest transferRequest = TransferRequest.builder()
                .conversation(conversation)
                .requestedBy(staff.referenceId())
                .requestedTo(request.getRequestedTo())
                .reason(normalizeText(request.getReason()))
                .status(TransferRequestStatus.PENDING)
                .build();

        TransferRequestResponse response = chatMapper.toTransferRequestResponse(
                transferRequestRepository.save(transferRequest)
        );
        messagingTemplate.convertAndSend("/topic/owner.chat.queue", response);
        return response;
    }

    @Transactional
    public TransferRequestResponse updateTransferRequest(UUID requestId, TransferRequestUpdateRequest request) {
        ChatParticipantService.StaffIdentity staff = chatParticipantService.getCurrentStaffIdentity();
        TransferRequest transferRequest = transferRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer Request", "id", requestId));

        if (transferRequest.getStatus() != TransferRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending transfer requests can be updated");
        }

        TransferRequestStatus nextStatus = request.getStatus();
        if (nextStatus == TransferRequestStatus.PENDING) {
            throw new IllegalArgumentException("Transfer request is already pending");
        }

        transferRequest.setStatus(nextStatus);
        transferRequest.setResolvedAt(Instant.now());
        if (nextStatus == TransferRequestStatus.ACCEPTED) {
            chatParticipantService.addOrUpdateParticipant(
                    transferRequest.getConversation(),
                    staff.participantType(),
                    staff.referenceId(),
                    ParticipantStatus.ACTIVE
            );
        }
        TransferRequestResponse response = chatMapper.toTransferRequestResponse(
                transferRequestRepository.save(transferRequest)
        );
        messagingTemplate.convertAndSend("/topic/owner.chat.queue", response);
        return response;
    }

    private Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
    }

    private void ensureNotClosed(Conversation conversation) {
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new AccessDeniedException("Conversation is closed");
        }
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }

        String trimmedText = text.trim();
        return trimmedText.isEmpty() ? null : trimmedText;
    }
}
