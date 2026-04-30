package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductGroupService {
    private final ProductGroupRepository productGroupRepository;

    @Transactional
    public ProductGroup getOrCreateGroup(String groupName, String description) {
        return productGroupRepository.findByGroupName(groupName)
                .orElseGet(() -> {
                    ProductGroup newGroup = ProductGroup.builder()
                            .groupName(groupName)
                            .description(description)
                            .build();
                    return productGroupRepository.save(newGroup);
                });
    }

    @Transactional(readOnly = true)
    public boolean existsByGroupName(String groupName) {
        return productGroupRepository.existsByGroupName(groupName);
    }
}