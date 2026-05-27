package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductGroupResponse {
    private UUID id;
    private String groupName;
    private String description;
    private boolean deleted;
    private Instant deletedAt;
    private Instant updatedAt;
    private List<UUID> productIds;
    private int productCount;
}
