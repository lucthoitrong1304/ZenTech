package hcmute.edu.zentech.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class AiAgentDatasetsRequest {
    private Set<UUID> datasetIds = Set.of();
}
