package jnm.engineer.demo.controllers;

import jakarta.validation.Valid;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.services.UserService;
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

    // Get /api/users/by-role?role=Teacher
    @GetMapping("/by-role")
    public ResponseEntity<List<User>> getByRole(@PathVariable User.Role role){
        return ResponseEntity.ok(userService.getByRole(role));
    }
    // POST /api/users
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
        String newPasswordHarsh =  body.get("passwordHarsh");
        userService.changePassword(id, newPasswordHarsh);
        return ResponseEntity.ok("password update successfully.");
    }

    //DELETE /api/users/1
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id){
        userService.delete(id);
        return ResponseEntity.ok("user deleted successfully");
    }
}
