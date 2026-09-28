package jnm.engineer.demo.controllers;

import jnm.engineer.demo.security.AccessGuard;
import jnm.engineer.demo.services.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Daily attendance register — web layer only. Rules are in AttendanceService.
 * This class checks WHO may read / write each class (AccessGuard):
 *   read  — admin/clerk/bursar: all; teachers: own class + classes they teach
 *   write — admin: all; teachers: their OWN class only
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AccessGuard accessGuard;

    private void requireRead(Long classId) {
        if (!accessGuard.currentScope().canRead(classId)) {
            throw AccessGuard.forbidden("You can only view attendance for your own classes.");
        }
    }

    @GetMapping("/class/{classId}/date/{date}")
    public ResponseEntity<List<Map<String, Object>>> getDay(@PathVariable Long classId,
                                                            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireRead(classId);
        return ResponseEntity.ok(attendanceService.getDay(classId, date));
    }

    @PutMapping("/class/{classId}/date/{date}")
    public ResponseEntity<Map<String, Object>> saveDay(@PathVariable Long classId,
                                                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                       @RequestBody List<AttendanceService.Entry> entries,
                                                       @AuthenticationPrincipal UserDetails me) {
        if (!accessGuard.currentScope().canWrite(classId, null)) {       // own class only (admin passes)
            throw AccessGuard.forbidden("Only the class teacher can take the register for this class.");
        }
        return ResponseEntity.ok(attendanceService.saveDay(classId, date, entries, me != null ? me.getUsername() : null));
    }

    @GetMapping("/class/{classId}/summary")
    public ResponseEntity<Map<String, Object>> summary(@PathVariable Long classId,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireRead(classId);
        return ResponseEntity.ok(attendanceService.summary(classId, from, to));
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> byStudent(@PathVariable Long studentId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireRead(accessGuard.classOfStudent(studentId));
        return ResponseEntity.ok(attendanceService.byStudent(studentId, from, to));
    }
}
