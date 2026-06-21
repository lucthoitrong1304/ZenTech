package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.BusinessEvent;
import hcmute.edu.zentech.model.BusinessEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BusinessEventRepository extends JpaRepository<BusinessEvent, UUID> {

    List<BusinessEvent> findByEventTypeAndCreatedAtBetween(
            BusinessEventType eventType,
            Instant start,
            Instant end
    );

    long countByEventTypeAndCreatedAtBetween(
            BusinessEventType eventType,
            Instant start,
            Instant end
    );

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0.0)
            FROM BusinessEvent e
            WHERE e.eventType = :eventType
              AND e.createdAt >= :start
              AND e.createdAt <= :end
            """)
    double sumAmountByEventTypeAndCreatedAtBetween(
            @Param("eventType") BusinessEventType eventType,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            SELECT COUNT(DISTINCT COALESCE(e.traceId, CAST(e.userId AS string)))
            FROM BusinessEvent e
            WHERE e.createdAt >= :start
              AND e.createdAt <= :end
            """)
    long countAffectedUsersBetween(
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
