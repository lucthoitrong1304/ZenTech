package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.ChatMessageResponse;
import hcmute.edu.zentech.dto.response.ConversationResponse;
import hcmute.edu.zentech.mapper.ChatMapper;
import hcmute.edu.zentech.model.ChatMessage;
import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationParticipant;
import hcmute.edu.zentech.model.ConversationStatus;
import hcmute.edu.zentech.model.Employee;
import hcmute.edu.zentech.model.ParticipantStatus;
import hcmute.edu.zentech.model.ParticipantType;
import hcmute.edu.zentech.repository.ChatMessageRepository;
import hcmute.edu.zentech.repository.ConversationParticipantRepository;
import hcmute.edu.zentech.repository.ConversationRepository;
import hcmute.edu.zentech.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatConversationServiceTest {
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationParticipantRepository participantRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ChatParticipantService chatParticipantService;
    @Mock
    private ChatMapper chatMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatConversationService chatConversationService;

    private UUID conversationId;
    private UUID staffAccountId;
    private UUID staffReferenceId;
    private Conversation conversation;
    private ConversationParticipant staffParticipant;
    private ConversationParticipant customerParticipant;
    private ChatParticipantService.StaffIdentity staffIdentity;

    @BeforeEach
    void setUp() {
        conversationId = UUID.randomUUID();
        staffAccountId = UUID.randomUUID();
        staffReferenceId = UUID.randomUUID();
        conversation = Conversation.builder()
                .id(conversationId)
                .status(ConversationStatus.AGENT_HANDLING)
                .title("Support conversation")
                .build();
        staffParticipant = ConversationParticipant.builder()
                .id(UUID.randomUUID())
                .conversation(conversation)
                .userType(ParticipantType.EMPLOYEE)
                .referenceId(staffReferenceId)
                .status(ParticipantStatus.ACTIVE)
                .build();
        customerParticipant = ConversationParticipant.builder()
                .id(UUID.randomUUID())
                .conversation(conversation)
                .userType(ParticipantType.CUSTOMER)
                .referenceId(UUID.randomUUID())
                .status(ParticipantStatus.ACTIVE)
                .build();
        staffIdentity = new ChatParticipantService.StaffIdentity(
                staffAccountId,
                staffReferenceId,
                ParticipantType.EMPLOYEE
        );

        lenient().when(chatParticipantService.getCurrentStaffIdentity()).thenReturn(staffIdentity);
        lenient().when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        lenient().when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(chatParticipantService.getActiveParticipant(conversationId, staffAccountId)).thenReturn(staffParticipant);
        lenient().when(participantRepository.findByConversation_Id(conversationId))
                .thenReturn(List.of(customerParticipant, staffParticipant));

        Employee employee = new Employee();
        employee.setId(staffReferenceId);
        employee.setFullName("Nhan vien A");
        lenient().when(employeeRepository.findById(staffReferenceId)).thenReturn(Optional.of(employee));

        lenient().when(chatMapper.toConversationResponse(any(Conversation.class), any()))
                .thenAnswer(invocation -> {
                    Conversation saved = invocation.getArgument(0);
                    return ConversationResponse.builder()
                            .id(saved.getId())
                            .status(saved.getStatus())
                            .title(saved.getTitle())
                            .build();
                });
        lenient().when(chatMapper.toChatMessageResponse(any(ChatMessage.class)))
                .thenReturn(ChatMessageResponse.builder().id(UUID.randomUUID()).build());
    }

    @Test
    void leaveConversationMovesLastActiveStaffConversationBackToQueue() {
        when(participantRepository.findByConversation_IdAndStatus(conversationId, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(customerParticipant));

        ConversationResponse response = chatConversationService.leaveConversation(conversationId);

        assertThat(response.getStatus()).isEqualTo(ConversationStatus.WAITING_FOR_AGENT);
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.WAITING_FOR_AGENT);
        assertThat(staffParticipant.getStatus()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(staffParticipant.getLeftAt()).isNotNull();
        verify(participantRepository).save(staffParticipant);
        verify(messagingTemplate).convertAndSend("/topic/management.chat.queue", response);
        verify(messagingTemplate).convertAndSend("/topic/conversations." + conversationId, response);
    }

    @Test
    void leaveConversationKeepsHandlingWhenAnotherStaffIsActive() {
        ConversationParticipant anotherStaff = ConversationParticipant.builder()
                .id(UUID.randomUUID())
                .conversation(conversation)
                .userType(ParticipantType.EXPERT)
                .referenceId(UUID.randomUUID())
                .status(ParticipantStatus.ACTIVE)
                .build();
        when(participantRepository.findByConversation_IdAndStatus(conversationId, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(customerParticipant, anotherStaff));

        ConversationResponse response = chatConversationService.leaveConversation(conversationId);

        assertThat(response.getStatus()).isEqualTo(ConversationStatus.AGENT_HANDLING);
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.AGENT_HANDLING);
        assertThat(staffParticipant.getStatus()).isEqualTo(ParticipantStatus.LEFT);
    }

    @Test
    void leaveConversationCreatesSystemMessage() {
        when(participantRepository.findByConversation_IdAndStatus(conversationId, ParticipantStatus.ACTIVE))
                .thenReturn(List.of(customerParticipant));
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        chatConversationService.leaveConversation(conversationId);

        verify(chatMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getContent())
                .isEqualTo("Nhân viên Nhan vien A đã rời cuộc trò chuyện");
    }

    @Test
    void leaveConversationRequiresActiveStaffParticipant() {
        when(chatParticipantService.getActiveParticipant(conversationId, staffAccountId))
                .thenThrow(new AccessDeniedException("Only active participants can send messages"));

        assertThatThrownBy(() -> chatConversationService.leaveConversation(conversationId))
                .isInstanceOf(AccessDeniedException.class);

        verify(participantRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void leaveConversationRejectsClosedConversation() {
        conversation.setStatus(ConversationStatus.CLOSED);

        assertThatThrownBy(() -> chatConversationService.leaveConversation(conversationId))
                .isInstanceOf(AccessDeniedException.class);

        verify(chatParticipantService, never()).getActiveParticipant(any(), any());
        verify(participantRepository, never()).save(any());
    }
}
