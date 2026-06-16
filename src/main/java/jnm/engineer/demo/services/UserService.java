package jnm.engineer.demo.services;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;

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
        return userRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        userRepository.deleteById(id);
    }
}
