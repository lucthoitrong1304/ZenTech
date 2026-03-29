package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Product;
import hcmute.edu.zentech.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
}
