package hcmute.edu.zentech.dto.request;

import hcmute.edu.zentech.model.BusinessEventType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BusinessEventRequest {
    @NotNull(message = "Loại sự kiện không được để trống")
    private BusinessEventType eventType;
    
    private Double amount;
    private String traceId;
}
