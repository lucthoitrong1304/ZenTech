package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AiAgent;
import hcmute.edu.zentech.model.AiAgentStatus;
import hcmute.edu.zentech.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiAgentRepository extends JpaRepository<AiAgent, UUID> {
    @Query("""
            select distinct a
            from AiAgent a
            left join fetch a.datasets
            where a.deleted = false
            order by a.updatedAt desc
            """)
    List<AiAgent> findAllActiveRecords();

    @Query("""
            select distinct a
            from AiAgent a
            left join fetch a.datasets
            where a.id = :agentId
            and a.deleted = false
            """)
    Optional<AiAgent> findDetailById(@Param("agentId") UUID agentId);

    @Query("""
            select distinct a
            from AiAgent a
            left join fetch a.datasets
            where a.deleted = false
            and a.status = :status
            and a.assignedRole = :role
            order by a.priority desc, a.updatedAt desc
            """)
    List<AiAgent> findRuntimeCandidates(
            @Param("role") Role role,
            @Param("status") AiAgentStatus status
    );

    @Query("""
            select count(a) > 0
            from AiAgent a
            where a.deleted = false
            and a.status = :status
            and a.assignedRole = :role
            and (:agentId is null or a.id <> :agentId)
            """)
    boolean existsOtherActiveAgentForRole(
            @Param("role") Role role,
            @Param("agentId") UUID agentId,
            @Param("status") AiAgentStatus status
    );
}
