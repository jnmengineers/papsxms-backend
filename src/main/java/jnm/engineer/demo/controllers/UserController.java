package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User accounts — ADMIN only (SecurityConfig: /api/users/**).
 *
 * New in step 2:
 *   PATCH  /{id}/link-teacher/{teacherId}   link this login to its teacher record
 *   DELETE /{id}/link-teacher               unlink
 *   PATCH  /{id}/reset-password             admin sets a temporary password (+ unlocks)
 *   PATCH  /{id}/unlock                     clear a lockout early
 *   assign-class now also makes the linked teacher the class teacher (one source of truth)
 *
 * Removed: the old change-password endpoint that stored whatever "passwordHash" it was
 * sent. That path now does the safe reset instead.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserService userService;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<User>> getAll() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/by-role")
    public ResponseEntity<List<User>> getByRole(@RequestParam User.Role role) {
        return ResponseEntity.ok(userService.getByRole(role));
    }

    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id, @Valid @RequestBody User user) {
        return ResponseEntity.ok(userService.update(id, user));
    }

    // ── Passwords & lockout ──────────────────────────────────────────────────
    /** Admin sets a temporary password; the person must choose their own at next login. */
    @PatchMapping({"/{id}/reset-password", "/{id}/change-password"})
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = find(id);
        String password = body.getOrDefault("newPassword", "");
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return error(HttpStatus.BAD_REQUEST, "The temporary password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.equalsIgnoreCase(user.getUsername())) {
            return error(HttpStatus.BAD_REQUEST, "The password cannot be the same as the username.");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(java.time.LocalDateTime.now().withNano(0));   // logs them out everywhere
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password reset for \"" + user.getUsername() + "\". They must choose a new one when they log in."));
    }

    @PatchMapping("/{id}/unlock")
    public ResponseEntity<?> unlock(@PathVariable Long id) {
        User user = find(id);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "\"" + user.getUsername() + "\" can log in again."));
    }

    // ── Link login ↔ teacher record ──────────────────────────────────────────
    @PatchMapping("/{userId}/link-teacher/{teacherId}")
    @Transactional
    public ResponseEntity<?> linkTeacher(@PathVariable Long userId, @PathVariable Long teacherId) {
        User user = find(userId);
        Teacher teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null) return error(HttpStatus.NOT_FOUND, "Teacher not found.");

        Optional<User> other = userRepository.findAll().stream()
                .filter(u -> !u.getUserId().equals(userId) && u.getTeacher() != null
                        && u.getTeacher().getTeacherId().equals(teacherId))
                .findFirst();
        if (other.isPresent()) {
            return error(HttpStatus.CONFLICT, teacher.getFirstName() + " " + teacher.getLastName()
                    + " is already linked to the login \"" + other.get().getUsername() + "\". Unlink that one first.");
        }

        user.setTeacher(teacher);
        user.setLinkedId(teacherId);           // keep the old field in step for now
        String note = "";

        // Line up "which class" in both places
        List<SchoolClass> classTeacherOf = schoolClassRepository.findByClassTeacherTeacherId(teacherId);
        if (!classTeacherOf.isEmpty()) {
            user.setLinkedClass(classTeacherOf.get(0));           // login follows the class-teacher setting
            note = " Their login now manages " + classTeacherOf.get(0).getClassName() + ".";
        } else if (user.getLinkedClass() != null) {
            SchoolClass cls = user.getLinkedClass();
            if (cls.getClassTeacher() == null) {
                cls.setClassTeacher(teacher);                       // class had no class teacher → fill it
                schoolClassRepository.save(cls);
                note = " They are now class teacher of " + cls.getClassName() + ".";
            } else {
                note = " Note: " + cls.getClassName() + " already has " + cls.getClassTeacher().getFirstName() + " "
                        + cls.getClassTeacher().getLastName() + " as class teacher — check the Classes page.";
            }
        }
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "\"" + user.getUsername() + "\" is linked to "
                + teacher.getFirstName() + " " + teacher.getLastName() + "." + note));
    }

    @DeleteMapping("/{userId}/link-teacher")
    public ResponseEntity<?> unlinkTeacher(@PathVariable Long userId) {
        User user = find(userId);
        user.setTeacher(null);
        user.setLinkedId(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "\"" + user.getUsername() + "\" is no longer linked to a teacher."));
    }

    // ── Class ────────────────────────────────────────────────────────────────
    /** Sets the login's class AND (if linked) makes that teacher the class teacher. */
    @PatchMapping("/{userId}/assign-class/{classId}")
    @Transactional
    public ResponseEntity<?> assignClass(@PathVariable Long userId, @PathVariable Long classId) {
        userService.assignClassToUser(userId, classId);
        User user = find(userId);
        if (user.getTeacher() != null) {
            Long teacherId = user.getTeacher().getTeacherId();
            // Moving class: stop being class teacher of the old one
            for (SchoolClass c : schoolClassRepository.findByClassTeacherTeacherId(teacherId)) {
                if (!c.getClassId().equals(classId)) {
                    c.setClassTeacher(null);
                    schoolClassRepository.save(c);
                }
            }
            SchoolClass cls = schoolClassRepository.findById(classId)
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            cls.setClassTeacher(user.getTeacher());
            schoolClassRepository.save(cls);
        }
        return ResponseEntity.ok(Map.of("message", "Class assigned successfully"));
    }

    // ── Delete ───────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails me) {
        User user = find(id);
        if (me != null && user.getUsername().equalsIgnoreCase(me.getUsername())) {
            return error(HttpStatus.BAD_REQUEST, "You can't delete the account you're logged in with.");
        }
        if (user.getRole() == User.Role.ADMIN
                && userService.getByRole(User.Role.ADMIN).size() <= 1) {
            return error(HttpStatus.BAD_REQUEST, "You can't delete the last admin.");
        }
        userService.delete(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message));
    }
}
