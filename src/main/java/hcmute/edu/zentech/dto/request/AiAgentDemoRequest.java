package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AiAgentDemoRequest {
    @NotBlank
    private String message;

    private List<AiAgentRuntimeRequest.HistoryMessage> history = new ArrayList<>();
}
