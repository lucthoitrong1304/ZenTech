package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.VariantRequestDTO;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductVariantService {
    private final ProductVariantRepository productVariantRepository;

    public ProductVariant buildProductVariant(Product parentProduct, VariantRequestDTO dto) {
        Double finalSalePrice = dto.getSalePrice();
        if (dto.getSaleStartAt() == null && dto.getSaleEndAt() == null) {
            finalSalePrice = null;
        }

        List<String> imageUrls = dto.getImageUrls() == null ? List.of() : dto.getImageUrls();

        return ProductVariant.builder()
                .product(parentProduct)
                .originalPrice(dto.getOriginalPrice())
                .salePrice(finalSalePrice)
                .name(dto.getName())
                .nameColor(dto.getNameColor())
                .imageUrls(new ArrayList<>(imageUrls))
                .colorCode(dto.getColorCode())
                .saleStartAt(dto.getSaleStartAt())
                .saleEndAt(dto.getSaleEndAt())
                .stockQuantity(dto.getStockQuantity())
                .build();
    }
}
