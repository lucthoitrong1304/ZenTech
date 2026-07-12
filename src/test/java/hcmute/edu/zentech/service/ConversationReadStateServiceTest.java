package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationReadState;
import hcmute.edu.zentech.model.Customer;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.ConversationReadStateRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationReadStateServiceTest {
    @Mock
    private ConversationReadStateRepository readStateRepository;
    @Mock
    private AccountUserRepository accountUserRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @InjectMocks
    private ConversationReadStateService service;

    @Test
    void incrementsUnreadForRecipientsButNotSender() {
        UUID customerAccountId = UUID.randomUUID();
        UUID staffAccountId = UUID.randomUUID();
        AccountUser customerAccount = AccountUser.builder().id(customerAccountId).build();
        AccountUser staffAccount = AccountUser.builder().id(staffAccountId).build();
        Conversation conversation = Conversation.builder()
                .id(UUID.randomUUID())
                .customer(Customer.builder().userInfo(customerAccount).build())
                .build();

        when(employeeRepository.findActiveStaff(any())).thenReturn(List.of(
                Employee.builder().userInfo(staffAccount).build()));
        when(readStateRepository.findByConversation_IdAndAccount_Id(conversation.getId(), staffAccountId))
                .thenReturn(Optional.empty());
        when(accountUserRepository.getReferenceById(staffAccountId)).thenReturn(staffAccount);

        service.incrementRecipients(conversation, customerAccountId);

        ArgumentCaptor<ConversationReadState> captor = ArgumentCaptor.forClass(ConversationReadState.class);
        verify(readStateRepository).save(captor.capture());
        assertThat(captor.getValue().getAccount().getId()).isEqualTo(staffAccountId);
        assertThat(captor.getValue().getUnreadCount()).isEqualTo(1);
    }

    @Test
    void markReadResetsOnlyCurrentAccountState() {
        UUID accountId = UUID.randomUUID();
        Conversation conversation = Conversation.builder().id(UUID.randomUUID()).build();
        ConversationReadState state = ConversationReadState.builder().unreadCount(4).build();
        when(accountUserRepository.getReferenceById(accountId)).thenReturn(AccountUser.builder().id(accountId).build());
        when(readStateRepository.findByConversation_IdAndAccount_Id(conversation.getId(), accountId))
                .thenReturn(Optional.of(state));

        service.markRead(conversation, accountId);

        assertThat(state.getUnreadCount()).isZero();
        assertThat(state.getLastReadAt()).isNotNull();
        verify(readStateRepository).save(state);
    }
}
