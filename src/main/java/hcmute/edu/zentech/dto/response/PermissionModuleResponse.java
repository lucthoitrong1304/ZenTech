package hcmute.edu.zentech.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PermissionModuleResponse {
    private String module;
    private String moduleName;
    private List<PermissionItemResponse> permissions;
}
