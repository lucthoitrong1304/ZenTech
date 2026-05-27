package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.ProductGroupCreateRequest;
import hcmute.edu.zentech.dto.request.ProductGroupUpdateRequest;
import hcmute.edu.zentech.mapper.ProductMapper;
import hcmute.edu.zentech.model.ProductGroup;
import hcmute.edu.zentech.repository.ProductGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductGroupServiceTest {
    @Mock
    private ProductGroupRepository productGroupRepository;

    private ProductGroupService productGroupService;

    @BeforeEach
    void setUp() {
        productGroupService = new ProductGroupService(productGroupRepository, new ProductMapper());
    }

    @Test
    void createGroupRejectsDuplicateActiveName() {
        ProductGroupCreateRequest request = new ProductGroupCreateRequest();
        request.setGroupName("Alpha65");
        when(productGroupRepository.existsActiveGroupNameExcludingId("Alpha65", null)).thenReturn(true);

        assertThatThrownBy(() -> productGroupService.createGroup(request))
                .hasMessage("Product group name already exists");
    }

    @Test
    void updateGroupAllowsCurrentNameAndNormalizesDescription() {
        UUID groupId = UUID.randomUUID();
        ProductGroup group = ProductGroup.builder()
                .id(groupId)
                .groupName("Old")
                .description("Old description")
                .build();
        ProductGroupUpdateRequest request = new ProductGroupUpdateRequest();
        request.setGroupName(" New ");
        request.setDescription("   ");

        when(productGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        productGroupService.updateGroup(groupId, request);

        assertThat(group.getGroupName()).isEqualTo("New");
        assertThat(group.getDescription()).isNull();
    }

    @Test
    void deleteGroupSoftDeletesGroup() {
        UUID groupId = UUID.randomUUID();
        ProductGroup group = ProductGroup.builder()
                .id(groupId)
                .groupName("Alpha65")
                .build();
        when(productGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        productGroupService.deleteGroup(groupId);

        assertThat(group.isDeleted()).isTrue();
        assertThat(group.getDeletedAt()).isNotNull();
    }
}
