package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.PermissionCode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionItemResponse {
    private PermissionCode code;
    private String action;
    private String description;
}
