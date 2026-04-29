package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByUserInfo_Id(UUID accountId);
    @EntityGraph(attributePaths = {"userInfo"})
    @Query(
            value = """
                    SELECT c
                    FROM Customer c
                    JOIN c.userInfo u
                    WHERE (:keyword IS NULL
                        OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:active IS NULL OR u.isActive = :active)
                    """,
            countQuery = """
                    SELECT COUNT(c)
                    FROM Customer c
                    JOIN c.userInfo u
                    WHERE (:keyword IS NULL
                        OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:active IS NULL OR u.isActive = :active)
                    """
    )
    Page<Customer> searchCustomers(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"userInfo", "addressList"})
    @Query("SELECT c FROM Customer c WHERE c.id = :customerId")
    Optional<Customer> findDetailById(@Param("customerId") UUID customerId);
}
