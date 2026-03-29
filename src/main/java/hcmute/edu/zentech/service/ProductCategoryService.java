package hcmute.edu.zentech.service;

import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.ProductCategory;
import hcmute.edu.zentech.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    private final ProductCategoryRepository productCategoryRepository;

    // Add Category
    public ProductCategory addCategory(String categoryName, String shortName, UUID categoryParentId) {
        ProductCategory newCategory = new ProductCategory();

        // Set name and short name
        newCategory.setCategoryName(categoryName);
        newCategory.setShortName(shortName);

        if (categoryParentId != null) {
            ProductCategory parent = productCategoryRepository.findById(categoryParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product Category", "ID", categoryParentId));
            newCategory.setParent(parent);
        } else {
            newCategory.setParent(null);
        }
        return productCategoryRepository.save(newCategory);
    }

    // find Category by short Name
    public ProductCategory findCategoryByShortName(String shortName) {
        ProductCategory productCategory = productCategoryRepository.findCategoryByShortName(shortName);
        if (productCategory == null) {
            throw new ResourceNotFoundException("Product Category", "shortName", shortName);
        }
        return productCategory;
    };
}
