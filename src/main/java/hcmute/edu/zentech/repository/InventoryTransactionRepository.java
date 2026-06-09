package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.InventoryTransaction;
import hcmute.edu.zentech.model.InventoryTransactionType;
import hcmute.edu.zentech.model.InventoryTransactionReason;
import hcmute.edu.zentech.repository.projection.TransactionStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    @Query(
            value = """
                    select t
                    from InventoryTransaction t
                    join fetch t.productVariant v
                    join fetch v.product p
                    where (:type is null or t.type = :type)
                    and (:employeeId is null or t.createdBy = :employeeId)
                    and (:reason is null or t.reason = :reason)
                    and (:startDate is null or t.createdAt >= :startDate)
                    and (:endDate is null or t.createdAt <= :endDate)
                    and (:keyword is null
                         or lower(p.productName) like lower(concat('%', :keyword, '%'))
                         or lower(v.name) like lower(concat('%', :keyword, '%'))
                         or lower(t.note) like lower(concat('%', :keyword, '%')))
                    """,
            countQuery = """
                    select count(t)
                    from InventoryTransaction t
                    join t.productVariant v
                    join v.product p
                    where (:type is null or t.type = :type)
                    and (:employeeId is null or t.createdBy = :employeeId)
                    and (:reason is null or t.reason = :reason)
                    and (:startDate is null or t.createdAt >= :startDate)
                    and (:endDate is null or t.createdAt <= :endDate)
                    and (:keyword is null
                         or lower(p.productName) like lower(concat('%', :keyword, '%'))
                         or lower(v.name) like lower(concat('%', :keyword, '%'))
                         or lower(t.note) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<InventoryTransaction> searchTransactions(
            @Param("keyword") String keyword,
            @Param("type") InventoryTransactionType type,
            @Param("employeeId") UUID employeeId,
            @Param("reason") InventoryTransactionReason reason,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    @Query("""
            select 
                coalesce(sum(case when t.type = 'IMPORT' then t.quantity else 0 end), 0) as totalImports,
                coalesce(sum(case when t.type = 'EXPORT' then t.quantity else 0 end), 0) as totalExports,
                count(t) as totalCount
            from InventoryTransaction t
            join t.productVariant v
            join v.product p
            where (:type is null or t.type = :type)
            and (:employeeId is null or t.createdBy = :employeeId)
            and (:reason is null or t.reason = :reason)
            and (:startDate is null or t.createdAt >= :startDate)
            and (:endDate is null or t.createdAt <= :endDate)
            and (:keyword is null
                 or lower(p.productName) like lower(concat('%', :keyword, '%'))
                 or lower(v.name) like lower(concat('%', :keyword, '%'))
                 or lower(t.note) like lower(concat('%', :keyword, '%')))
            """)
    TransactionStatsProjection getTransactionStats(
            @Param("keyword") String keyword,
            @Param("type") InventoryTransactionType type,
            @Param("employeeId") UUID employeeId,
            @Param("reason") InventoryTransactionReason reason,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
            select coalesce(sum(t.quantity), 0)
            from InventoryTransaction t
            where t.productVariant.id = :variantId
            and t.reason = :reason
            and t.createdAt >= :startDate
            """)
    long sumQuantityByVariantAndReasonAndDate(
            @Param("variantId") UUID variantId,
            @Param("reason") hcmute.edu.zentech.model.InventoryTransactionReason reason,
            @Param("startDate") java.time.Instant startDate
    );
}
