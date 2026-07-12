package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationReadState;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.ConversationReadStateRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationReadStateService {
    private static final List<Role> STAFF_ROLES = List.of(Role.EMPLOYEE, Role.MANAGER, Role.OWNER, Role.ADMIN);

    private final ConversationReadStateRepository readStateRepository;
    private final AccountUserRepository accountUserRepository;
    private final EmployeeRepository employeeRepository;

    public int getUnreadCount(UUID conversationId, UUID accountId) {
        return readStateRepository.findByConversation_IdAndAccount_Id(conversationId, accountId)
                .map(ConversationReadState::getUnreadCount)
                .orElse(0);
    }

    public void incrementRecipients(Conversation conversation, UUID senderAccountId) {
        Set<UUID> recipientIds = new LinkedHashSet<>();
        if (conversation.getCustomer() != null && conversation.getCustomer().getUserInfo() != null) {
            recipientIds.add(conversation.getCustomer().getUserInfo().getId());
        }
        employeeRepository.findActiveStaff(STAFF_ROLES).stream()
                .map(employee -> employee.getUserInfo().getId())
                .forEach(recipientIds::add);
        recipientIds.remove(senderAccountId);

        recipientIds.forEach(accountId -> {
            ConversationReadState state = readStateRepository
                    .findByConversation_IdAndAccount_Id(conversation.getId(), accountId)
                    .orElseGet(() -> ConversationReadState.builder()
                            .conversation(conversation)
                            .account(accountUserRepository.getReferenceById(accountId))
                            .unreadCount(0)
                            .build());
            state.setUnreadCount(state.getUnreadCount() + 1);
            readStateRepository.save(state);
        });
    }

    public void markRead(Conversation conversation, UUID accountId) {
        AccountUser account = accountUserRepository.getReferenceById(accountId);
        ConversationReadState state = readStateRepository
                .findByConversation_IdAndAccount_Id(conversation.getId(), accountId)
                .orElseGet(() -> ConversationReadState.builder()
                        .conversation(conversation)
                        .account(account)
                        .build());
        state.setUnreadCount(0);
        state.setLastReadAt(Instant.now());
        readStateRepository.save(state);
    }

    public List<UUID> getAccountsWithReadState(UUID conversationId) {
        return readStateRepository.findByConversation_Id(conversationId).stream()
                .map(state -> state.getAccount().getId())
                .toList();
    }

    public void deleteByConversationId(UUID conversationId) {
        readStateRepository.deleteByConversationId(conversationId);
    }
}
