package jnm.engineer.demo.security;

import jnm.engineer.demo.services.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * WHO CAN DO WHAT (rules are checked top to bottom; the first match wins)
 *
 *   Anyone ............ login (everything under /api/auth/ except the two below), CORS pre-flight,
 *                       school name/motto/logos (GET /api/settings/public)
 *   Logged in ......... change their own password; READ anything (GET)
 *   ADMIN, TEACHER, CLERK .. save marks            (/api/results/**)
 *   ADMIN, TEACHER, CLERK .. save report cards     (/api/reportCards/**)  — clerk = school secretary
 *   ADMIN, TEACHER .... take the register          (/api/attendance/**)
 *   ADMIN, TEACHER .... arrange cover for marks    (/api/mark-cover/**)
 *   ADMIN, TEACHER, CLERK .. add / edit students   (/api/students/**, except delete)
 *   ADMIN, ACCOUNTANT . everything under /api/finance/ and /api/transport/ (reading too)
 *   + TEACHER ......... /api/class-services/ (tick meals/trips/transport: own class, current term)
 *   ADMIN only ........ create users (/api/auth/register), everything under /api/users/,
 *                       deleting students, and ALL other changes: classes, subjects,
 *                       exams, teachers, schedules, grading scales, academic years,
 *                       class subjects, teaching assignments
 *
 * Step 1b (next) will add "teachers only for their own classes" on top of this.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // lets controllers use @PreAuthorize for the finer, per-class rules in step 1b
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtFilter jwtFilter;

    // Env var CORS_ALLOWED_ORIGINS can override
    @Value("${CORS_ALLOWED_ORIGINS:https://papsxms-frontend.vercel.app,http://localhost:3000,http://172.16.0.180:8088,https://papsxms.somwaki.com}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())   // fine: stateless JWT in a header, no cookies
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**").permitAll()
                        // Spring's error page: lets 403/404 messages from controllers reach the browser
                        .requestMatchers("/error").permitAll()
                        // School name, motto and logos for the login page
                        .requestMatchers(HttpMethod.GET, "/api/settings/public").permitAll()

                        // ── /api/auth: close the two holes, keep login open ──
                        .requestMatchers("/api/auth/register", "/api/auth/register/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/change-password", "/api/auth/change-password/**").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── user accounts: admin only (reading too) ──
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // ── finance: admin and bursar only (reading too) ──
                        .requestMatchers("/api/finance/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/transport/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        // Meals & Transport ticking: class teachers (own class, current term — checked in the service)
                        .requestMatchers("/api/class-services/**").hasAnyRole("ADMIN", "ACCOUNTANT", "TEACHER")

                        // ── notices: posting and "who has read" are admin only; anyone may mark as read ──
                        .requestMatchers("/api/announcements/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/announcements/*/read").authenticated()

                        // ── reading: any logged-in user (per-class limits come in step 1b) ──
                        .requestMatchers(HttpMethod.GET, "/**").authenticated()

                        // ── changes ──
                        .requestMatchers("/api/results/**").hasAnyRole("ADMIN", "TEACHER", "CLERK")
                        .requestMatchers("/api/reportCards/**").hasAnyRole("ADMIN", "TEACHER", "CLERK")
                        .requestMatchers("/api/attendance/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/mark-cover/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/students/**").hasRole("ADMIN")
                        .requestMatchers("/api/students/**").hasAnyRole("ADMIN", "TEACHER", "CLERK")

                        // everything else that changes data
                        .anyRequest().hasRole("ADMIN")
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Clear JSON answers instead of blank pages: 401 = not logged in, 403 = not allowed
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"status\":401,\"message\":\"Please log in.\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"status\":403,\"message\":\"You don't have permission to do this.\"}");
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JwtFilter is a @Component, so Spring Boot would ALSO register it as an ordinary
     * servlet filter outside the security chain. This switches that copy off so it
     * only ever runs inside the chain above, in the right place.
     */
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterServletRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return request -> configuration;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
