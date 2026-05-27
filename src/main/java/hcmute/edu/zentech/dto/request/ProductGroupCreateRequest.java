package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProductGroupCreateRequest {
    @NotBlank(message = "groupName is required")
    @Size(max = 255, message = "groupName must not exceed 255 characters")
    private String groupName;

    @Size(max = 5000, message = "description must not exceed 5000 characters")
    private String description;

    private List<UUID> productIds;
}
