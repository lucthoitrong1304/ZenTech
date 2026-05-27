package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.ProductGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductGroupRepository extends JpaRepository<ProductGroup, UUID> {
    Optional<ProductGroup> findByGroupName(String groupName);
    boolean existsByGroupName(String groupName);
    Optional<ProductGroup> findByGroupNameAndDeletedFalse(String groupName);

    Optional<ProductGroup> findByIdAndDeletedFalse(UUID groupId);

    @Query(
            value = """
                    select pg
                    from ProductGroup pg
                    where (:includeDeleted = true or pg.deleted = false)
                    and (:keyword is null
                         or lower(pg.groupName) like lower(concat('%', :keyword, '%'))
                         or lower(pg.description) like lower(concat('%', :keyword, '%')))
                    """,
            countQuery = """
                    select count(pg)
                    from ProductGroup pg
                    where (:includeDeleted = true or pg.deleted = false)
                    and (:keyword is null
                         or lower(pg.groupName) like lower(concat('%', :keyword, '%'))
                         or lower(pg.description) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<ProductGroup> searchManagementGroups(
            @Param("keyword") String keyword,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable);

    @Query("""
            select count(pg) > 0
            from ProductGroup pg
            where lower(pg.groupName) = lower(:groupName)
            and pg.deleted = false
            and (:groupId is null or pg.id <> :groupId)
            """)
    boolean existsActiveGroupNameExcludingId(
            @Param("groupName") String groupName,
            @Param("groupId") UUID groupId);
}
