package jnm.engineer.demo.services;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.UserRepository;
import jnm.engineer.demo.security.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final PasswordEncoder passwordEncoder;

    public void assignClassToUser(Long userId, Long classId) {
        User user = getById(userId);
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        user.setLinkedClass(schoolClass);
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    public List<User> getByRole(User.Role role) {
        return userRepository.findByRole(role);
    }

    /** NOTE: stores passwordHash exactly as sent. The Users page uses /api/auth/register instead. */
    public User create(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username " + user.getUsername() + " already exists.");
        }
        return userRepository.save(user);
    }

    public User update(Long id, User updated) {
        User existing = getById(id);
        existing.setUsername(updated.getUsername());
        existing.setRole(updated.getRole());
        existing.setLinkedId(updated.getLinkedId());
        return userRepository.save(existing);
    }

    /**
     * @deprecated stores the given value as the hash without encoding it. Nothing calls this
     * any more — UserController's reset-password encodes properly. Kept only so old code compiles.
     */
    @Deprecated
    public User changePassword(Long id, String newPasswordHash) {
        User existing = getById(id);
        existing.setPasswordHash(newPasswordHash);
        existing.setMustChangePassword(false);
        return userRepository.save(existing);
    }

    public void delete(Long id) {
        getById(id);
        userRepository.deleteById(id);
    }

    /** Result of creating/updating a teacher login. temporaryPassword is set ONLY when a new login was created. */
    public record TeacherLogin(User user, String temporaryPassword) {}

    /**
     * Auto-create or update the TEACHER login when a teacher becomes a class teacher.
     * Username = the teacher's phone number. A NEW login gets a random temporary password
     * (returned once so the admin can pass it on) and must change it at first login.
     *
     * Step-2 changes:
     *  - the login is now LINKED to its teacher record (it never was before)
     *  - a login already linked to this teacher is reused, even if the phone number changed
     *  - an ADMIN or CLERK account that happens to use this username is never turned into a teacher
     */
    public TeacherLogin createOrUpdateTeacherUser(Teacher teacher, SchoolClass schoolClass) {
        // 1. A login already linked to this teacher — just point it at the class
        Optional<User> linked = userRepository.findAll().stream()
                .filter(u -> u.getTeacher() != null && u.getTeacher().getTeacherId().equals(teacher.getTeacherId()))
                .findFirst();
        if (linked.isPresent()) {
            User u = linked.get();
            u.setLinkedClass(schoolClass);
            return new TeacherLogin(userRepository.save(u), null);
        }

        String username = teacher.getPhone().trim();
        return userRepository.findByUsername(username)
                .map(existing -> {
                    if (existing.getRole() != User.Role.TEACHER) {
                        return new TeacherLogin(existing, null);   // never demote an admin / clerk
                    }
                    existing.setLinkedClass(schoolClass);
                    if (existing.getTeacher() == null) { // link it now
                        existing.setTeacher(teacher);
                        existing.setLinkedId(teacher.getTeacherId());
                    }
                    return new TeacherLogin(userRepository.save(existing), null);
                })
                .orElseGet(() -> {
                    String temporary = PasswordGenerator.temporary();          // NOT the phone number any more
                    User newUser = new User();
                    newUser.setUsername(username);
                    newUser.setPasswordHash(passwordEncoder.encode(temporary));
                    newUser.setPasswordChangedAt(LocalDateTime.now().withNano(0));
                    newUser.setRole(User.Role.TEACHER);
                    newUser.setLinkedClass(schoolClass);
                    newUser.setTeacher(teacher);                               // NEW: proper link
                    newUser.setLinkedId(teacher.getTeacherId());
                    newUser.setMustChangePassword(true);
                    return new TeacherLogin(userRepository.save(newUser), temporary);
                });
    }
}
