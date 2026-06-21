package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.AiAgentStatus;
import hcmute.edu.zentech.model.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class AiAgentRequest {
    @NotBlank
    private String name;

    private String description;

    private AiAgentStatus status = AiAgentStatus.INACTIVE;

    @jakarta.validation.constraints.NotNull
    private Role assignedRole;

    private int priority = 0;

    @NotBlank
    private String systemPrompt;

    private String guardrails;

    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private BigDecimal temperature = BigDecimal.valueOf(0.3);

    @Min(100)
    @Max(8000)
    private int maxTokens = 1000;

    @Min(1)
    @Max(20)
    private int topK = 5;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal scoreThreshold = BigDecimal.valueOf(0.35);

    private String fallbackMessage;

    private boolean handoffEnabled = true;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private BigDecimal handoffThreshold = BigDecimal.valueOf(0.50);

    private Set<UUID> datasetIds = Set.of();
}
