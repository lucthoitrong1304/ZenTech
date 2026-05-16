package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSummaryResponse {
    private UUID employeeId;
    private UUID accountId;
    private String email;
    private String fullName;
    private String imageUrl;
    private Role role;
    private boolean active;
    private Instant createdAt;
}
