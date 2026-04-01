package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.VariantRequestDTO;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.model.ProductVariant;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import hcmute.edu.zentech.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductVariantService productVariantService;

    // Add Product
    @Transactional
    public Product addProduct(
            String productName, String specifications, String compatibility,
            String boxContents, String supportInfo, UUID categoryId,
            List<VariantRequestDTO> variantDataList) {

        // 1. Kiểm tra và lấy danh mục
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryId));

        // 2. Khởi tạo đối tượng Product
        Product product = new Product();
        product.setProductName(productName);
        product.setSpecifications(specifications);
        product.setCompatibility(compatibility);
        product.setBoxContents(boxContents);
        product.setSupportInfo(supportInfo);
        product.setCategories(new HashSet<>(Set.of(category)));

        // 3. Xử lý danh sách biến thể
        if (variantDataList != null && !variantDataList.isEmpty()) {
            Set<ProductVariant> managedVariants = new HashSet<>();

            for (VariantRequestDTO dto : variantDataList) {
                ProductVariant newVariant = productVariantService.buildProductVariant(product, dto);
                managedVariants.add(newVariant);
            }

            // Gắn danh sách Con vào Cha
            product.setVariants(managedVariants);
        }

        // 4. Lưu vào Database (Nhờ CascadeType.ALL, các Variants cũng sẽ được tự động lưu)
        return productRepository.save(product);
    }
}
