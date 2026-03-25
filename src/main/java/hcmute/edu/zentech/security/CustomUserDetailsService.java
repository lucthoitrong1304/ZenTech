package hcmute.edu.zentech.security;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.repository.AccountUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountUserRepository accountUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AccountUser account = accountUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại: " + email));

        return CustomUserDetails.build(account);
    }
}