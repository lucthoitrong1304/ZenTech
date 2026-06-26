package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.LeaveTypeUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveTypeResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private LeaveTypeUnit unit;
    private boolean active;
    private boolean systemDefault;
    private int sortOrder;
}
