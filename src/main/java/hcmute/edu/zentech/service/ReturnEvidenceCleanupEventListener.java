package hcmute.edu.zentech.service;

import hcmute.edu.zentech.event.ReturnEvidenceCleanupEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReturnEvidenceCleanupEventListener {
    private final R2StorageService r2StorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReturnEvidenceCleanup(ReturnEvidenceCleanupEvent event) {
        for (String tempKey : event.getTempKeys()) {
            try {
                r2StorageService.deleteFile(tempKey);
            } catch (Exception ex) {
                log.error("Failed to clean up temporary return evidence {} after commit", tempKey, ex);
            }
        }
    }
}
