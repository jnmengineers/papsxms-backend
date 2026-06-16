package jnm.engineer.demo.services;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final PasswordEncoder passwordEncoder;

    public void assignClassToUser(Long userId, Long classId){
        User user = getById(userId);
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        user.setLinkedClass(schoolClass);
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getById(Long id){
        return userRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("User not found with id: " + id));
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    public List<User> getByRole(User.Role role) {
        return userRepository.findByRole(role);
    }

    public User create(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username " + user.getUsername() + " already exists.");
        }
        return userRepository.save(user);
    }

    public User update(Long id, User updated){
        User existing = getById(id);
        existing.setUsername(updated.getUsername());
        existing.setRole(updated.getRole());
        existing.setLinkedId(updated.getLinkedId());
        return userRepository.save(existing);
    }

    public User changePassword(Long id, String newPasswordHash){
        User existing = getById(id);
        existing.setPasswordHash(newPasswordHash);
        existing.setMustChangePassword(false); // ✅ clear the force-change flag
        return userRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        userRepository.deleteById(id);
    }

    // ✅ New — auto-create or update a TEACHER user account when a teacher
    // is assigned as a class teacher. Username and default password = teacher's phone.
    public User createOrUpdateTeacherUser(jnm.engineer.demo.models.Teacher teacher, SchoolClass schoolClass) {
        String username = teacher.getPhone().trim();

        return userRepository.findByUsername(username)
                .map(existing -> {
                    // Existing account — just (re)link the class, keep their password as-is
                    existing.setLinkedClass(schoolClass);
                    existing.setRole(User.Role.TEACHER);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    // New account — username & default password = phone number
                    User newUser = new User();
                    newUser.setUsername(username);
                    newUser.setPasswordHash(passwordEncoder.encode(username)); // default password = phone
                    newUser.setRole(User.Role.TEACHER);
                    newUser.setLinkedClass(schoolClass);
                    newUser.setMustChangePassword(true); // ✅ force change on first login
                    return userRepository.save(newUser);
                });
    }
}