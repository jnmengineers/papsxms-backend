package jnm.engineer.demo.services;

import jnm.engineer.demo.models.Announcement;
import jnm.engineer.demo.models.AnnouncementRead;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.AnnouncementReadRepository;
import jnm.engineer.demo.repositories.AnnouncementRepository;
import jnm.engineer.demo.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Notices from the office to staff. The admin posts them to all staff or chosen roles,
 * with optional start/end dates, "pinned" and "urgent". Everyone sees the notices meant
 * for them; the admin sees who has read each one.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {
    private static final Set<String> AUDIENCES = Set.of("ALL", "ADMIN", "TEACHER", "CLERK", "ACCOUNTANT");

    private final AnnouncementRepository repository;
    private final AnnouncementReadRepository readRepository;
    private final UserRepository userRepository;

    public record AnnouncementIn(String title, String body, List<String> audience, Boolean pinned, Boolean urgent,
                                 LocalDate startsOn, LocalDate endsOn) {}

    // ══ For everyone ════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(String username) {
        String role = roleOf(username);
        Set<Long> read = new HashSet<>();
        readRepository.findByUsername(username).forEach(r -> read.add(r.getAnnouncement().getAnnouncementId()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Announcement a : repository.findByArchivedFalseOrderByPinnedDescStartsOnDescAnnouncementIdDesc()) {
            if (!showingNow(a) || !a.isFor(role)) continue;
            Map<String, Object> m = map(a);
            m.put("read", read.contains(a.getAnnouncementId()));
            out.add(m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> unreadCount(String username) {
        long n = mine(username).stream().filter(m -> !Boolean.TRUE.equals(m.get("read"))).count();
        return Map.of("unread", n);
    }

    @Transactional
    public Map<String, Object> markRead(Long id, String username) {
        Announcement a = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found."));
        if (!a.isFor(roleOf(username))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This notice isn't for you.");
        if (!readRepository.existsByAnnouncementAnnouncementIdAndUsername(id, username)) {
            AnnouncementRead r = new AnnouncementRead();
            r.setAnnouncement(a);
            r.setUsername(username);
            r.setReadAt(LocalDateTime.now());
            readRepository.save(r);
        }
        return Map.of("message", "Marked as read.");
    }

    // ══ Admin ═══════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> all() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Announcement a : repository.findAllByOrderByArchivedAscPinnedDescStartsOnDescAnnouncementIdDesc()) {
            Map<String, Object> m = map(a);
            m.put("readCount", readRepository.countByAnnouncementAnnouncementId(a.getAnnouncementId()));
            m.put("audienceCount", users.stream().filter(u -> a.isFor(u.getRole() != null ? u.getRole().name() : null)).count());
            m.put("showingNow", !a.isArchived() && showingNow(a));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> save(Long id, AnnouncementIn in, String who) {
        Announcement a = id == null ? new Announcement()
                : repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found."));
        String title = in.title() == null ? "" : in.title().trim();
        String body = in.body() == null ? "" : in.body().trim();
        if (title.length() < 3 || title.length() > 120) throw bad("The title must be 3 to 120 characters.");
        if (body.isEmpty() || body.length() > 4000) throw bad("Write the notice (4,000 characters at most).");
        List<String> aud = in.audience() == null ? List.of() : in.audience().stream().map(s -> String.valueOf(s).trim().toUpperCase()).distinct().toList();
        if (aud.isEmpty()) throw bad("Choose who should see it.");
        if (!AUDIENCES.containsAll(aud)) throw bad("Unknown audience.");
        LocalDate start = in.startsOn() != null ? in.startsOn() : LocalDate.now();
        if (in.endsOn() != null && in.endsOn().isBefore(start)) throw bad("The end date is before the start date.");
        a.setTitle(title);
        a.setBody(body);
        a.setAudience(aud.contains("ALL") ? "ALL" : String.join(",", aud));
        a.setPinned(Boolean.TRUE.equals(in.pinned()));
        a.setUrgent(Boolean.TRUE.equals(in.urgent()));
        a.setStartsOn(start);
        a.setEndsOn(in.endsOn());
        if (id == null) { a.setCreatedBy(who); a.setCreatedAt(LocalDateTime.now()); }
        else a.setUpdatedAt(LocalDateTime.now());
        repository.save(a);
        return Map.of("message", id == null ? "Notice posted." : "Notice updated.", "announcementId", a.getAnnouncementId());
    }

    @Transactional
    public Map<String, Object> setArchived(Long id, boolean archived) {
        Announcement a = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found."));
        a.setArchived(archived);
        a.setUpdatedAt(LocalDateTime.now());
        repository.save(a);
        return Map.of("message", archived ? "Notice taken down." : "Notice restored.");
    }

    /** Everyone the notice is for, with when they read it (or not yet). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> readers(Long id) {
        Announcement a = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found."));
        Map<String, LocalDateTime> readAt = new HashMap<>();
        readRepository.findByAnnouncementAnnouncementId(id).forEach(r -> readAt.put(r.getUsername(), r.getReadAt()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            String role = u.getRole() != null ? u.getRole().name() : null;
            if (!a.isFor(role)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.getUsername());
            m.put("name", u.getTeacher() != null ? (u.getTeacher().getFirstName() + " " + u.getTeacher().getLastName()).trim() : u.getUsername());
            m.put("role", role);
            m.put("readAt", readAt.containsKey(u.getUsername()) ? readAt.get(u.getUsername()).toString() : null);
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> m.get("readAt") == null ? 0 : 1).thenComparing(m -> String.valueOf(m.get("name"))));
        return out;
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    private String roleOf(String username) {
        return userRepository.findByUsername(username).map(u -> u.getRole() != null ? u.getRole().name() : null).orElse(null);
    }

    private static boolean showingNow(Announcement a) {
        LocalDate today = LocalDate.now();
        return !a.getStartsOn().isAfter(today) && (a.getEndsOn() == null || !a.getEndsOn().isBefore(today));
    }

    private static Map<String, Object> map(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("announcementId", a.getAnnouncementId());
        m.put("title", a.getTitle());
        m.put("body", a.getBody());
        m.put("audience", a.audienceList());
        m.put("pinned", a.isPinned());
        m.put("urgent", a.isUrgent());
        m.put("startsOn", a.getStartsOn().toString());
        m.put("endsOn", a.getEndsOn() != null ? a.getEndsOn().toString() : null);
        m.put("archived", a.isArchived());
        m.put("createdBy", a.getCreatedBy());
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        return m;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
