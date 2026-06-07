package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.AiDatasetStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiDatasetRequest {
    @NotBlank
    private String name;
    private String description;
    private AiDatasetStatus status = AiDatasetStatus.ACTIVE;
}
