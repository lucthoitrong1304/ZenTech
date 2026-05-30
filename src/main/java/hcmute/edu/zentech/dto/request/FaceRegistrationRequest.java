package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FaceRegistrationRequest {
    @NotNull(message = "Face descriptors must not be null")
    @NotEmpty(message = "Face descriptors must not be empty")
    private List<List<Float>> faceDescriptors;
}
