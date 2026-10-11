package jnm.engineer.demo.security;

import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a login with ALL its roles: the main role, the extra roles ticked by the super admin,
 * and what they imply (SUPER_ADMIN → also ADMIN; TREASURER → also ACCOUNTANT).
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        List<SimpleGrantedAuthority> authorities = user.effectiveRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPasswordHash(), authorities);
    }
}
