package hcmute.edu.zentech.security;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.model.PermissionCode;
import hcmute.edu.zentech.model.Role;
import hcmute.edu.zentech.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountUserRepository accountUserRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AccountUser account = accountUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại: " + email));

        var permissions = account.getRole() == Role.ADMIN
                ? java.util.EnumSet.allOf(PermissionCode.class)
                : rolePermissionRepository.findPermissionCodesByRole(account.getRole());

        return CustomUserDetails.build(
                account,
                permissions.stream().map(Enum::name).toList()
        );
    }
}
