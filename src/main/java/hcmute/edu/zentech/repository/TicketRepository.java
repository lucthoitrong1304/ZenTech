package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketStatus;
import hcmute.edu.zentech.model.TicketPriority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    Optional<Ticket> findByCode(String code);

    Optional<Ticket> findByIncidentId(UUID incidentId);

    @Query("SELECT t FROM Ticket t " +
           "LEFT JOIN t.assignee a " +
           "LEFT JOIN t.createdBy c " +
           "WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:assigneeEmail IS NULL OR :assigneeEmail = '' OR " +
           "  (:assigneeEmail = 'UNASSIGNED' AND a IS NULL) OR " +
           "  (a.email = :assigneeEmail)) AND " +
           "(:startDate IS NULL OR t.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR t.createdAt <= :endDate) AND " +
           "(:search IS NULL OR :search = '' OR " +
           "  LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(a.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Ticket> searchTickets(
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("assigneeEmail") String assigneeEmail,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(t) FROM Ticket t")
    long countAllTickets();
}
