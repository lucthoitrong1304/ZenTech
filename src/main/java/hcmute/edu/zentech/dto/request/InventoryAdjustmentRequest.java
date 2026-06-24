package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.InventoryTransactionReason;
import hcmute.edu.zentech.model.InventoryTransactionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentRequest {
    @NotNull(message = "productVariantId must not be null")
    private UUID productVariantId;

    @NotNull(message = "type must not be null")
    private InventoryTransactionType type;

    @NotNull(message = "quantity must not be null")
    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

    @NotNull(message = "reason must not be null")
    private InventoryTransactionReason reason;

    private String note;

    private String targetWarehouse;
}
