package hcmute.edu.zentech.service;

import hcmute.edu.zentech.event.ReturnEvidenceCleanupEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReturnEvidenceCleanupEventListenerTest {
    @Test
    void continuesCleanupWhenOneDeleteFails() {
        R2StorageService storageService = mock(R2StorageService.class);
        ReturnEvidenceCleanupEventListener listener = new ReturnEvidenceCleanupEventListener(storageService);
        doThrow(new RuntimeException("R2 unavailable")).when(storageService).deleteFile("temp/one.png");

        assertDoesNotThrow(() -> listener.handleReturnEvidenceCleanup(
                new ReturnEvidenceCleanupEvent(this, List.of("temp/one.png", "temp/two.png"))));

        verify(storageService).deleteFile("temp/one.png");
        verify(storageService).deleteFile("temp/two.png");
    }
}
