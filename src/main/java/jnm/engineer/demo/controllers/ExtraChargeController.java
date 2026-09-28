package jnm.engineer.demo.controllers;

import jnm.engineer.demo.services.ExtraChargeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Extra charges — web layer only (rules in ExtraChargeService).
 * Under /api/finance, so only ADMIN and ACCOUNTANT may use it (SecurityConfig).
 *
 *   GET  /api/finance/charges                                     the list of extra charges
 *   PUT  /api/finance/charges                                     create or edit one
 *   GET  /api/finance/charges/{id}/learners?yearLabel=&term=&classId=   tick list
 *   POST /api/finance/charges/{id}/apply                          save ticks / unticks
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance/charges")
public class ExtraChargeController {

    private final ExtraChargeService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(service.listCharges());
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody ExtraChargeService.ChargeIn in) {
        return ResponseEntity.ok(service.saveCharge(in));
    }

    @GetMapping("/{chargeId}/learners")
    public ResponseEntity<Map<String, Object>> learners(@PathVariable Long chargeId, @RequestParam String yearLabel,
                                                        @RequestParam Integer term, @RequestParam(required = false) Long classId) {
        return ResponseEntity.ok(service.learners(chargeId, yearLabel, term, classId));
    }

    @PostMapping("/{chargeId}/apply")
    public ResponseEntity<Map<String, Object>> apply(@PathVariable Long chargeId, @RequestBody ExtraChargeService.ApplyIn in,
                                                     @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.apply(chargeId, in, me != null ? me.getUsername() : null));
    }

    /** One learner's price: full term, months, days or an agreed amount. */
    @PutMapping("/{chargeId}/price")
    public ResponseEntity<Map<String, Object>> price(@PathVariable Long chargeId, @RequestBody ExtraChargeService.PriceIn in,
                                                     @AuthenticationPrincipal UserDetails me) {
        return ResponseEntity.ok(service.setPrice(chargeId, in, me != null ? me.getUsername() : null));
    }
}
