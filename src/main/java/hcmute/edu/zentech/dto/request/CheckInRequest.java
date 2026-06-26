package hcmute.edu.zentech.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CheckInRequest {
    @NotNull(message = "Face descriptor must not be null")
    @NotEmpty(message = "Face descriptor must not be empty")
    private List<Float> faceDescriptor;

    private Double latitude;

    private Double longitude;

    private Double accuracyMeters;
}
