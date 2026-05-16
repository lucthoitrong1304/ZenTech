package hcmute.edu.zentech.repository;

import hcmute.edu.zentech.model.AccountUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountUserRepository extends JpaRepository<AccountUser, UUID> {
    Optional<AccountUser> findByEmail(String email);
    Optional<AccountUser> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
}
