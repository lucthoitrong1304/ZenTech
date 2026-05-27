package hcmute.edu.zentech.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProductUpdateRequest {
    @Size(max = 255, message = "productName must not exceed 255 characters")
    private String productName;

    private UUID productGroupId;
    private Boolean clearProductGroup;
    private List<UUID> categoryIds;

    @Size(max = 1000, message = "representativeImageKey must not exceed 1000 characters")
    private String representativeImageKey;

    private Boolean clearRepresentativeImage;
    private List<@Size(max = 1000, message = "imageKey must not exceed 1000 characters") String> imageKeys;

    @Valid
    private MarkdownContentRequest description;

    @Valid
    private MarkdownContentRequest specifications;

    @Valid
    private MarkdownContentRequest compatibility;

    @Valid
    private MarkdownContentRequest boxContents;

    @Valid
    private MarkdownContentRequest supportInfo;

    @Valid
    private List<ProductVariantUpsertRequest> variants;
}
