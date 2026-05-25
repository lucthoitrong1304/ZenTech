package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateMyProfileRequest {
    @Size(max = 255, message = "fullName must not exceed 255 characters")
    private String fullName;

    @Size(max = 500, message = "imageUrl must not exceed 500 characters")
    private String imageUrl;
}
