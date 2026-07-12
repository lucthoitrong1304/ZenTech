package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "conversation_read_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_read_account",
                columnNames = {"conversation_id", "account_id"}
        ),
        indexes = @Index(name = "idx_conversation_read_account", columnList = "account_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationReadState {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "read_state_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountUser account;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    @Column(name = "last_read_at")
    private Instant lastReadAt;
}
