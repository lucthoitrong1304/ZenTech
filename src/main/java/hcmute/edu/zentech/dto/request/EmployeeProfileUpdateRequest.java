package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeProfileUpdateRequest {
    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    private String phoneNumber;

    private String address;

    private LocalDate dateOfBirth;

    private String imageUrl;
}
