package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.request.BusinessEventRequest;
import hcmute.edu.zentech.model.BusinessEvent;
import hcmute.edu.zentech.repository.BusinessEventRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessEventService {
    private final BusinessEventRepository businessEventRepository;

    @Transactional
    public void recordEvent(BusinessEventRequest request) {
        UUID accountId = null;
        try {
            accountId = SecurityContextUtils.getCurrentUserId();
        } catch (Exception e) {
            // bỏ qua nếu khách chưa đăng nhập (guest)
        }

        BusinessEvent event = BusinessEvent.builder()
                .eventType(request.getEventType())
                .userId(accountId)
                .traceId(request.getTraceId())
                .amount(request.getAmount())
                .build();

        businessEventRepository.save(event);
    }
}
