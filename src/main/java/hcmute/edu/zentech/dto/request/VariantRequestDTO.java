package hcmute.edu.zentech.dto.request;

import lombok.Data;

import java.time.Instant;

@Data
public class VariantRequestDTO {
    private double originalPrice;
    private Double salePrice;
    private String name;
    private String nameColor;
    private String colorCode;
    private Instant saleStartAt;
    private Instant saleEndAt;
    private int stockQuantity;
}
