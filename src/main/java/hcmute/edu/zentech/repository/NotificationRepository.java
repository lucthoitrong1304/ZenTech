package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByAccountUser_IdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    long countByAccountUser_IdAndIsReadFalse(UUID accountId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.accountUser.id = :accountId AND n.isRead = false")
    void markAllAsReadByAccountId(@Param("accountId") UUID accountId);
}
