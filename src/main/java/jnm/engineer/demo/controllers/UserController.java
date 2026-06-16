package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.User;
import jnm.engineer.demo.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<User>> getAll(){
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id){
        return ResponseEntity.ok(userService.getById(id));
    }

    // ✅ Fixed — changed @PathVariable to @RequestParam
    @GetMapping("/by-role")
    public ResponseEntity<List<User>> getByRole(@RequestParam User.Role role){
        return ResponseEntity.ok(userService.getByRole(role));
    }

    @PostMapping()
    public ResponseEntity<User> create(@Valid @RequestBody User user){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.create(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id,
                                       @Valid @RequestBody User user){
        return ResponseEntity.ok(userService.update(id, user));
    }

    @PatchMapping("/{id}/change-password")
    public ResponseEntity<String> changePassword(@PathVariable Long id,
                                                 @RequestBody Map<String,String> body){
        String newPasswordHash = body.get("passwordHash");
        userService.changePassword(id, newPasswordHash);
        return ResponseEntity.ok("Password updated successfully.");
    }

    @PatchMapping("/{userId}/assign-class/{classId}")
    public ResponseEntity<String> assignClass(@PathVariable Long userId,
                                              @PathVariable Long classId){
        userService.assignClassToUser(userId, classId);
        return ResponseEntity.ok("Class assigned successfully");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        userService.delete(id);
        return ResponseEntity.ok("User deleted successfully");
    }
}