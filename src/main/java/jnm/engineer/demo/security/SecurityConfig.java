package jnm.engineer.demo.security;

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
                        // The website itself (pages, scripts, images) when the backend serves it: anything that is NOT /api/.
                        // All data stays under /api/ and keeps the rules below.
                        .requestMatchers(request -> "GET".equals(request.getMethod()) && !request.getRequestURI().startsWith("/api/")).permitAll()
                        // School name, motto and logos for the login page
                        .requestMatchers(HttpMethod.GET, "/api/settings/public").permitAll()
                        // Public website: fee structures and the admission/contact form
                        .requestMatchers("/api/public/**").permitAll()
                        // Website enquiries inbox: admin and clerk
                        .requestMatchers("/api/enquiries", "/api/enquiries/**").hasAnyRole("ADMIN", "CLERK")
                        .requestMatchers("/api/admissions", "/api/admissions/**").hasAnyRole("ADMIN", "CLERK")
                        .requestMatchers("/api/site-posts", "/api/site-posts/**").hasAnyRole("ADMIN", "CLERK")
                        .requestMatchers("/api/job-applications", "/api/job-applications/**").hasAnyRole("ADMIN", "HEAD_TEACHER")

                        // ── /api/auth: close the two holes, keep login open ──
                        .requestMatchers("/api/auth/register", "/api/auth/register/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/change-password", "/api/auth/change-password/**").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── user accounts: admin only (reading too) ──
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // ── finance: admin and bursar only (reading too) ──
                        // Treasurer approves the bills the bursar prepares (only the treasurer)
                        .requestMatchers("/api/finance/expenses/*/approve", "/api/finance/expenses/*/reject").hasRole("TREASURER")
                        // …approves bills in bulk and raises / cancels the cheques that pay them (only the treasurer)
                        .requestMatchers(HttpMethod.POST, "/api/finance/expenses/approve", "/api/finance/cheques", "/api/finance/cheques/*/cancel",
                                "/api/finance/cheques/float", "/api/finance/float/adjust").hasRole("TREASURER")
                        // Chairperson oversees: may READ finance, payroll and transport (never change them)
                        // A learner's fee statement: also class teachers (own class only — checked in FinanceController)
                        .requestMatchers(HttpMethod.GET, "/api/finance/students/*/statement").hasAnyRole("ADMIN", "ACCOUNTANT", "CHAIRPERSON", "TEACHER")
                        // School overview (head teacher): read-only summaries
                        .requestMatchers(HttpMethod.GET, "/api/finance/balances").hasAnyRole("ADMIN", "ACCOUNTANT", "CHAIRPERSON")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/school-day/**").hasAnyRole("ADMIN", "TEACHER", "HEAD_TEACHER", "CHAIRPERSON")
                        // Payroll (salaries, payslips, bank file): the treasurer only — viewing and running it
                        .requestMatchers("/api/payroll", "/api/payroll/**").hasRole("TREASURER")
                        .requestMatchers(HttpMethod.GET, "/api/finance/**", "/api/transport/**").hasAnyRole("ADMIN", "ACCOUNTANT", "CHAIRPERSON")
                        // Parents / guardians: the office (admin, clerk, bursar/treasurer)
                        .requestMatchers("/api/guardians", "/api/guardians/**").hasAnyRole("ADMIN", "CLERK", "ACCOUNTANT")
                        // SMS account settings, test and log: admin only (sending reminders is under /api/reminders)
                        .requestMatchers("/api/sms", "/api/sms/**").hasRole("ADMIN")
                        // Fee reminders: admin and bursar/treasurer
                        .requestMatchers("/api/reminders", "/api/reminders/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        // Duty roster: anyone logged in may see it; the head teacher or an admin set it
                        .requestMatchers(HttpMethod.GET, "/api/duty/**").authenticated()
                        .requestMatchers("/api/duty/**").hasAnyRole("ADMIN", "HEAD_TEACHER")
                        // Staff attendance: own sign-in for everyone; the register for the office
                        .requestMatchers("/api/staff-attendance/mine", "/api/staff-attendance/mine/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/staff-attendance/**").hasAnyRole("ADMIN", "HEAD_TEACHER", "CLERK", "CHAIRPERSON", "SENIOR_TEACHER")
                        .requestMatchers("/api/staff-attendance/**").hasAnyRole("ADMIN", "HEAD_TEACHER", "CLERK")
                        // Leave: every staff member manages their OWN leave; the rest is the office (admin), chairperson may read
                        .requestMatchers("/api/leave/mine", "/api/leave/mine/**").authenticated()
                        // Who approves leave: the head teacher (role ticked by the super admin) or an admin
                        .requestMatchers("/api/leave/requests/*/approve", "/api/leave/requests/*/reject").hasAnyRole("ADMIN", "HEAD_TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/leave/**").hasAnyRole("ADMIN", "CHAIRPERSON", "HEAD_TEACHER", "SENIOR_TEACHER")
                        .requestMatchers("/api/leave/**").hasRole("ADMIN")
                        // Staff register: personal details (ID numbers…) — admin; the chairperson may read
                        .requestMatchers(HttpMethod.GET, "/api/staff/**").hasAnyRole("ADMIN", "CHAIRPERSON", "HEAD_TEACHER", "SENIOR_TEACHER")
                        .requestMatchers("/api/staff/**").hasRole("ADMIN")
                        .requestMatchers("/api/finance/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        // Payroll: salaries are private — admin and bursar only, reading included

                        .requestMatchers("/api/transport/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        // Meals & Transport ticking: class teachers (own class, current term — checked in the service)
                        .requestMatchers("/api/class-services/**").hasAnyRole("ADMIN", "ACCOUNTANT", "TEACHER")

                        // ── notices: posting and "who has read" are admin only; anyone may mark as read ──
                        .requestMatchers("/api/announcements/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/announcements/*/read").authenticated()

                        // ── reading: any logged-in user (per-class limits come in step 1b) ──
                        .requestMatchers(HttpMethod.GET, "/**").authenticated()

                        // ── changes ──
                        .requestMatchers("/api/invigilation", "/api/invigilation/**").hasAnyRole("ADMIN", "TEACHER")
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
