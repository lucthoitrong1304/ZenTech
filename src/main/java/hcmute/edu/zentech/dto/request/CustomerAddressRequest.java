package hcmute.edu.zentech.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomerAddressRequest {
    @NotBlank(message = "phoneNumber is required")
    @Size(max = 30, message = "phoneNumber must not exceed 30 characters")
    private String phoneNumber;

    @NotBlank(message = "province is required")
    @Size(max = 120, message = "province must not exceed 120 characters")
    private String province;

    @NotBlank(message = "ward is required")
    @Size(max = 120, message = "ward must not exceed 120 characters")
    private String ward;

    @NotBlank(message = "street is required")
    @Size(max = 255, message = "street must not exceed 255 characters")
    private String street;

    @JsonProperty("isDefault")
    private boolean isDefault;
}
