package jnm.engineer.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    public enum Role {
        ADMIN, TEACHER, CLERK, ACCOUNTANT   // ACCOUNTANT = bursar (finance only)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @NotBlank(message = "Username is required")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // SECURITY: can be RECEIVED in a request but is never SENT back in any response,
    // so GET /api/users no longer leaks password hashes to the browser.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @NotNull(message = "Role is required")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // plain text column, not a MySQL ENUM — new values need no ALTER TABLE
    @Column(nullable = false, length = 20)
    private Role role;

    // OLD loose link (kept only so the migration script can read it). Use `teacher` instead.
    @Column
    private Long LinkedId;

    // The teacher record this login belongs to. One login per teacher (unique).
    @JsonIgnoreProperties({"subjects", "hibernateLazyInitializer", "handler"})
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", unique = true)
    private Teacher teacher;

    @Column(nullable = false)
    private boolean mustChangePassword = false;

    // ── Lockout after repeated wrong passwords (managed by AuthController) ──
    // Nullable so the new columns can be added to the existing users table safely.
    @JsonIgnore
    @Column
    private Integer failedLoginAttempts;

    // When the password was last changed or reset. Login tokens issued BEFORE this are
    // refused, so changing a password ends every older session.
    @JsonIgnore
    @Column
    private LocalDateTime passwordChangedAt;

    // When set and still in the future, logins are refused. Visible (read-only) to the admin.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column
    private LocalDateTime lockedUntil;

    // Kept in step with SchoolClass.classTeacher automatically (Users, Classes and Teachers pages).
    @JsonIgnoreProperties({"studentList", "classTeacher", "hibernateLazyInitializer", "handler", "subjects"})
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "linked_class_id")
    private SchoolClass linkedClass;
}
