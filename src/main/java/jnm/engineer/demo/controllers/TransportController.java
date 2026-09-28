package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.TransportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Transport / fleet — web layer only (rules in TransportService).
 * ADMIN and ACCOUNTANT only (SecurityConfig: /api/transport/**).
 *
 *   GET/PUT /api/transport/routes                       destinations and fares
 *   GET/PUT /api/transport/vehicles                     the fleet
 *   GET     /api/transport/term?yearLabel=&term=&classId=   who rides this term
 *   POST    /api/transport/apply                        save the term's changes
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transport")
public class TransportController {

    private final TransportService service;

    @GetMapping("/routes")
    public ResponseEntity<List<Map<String, Object>>> routes() { return ResponseEntity.ok(service.routes()); }

    @PutMapping("/routes")
    public ResponseEntity<Map<String, Object>> saveRoute(@RequestBody TransportService.RouteIn in) { return ResponseEntity.ok(service.saveRoute(in)); }

    @GetMapping("/vehicles")
    public ResponseEntity<List<Map<String, Object>>> vehicles() { return ResponseEntity.ok(service.vehicles()); }

    @PutMapping("/vehicles")
    public ResponseEntity<Map<String, Object>> saveVehicle(@RequestBody TransportService.VehicleIn in) { return ResponseEntity.ok(service.saveVehicle(in)); }

    @GetMapping("/term")
    public ResponseEntity<Map<String, Object>> term(@RequestParam String yearLabel, @RequestParam Integer term,
                                                    @RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(service.term(yearLabel, term, classId));
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(@RequestBody TransportService.ApplyIn in, @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.apply(in, me != null ? me.getUsername() : null));
    }
}
