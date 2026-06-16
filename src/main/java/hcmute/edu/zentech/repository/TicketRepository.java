package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Ticket;
import hcmute.edu.zentech.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    Optional<Ticket> findByCode(String code);

    Optional<Ticket> findByIncidentId(UUID incidentId);

    @Query("SELECT COUNT(t) FROM Ticket t")
    long countAllTickets();
}
