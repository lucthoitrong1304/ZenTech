package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryManagementRequest {
    @NotBlank(message = "categoryName is required")
    @Size(max = 255, message = "categoryName must be at most 255 characters")
    private String categoryName;

    @Size(max = 255, message = "shortName must be at most 255 characters")
    private String shortName;

    private UUID parentId;
    private Boolean visible;
}
