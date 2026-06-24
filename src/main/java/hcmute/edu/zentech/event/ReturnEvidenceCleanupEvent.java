package hcmute.edu.zentech.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class ReturnEvidenceCleanupEvent extends ApplicationEvent {
    private final List<String> tempKeys;

    public ReturnEvidenceCleanupEvent(Object source, List<String> tempKeys) {
        super(source);
        this.tempKeys = List.copyOf(tempKeys);
    }
}
