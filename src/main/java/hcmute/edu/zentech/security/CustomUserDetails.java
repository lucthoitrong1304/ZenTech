package hcmute.edu.zentech.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hcmute.edu.zentech.model.AccountUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

@Data
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {
    private UUID id;
    private String email;

    @JsonIgnore
    private String password;

    private boolean isActive;

    private Collection<? extends GrantedAuthority> authorities;

    public static CustomUserDetails build(AccountUser account) {
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + account.getRole().name());

        return new CustomUserDetails(
                account.getId(),
                account.getEmail(),
                account.getPassword(),
                account.isActive(),
                Collections.singletonList(authority)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return email; } // Trả về email cho Spring Security dùng làm định danh

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isActive; }
}