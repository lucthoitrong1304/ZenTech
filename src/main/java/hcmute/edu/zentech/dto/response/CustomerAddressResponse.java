package hcmute.edu.zentech.dto.response;

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
public class CustomerAddressResponse {
    private UUID addressId;
    private String phoneNumber;
    private String province;
    private String ward;
    private String street;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;
}
