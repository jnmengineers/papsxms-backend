package jnm.engineer.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.services.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;

/**
 * Reads "Authorization: Bearer <token>" and logs the request in as that user.
 *
 *  - Expired / damaged / wrongly-signed token          → 401 "session expired"
 *  - Token for a deleted or disabled account            → 401
 *  - Token issued BEFORE the password was last changed  → 401 "password was changed"
 *    (changing or resetting a password logs out every other device)
 *  - Account that must change its password first        → 403 for everything except
 *    /api/auth/change-password (the page can't be skipped by typing another address)
 *  - No token at all                                    → carries on; SecurityConfig decides
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7).trim();

        try {
            String username = jwtUtil.extractUsername(token);   // throws if expired/damaged

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username); // throws if deleted

                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    reject(response, 401, "This account has been disabled. Contact the administrator.");
                    return;
                }
                if (!jwtUtil.isTokenValid(token, userDetails.getUsername())) {
                    reject(response, 401, "Your session has expired. Please log in again.");
                    return;
                }

                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    // Password changed/reset after this token was issued → this session is over
                    if (user.getPasswordChangedAt() != null) {
                        Date issuedAt = jwtUtil.extractIssuedAt(token);
                        long changedAt = user.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
                        if (issuedAt == null || issuedAt.toInstant().getEpochSecond() < changedAt) {
                            reject(response, 401, "Your password was changed. Please log in again.");
                            return;
                        }
                    }
                    // Must change password first: only the change-password call is allowed
                    if (user.isMustChangePassword() && !CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
                        reject(response, 403, "You must change your password before continuing.");
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            reject(response, 401, "This account no longer exists. Please log in again.");
            return;
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            reject(response, 401, "Your session has expired. Please log in again.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}
