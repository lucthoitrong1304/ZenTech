package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryReorderItemRequest {
    @NotNull(message = "id is required")
    private UUID id;

    private UUID parentId;

    @NotNull(message = "priority is required")
    private Integer priority;
}
