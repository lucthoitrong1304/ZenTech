package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ProductGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductGroupRepository extends JpaRepository<ProductGroup, UUID> {
}
