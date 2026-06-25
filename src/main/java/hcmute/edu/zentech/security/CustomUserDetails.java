package hcmute.edu.zentech.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import hcmute.edu.zentech.model.AccountUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
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
        return build(account, Set.of());
    }

    public static CustomUserDetails build(AccountUser account, Collection<String> permissions) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
        permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        return new CustomUserDetails(
                account.getId(),
                account.getEmail(),
                account.getPassword(),
                account.isActive(),
                authorities
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
