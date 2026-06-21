package hcmute.edu.zentech.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.UUID;

@Getter
public class ProductSyncEvent extends ApplicationEvent {
    private final UUID productId;
    private final String action; // "CREATE", "UPDATE", "DELETE"

    public ProductSyncEvent(Object source, UUID productId, String action) {
        super(source);
        this.productId = productId;
        this.action = action;
    }
}
