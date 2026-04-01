package hcmute.edu.zentech.mapper;

import hcmute.edu.zentech.dto.response.CategoryProductListItemResponse;
import hcmute.edu.zentech.model.Product;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {
    public CategoryProductListItemResponse toCategoryProductListItemResponse(
            Product product,
            List<String> imageUrls,
            Double originalPrice,
            Double salePrice,
            Double averageRating) {

        return CategoryProductListItemResponse.builder()
                .id(product.getId())
                .productName(product.getProductName())
                .imageUrls(imageUrls)
                .originalPrice(originalPrice)
                .salePrice(salePrice)
                .averageRating(averageRating)
                .build();
    }
}
