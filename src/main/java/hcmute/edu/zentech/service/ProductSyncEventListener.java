package hcmute.edu.zentech.service;

import hcmute.edu.zentech.event.ProductSyncEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSyncEventListener {

    private final AiManagementService aiManagementService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductSyncEvent(ProductSyncEvent event) {
        log.info("Received ProductSyncEvent for product {} with action {}", event.getProductId(), event.getAction());
        try {
            aiManagementService.syncProductToAi(event.getProductId());
        } catch (Exception ex) {
            log.error("Failed to sync product {} to AI", event.getProductId(), ex);
        }
    }
}
