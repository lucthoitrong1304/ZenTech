package hcmute.edu.zentech.security.service;

import hcmute.edu.zentech.model.AccountUser;
import hcmute.edu.zentech.repository.AccountUserRepository;
import hcmute.edu.zentech.security.model.CustomUserDetails;
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
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AccountUser user = accountUserRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
        
        return new CustomUserDetails(user);
    }
}
