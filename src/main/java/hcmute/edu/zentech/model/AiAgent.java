package hcmute.edu.zentech.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "ai_agents",
        indexes = {
                @Index(name = "idx_ai_agents_status", columnList = "status"),
                @Index(name = "idx_ai_agents_priority", columnList = "priority")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "agent_id")
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private AiAgentStatus status = AiAgentStatus.INACTIVE;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_agent_roles", joinColumns = @JoinColumn(name = "agent_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<Role> assignedRoles = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private int priority = 0;

    @Column(name = "default_for_role", nullable = false)
    @Builder.Default
    private boolean defaultForRole = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(columnDefinition = "TEXT")
    private String guardrails;

    @Column(nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal temperature = BigDecimal.valueOf(0.3);

    @Column(nullable = false)
    @Builder.Default
    private int maxTokens = 1000;

    @Column(nullable = false)
    @Builder.Default
    private int topK = 5;

    @Column(nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal scoreThreshold = BigDecimal.valueOf(0.35);

    @Column(columnDefinition = "TEXT")
    private String fallbackMessage;

    @Column(nullable = false)
    @Builder.Default
    private boolean handoffEnabled = true;

    @Column(nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal handoffThreshold = BigDecimal.valueOf(0.50);

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ai_agent_datasets",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "dataset_id")
    )
    private Set<AiDataset> datasets = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
