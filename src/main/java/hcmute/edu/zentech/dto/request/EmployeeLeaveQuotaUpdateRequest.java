package hcmute.edu.zentech.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class EmployeeLeaveQuotaUpdateRequest {
    @Valid
    private List<Item> quotas = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull
        private UUID leaveTypeId;

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal entitlement;
    }
}
