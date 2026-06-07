package hcmute.edu.zentech.service;

import hcmute.edu.zentech.dto.response.NotificationResponse;
import hcmute.edu.zentech.dto.response.PageResponse;
import hcmute.edu.zentech.dto.response.UnreadCountResponse;
import hcmute.edu.zentech.exception.ResourceNotFoundException;
import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.model.Notification;
import hcmute.edu.zentech.model.NotificationType;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final AccountUserRepository accountUserRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(UUID accountId, String title, String content, NotificationType type, UUID referenceId) {
        AccountUser accountUser = accountUserRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountUser", "id", accountId));

        Notification notification = Notification.builder()
                .accountUser(accountUser)
                .title(title)
                .content(content)
                .type(type)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = toNotificationResponse(saved);

        // Gửi qua WebSocket cho user (sử dụng email vì UserDetails trong Spring Security đang lưu email)
        messagingTemplate.convertAndSendToUser(
                accountUser.getEmail(),
                "/queue/notifications",
                response
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotificationsForAccount(UUID accountId, int page, int size) {
        Page<Notification> notificationPage = notificationRepository.findByAccountUser_IdOrderByCreatedAtDesc(
                accountId,
                PageRequest.of(page, size)
        );
        return PageResponse.from(notificationPage, notificationPage.getContent().stream()
                .map(this::toNotificationResponse)
                .toList());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID accountId) {
        long count = notificationRepository.countByAccountUser_IdAndIsReadFalse(accountId);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID accountId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.getAccountUser().getId().equals(accountId)) {
            throw new IllegalArgumentException("You don't have permission to mark this notification as read");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID accountId) {
        notificationRepository.markAllAsReadByAccountId(accountId);
    }

    private NotificationResponse toNotificationResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.isRead())
                .type(notification.getType())
                .referenceId(notification.getReferenceId())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
