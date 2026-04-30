package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Conversation;
import hcmute.edu.zentech.model.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    @EntityGraph(attributePaths = {"customer", "customer.userInfo"})
    Optional<Conversation> findFirstByCustomer_IdAndStatusInOrderByUpdatedAtDesc(
            UUID customerId,
            Collection<ConversationStatus> statuses
    );

    @EntityGraph(attributePaths = {"customer", "customer.userInfo"})
    Page<Conversation> findByCustomer_IdOrderByUpdatedAtDesc(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "customer.userInfo"})
    @Query(
            value = """
                    SELECT c
                    FROM Conversation c
                    JOIN c.customer customer
                    JOIN customer.userInfo account
                    WHERE (:status IS NULL OR c.status = :status)
                      AND (:keyword IS NULL
                        OR LOWER(customer.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(account.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM Conversation c
                    JOIN c.customer customer
                    JOIN customer.userInfo account
                    WHERE (:status IS NULL OR c.status = :status)
                      AND (:keyword IS NULL
                        OR LOWER(customer.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(account.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
                    """
    )
    Page<Conversation> searchOwnerConversations(
            @Param("status") ConversationStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
