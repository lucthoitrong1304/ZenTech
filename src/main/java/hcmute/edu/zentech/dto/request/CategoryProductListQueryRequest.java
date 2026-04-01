package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.CategoryProductSortOption;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryProductListQueryRequest {
    private String search;

    @Min(value = 1, message = "minRating must be between 1 and 5")
    @Max(value = 5, message = "minRating must be between 1 and 5")
    private Integer minRating;

    private CategoryProductSortOption sort = CategoryProductSortOption.NEWEST;

    @Min(value = 0, message = "page must be greater than or equal to 0")
    private int page = 0;

    @Min(value = 1, message = "size must be greater than 0")
    private int size = 10;
}
