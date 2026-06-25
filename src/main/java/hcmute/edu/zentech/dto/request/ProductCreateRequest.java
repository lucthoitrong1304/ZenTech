package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProductCreateRequest {
    @NotBlank(message = "productName is required")
    @Size(max = 255, message = "productName must not exceed 255 characters")
    private String productName;

    private UUID productGroupId;

    @NotEmpty(message = "categoryIds is required")
    private List<UUID> categoryIds;

    @Size(max = 1000, message = "representativeImageKey must not exceed 1000 characters")
    private String representativeImageKey;

    private List<@Size(max = 1000, message = "imageKey must not exceed 1000 characters") String> imageKeys;

    private String specifications;

    private String compatibility;

    private String boxContents;

    private String supportInfo;

    @jakarta.validation.Valid
    private List<ProductVariantUpsertRequest> variants;
}
