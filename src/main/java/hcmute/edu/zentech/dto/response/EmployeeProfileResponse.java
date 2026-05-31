package hcmute.edu.zentech.dto.response;

import hcmute.edu.zentech.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeProfileResponse {
    private UUID id;
    private String fullName;
    private String email;
    private Role role;
    private String imageUrl;
    private String phoneNumber;
    private String address;
    private LocalDate dateOfBirth;
    private boolean isActive;
    private boolean hasRegisteredFace;
}
