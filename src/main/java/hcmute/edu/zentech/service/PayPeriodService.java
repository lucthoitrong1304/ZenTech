package hcmute.edu.zentech.service;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.PayPeriod;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.PayPeriodRepository;
import hcmute.edu.zentech.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayPeriodService {
    private final PayPeriodRepository payPeriodRepository;
    private final AccountUserRepository accountUserRepository;

    @Transactional(readOnly = true)
    public List<PayPeriod> getAllPeriods() {
        return payPeriodRepository.findAll();
    }

    @Transactional
    public PayPeriod createPeriod(PayPeriod period) {
        // Validate overlapping periods
        List<PayPeriod> all = payPeriodRepository.findAll();
        for (PayPeriod p : all) {
            boolean overlaps = !period.getStartDate().isAfter(p.getEndDate()) && 
                               !period.getEndDate().isBefore(p.getStartDate());
            if (overlaps) {
                throw new RuntimeException("Kỳ công mới trùng lặp thời gian với kỳ công: " + p.getName());
            }
        }
        return payPeriodRepository.save(period);
    }

    @Transactional
    public PayPeriod toggleLock(UUID id, boolean lock) {
        PayPeriod period = payPeriodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ công."));

        if (lock) {
            UUID userId = SecurityContextUtils.getCurrentUserId();
            AccountUser user = userId != null ? accountUserRepository.findById(userId).orElse(null) : null;
            period.setLocked(true);
            period.setLockedBy(user);
            period.setLockedAt(LocalDateTime.now());
        } else {
            period.setLocked(false);
            period.setLockedBy(null);
            period.setLockedAt(null);
        }

        return payPeriodRepository.save(period);
    }
}
