package jnm.engineer.demo.security;

import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** Who holds which role — for the rules that depend on the people in the school, not just the request. */
@Component
@RequiredArgsConstructor
public class RoleGuard {
    /** Roles only a super admin may give (except while the school has no super admin yet). */
    public static final Set<User.Role> RESERVED = EnumSet.of(User.Role.SUPER_ADMIN, User.Role.TREASURER, User.Role.CHAIRPERSON);

    private final UserRepository userRepository;

    /** Does the person making this request have the role (directly or implied)? */
    public boolean current(User.Role r) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + r.name()));
    }

    public boolean anyoneHas(User.Role r) {
        return userRepository.findAll().stream().anyMatch(u -> u.hasRole(r));
    }

    /** Bills need the treasurer's approval once the school has a treasurer. */
    public boolean treasurerApprovalOn() { return anyoneHas(User.Role.TREASURER); }

    /** A super admin may give roles; while nobody is super admin yet, an admin may (to set the first one up). */
    public boolean mayManageRoles() {
        return current(User.Role.SUPER_ADMIN) || (current(User.Role.ADMIN) && !anyoneHas(User.Role.SUPER_ADMIN));
    }
}
