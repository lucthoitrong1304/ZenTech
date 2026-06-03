package hcmute.edu.zentech.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportManagementCustomerSegmentResponse {
    private String customerName;
    private String email;
    private String imageUrl;
    private java.time.Instant joinDate;
    private String address;
    private double totalSpent;
    private long orderCount;
}
