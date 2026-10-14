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
        ADMIN, TEACHER, CLERK, ACCOUNTANT,   // ACCOUNTANT = bursar (prepares finance)
        TREASURER,                           // head of the bursar: everything the bursar can do + approves bills
        CHAIRPERSON,                         // oversees the school: can view everything, change nothing
        SUPER_ADMIN,                         // everything an admin can do + ticks which roles each user has
        HEAD_TEACHER,                        // approves staff leave; sees the staff register and leave (no editing)
        DEPUTY_HEAD,                         // deputy head teacher: everything the head teacher can do
        SENIOR_TEACHER                       // a teacher who also sees the School Overview
    }

    /**
     * More roles ticked for this person by the super admin, as a comma list (e.g. "TREASURER,TEACHER").
     * The main `role` above decides what they see first; these add permissions.
     */
    @Column(length = 200)
    private String extraRoles;

    /** Every role this person effectively has: main + extra, plus what each implies. */
    public java.util.Set<Role> effectiveRoles() {
        java.util.Set<Role> out = java.util.EnumSet.noneOf(Role.class);
        if (role != null) out.add(role);
        if (extraRoles != null) {
            for (String r : extraRoles.split(",")) {
                try { if (!r.isBlank()) out.add(Role.valueOf(r.trim().toUpperCase())); } catch (IllegalArgumentException ignored) { /* old value */ }
            }
        }
        boolean headTicked = out.contains(Role.HEAD_TEACHER);              // ticked itself, not via deputy
        if (out.contains(Role.SUPER_ADMIN)) out.add(Role.ADMIN);          // a super admin is also an admin
        if (out.contains(Role.TREASURER)) out.add(Role.ACCOUNTANT);       // the treasurer can do the bursar's work
        if (out.contains(Role.DEPUTY_HEAD)) out.add(Role.HEAD_TEACHER);   // the deputy can do the head teacher's work
        // Only the head teacher (ticked as such, not the deputy) also gets the chairperson's read-only
        // finance view. Deputy and senior teacher see academics and staff, but no finance.
        if (headTicked) out.add(Role.CHAIRPERSON);
        if (out.contains(Role.SENIOR_TEACHER)) out.add(Role.TEACHER);     // a senior teacher is a teacher
        return out;
    }

    public boolean hasRole(Role r) { return effectiveRoles().contains(r); }

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
