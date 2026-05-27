package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ProductVariantUpsertRequest {
    private UUID id;

    @DecimalMin(value = "0.0", message = "originalPrice must be greater than or equal to 0")
    private Double originalPrice;

    @DecimalMin(value = "0.0", message = "salePrice must be greater than or equal to 0")
    private Double salePrice;

    @Size(max = 255, message = "name must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "nameColor must not exceed 255 characters")
    private String nameColor;

    @Size(max = 50, message = "colorCode must not exceed 50 characters")
    private String colorCode;

    private Instant saleStartAt;
    private Instant saleEndAt;

    @Min(value = 0, message = "stockQuantity must be greater than or equal to 0")
    private Integer stockQuantity;
}
