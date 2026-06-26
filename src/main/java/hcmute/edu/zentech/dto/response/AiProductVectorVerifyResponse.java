package hcmute.edu.zentech.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AiProductVectorVerifyResponse {
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private UUID productId;
        private UUID variantId;
        private boolean present;
        private int pointCount;
    }
}
