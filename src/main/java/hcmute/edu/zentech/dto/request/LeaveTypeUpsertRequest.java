package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.LeaveTypeUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeaveTypeUpsertRequest {
    @NotBlank
    private String name;

    private String code;

    private String description;

    @NotNull
    private LeaveTypeUnit unit;

    private Boolean active;

    private Integer sortOrder;
}
