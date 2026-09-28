package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.ClassServicesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Meals & Transport: class teachers tick lunch, porridge, trips… and bus users for their own
 * class in the current term. Admin and bursar may use it for any class. All rules are in
 * ClassServicesService; SecurityConfig lets ADMIN, ACCOUNTANT and TEACHER reach it.
 */
@RestController
@RequestMapping("/api/class-services")
@RequiredArgsConstructor
public class ClassServicesController {
    private final ClassServicesService service;

    @GetMapping("/classes")
    public ResponseEntity<Map<String, Object>> classes() {
        return ResponseEntity.ok(service.myClasses());
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> sheet(@RequestParam Long classId) {
        return ResponseEntity.ok(service.sheet(classId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody ClassServicesService.SaveIn in,
                                                    @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.save(in, me != null ? me.getUsername() : null));
    }
}
