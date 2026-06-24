package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.InventoryTransactionReason;
import hcmute.edu.zentech.model.InventoryTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionResponse {
    private UUID id;
    private String productName;
    private String variantName;
    private InventoryTransactionType type;
    private int quantity;
    private InventoryTransactionReason reason;
    private String note;
    private Instant createdAt;
    private UUID createdBy;
    private String createdByName;
    private String createdByEmail;
    private String createdByAvatar;
    private String targetWarehouse;
}
