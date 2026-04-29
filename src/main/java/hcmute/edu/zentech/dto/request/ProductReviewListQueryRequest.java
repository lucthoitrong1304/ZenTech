package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductReviewListQueryRequest {
    @Min(value = 0, message = "page must be greater than or equal to 0")
    private int page = 0;

    @Min(value = 1, message = "size must be greater than 0")
    private int size = 5;
}
