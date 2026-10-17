package jnm.engineer.demo.controllers;

import jnm.engineer.demo.models.SitePost;
import jnm.engineer.demo.repositories.SitePostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Website news, events and gallery. Public: /api/public/posts (+ image). Staff (ADMIN, CLERK):
 * /api/site-posts to add, edit, hide and delete. Photos come resized from the browser (base64).
 */
@RestController
@RequiredArgsConstructor
public class SitePostController {
    private static final Set<String> KINDS = Set.of("NEWS", "EVENT", "GALLERY", "SLIDE", "PERSON", "PROGRAM", "DOC", "JOB");
    private static final int MAX_IMAGE = 1_500_000;
    private final SitePostRepository repo;

    public record PostIn(String kind, String title, String tag, String body, LocalDate eventDate, Boolean published,
                         String imageBase64, String imageType, Boolean removeImage) {}

    // ── Public ──
    @GetMapping("/api/public/posts")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicPosts(@RequestParam(defaultValue = "NEWS,EVENT") String kind, @RequestParam(defaultValue = "6") int limit) {
        Set<String> kinds = new HashSet<>(Arrays.asList(kind.toUpperCase().split(",")));
        return repo.findByPublishedTrueOrderByCreatedAtDesc().stream().filter(p -> kinds.contains(p.getKind()))
                .limit(Math.min(Math.max(limit, 1), 30)).map(this::view).toList();
    }

    @GetMapping("/api/public/posts/{id}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        SitePost p = repo.findById(id).filter(x -> Boolean.TRUE.equals(x.getPublished()) && x.isHasImage())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getImageType() == null ? "image/jpeg" : p.getImageType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)).body(p.getImageData());
    }

    // ── Staff ──
    @GetMapping("/api/site-posts")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> all() { return repo.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList(); }

    @GetMapping("/api/site-posts/{id}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> staffImage(@PathVariable Long id) {
        SitePost p = repo.findById(id).filter(SitePost::isHasImage).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(p.getImageType() == null ? "image/jpeg" : p.getImageType())).body(p.getImageData());
    }

    @PostMapping("/api/site-posts")
    public Map<String, Object> create(@RequestBody PostIn in, @AuthenticationPrincipal UserDetails me) {
        SitePost p = new SitePost();
        p.setCreatedAt(LocalDateTime.now()); p.setCreatedBy(me != null ? me.getUsername() : null);
        return view(repo.save(apply(p, in)));
    }

    @PutMapping("/api/site-posts/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody PostIn in) {
        SitePost p = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return view(repo.save(apply(p, in)));
    }

    @DeleteMapping("/api/site-posts/{id}")
    public Map<String, String> delete(@PathVariable Long id) { repo.deleteById(id); return Map.of("message", "Deleted."); }

    private SitePost apply(SitePost p, PostIn in) {
        String kind = in.kind() == null ? "" : in.kind().toUpperCase();
        if (!KINDS.contains(kind)) throw bad("Choose a type.");
        boolean doc = "DOC".equals(kind);
        if (!"GALLERY".equals(kind) && !doc && (in.title() == null || in.title().isBlank())) throw bad("Enter a title.");
        if (doc && !Set.of("PRIMARY", "JSS").contains(String.valueOf(in.tag()))) throw bad("Choose Primary or Junior School.");
        p.setKind(kind); p.setTitle(cut(in.title(), 150)); p.setTag(cut(in.tag(), 30)); p.setBody(cut(in.body(), 2000));
        p.setEventDate(in.eventDate()); p.setPublished(in.published() == null || in.published());
        if (Boolean.TRUE.equals(in.removeImage())) { p.setImageData(null); p.setImageType(null); }
        if (in.imageBase64() != null && !in.imageBase64().isBlank()) {
            String b64 = in.imageBase64().contains(",") ? in.imageBase64().substring(in.imageBase64().indexOf(',') + 1) : in.imageBase64();
            byte[] data;
            try { data = Base64.getDecoder().decode(b64); } catch (IllegalArgumentException e) { throw bad("The photo could not be read."); }
            String type = in.imageType() == null ? "image/jpeg" : in.imageType();
            if (doc) { if (!"application/pdf".equals(type)) throw bad("Upload a PDF file."); if (data.length > 5_000_000) throw bad("The PDF is too large (max 5 MB)."); }
            else { if (data.length > MAX_IMAGE) throw bad("The photo is too large (max 1.5 MB)."); if (!Set.of("image/jpeg", "image/png", "image/webp").contains(type)) throw bad("Use a JPG, PNG or WebP photo."); }
            p.setImageData(data); p.setImageType(type);
        }
        if (("GALLERY".equals(kind) || "SLIDE".equals(kind)) && !p.isHasImage()) throw bad("This needs a photo.");
        if (doc && !p.isHasImage()) throw bad("Upload the PDF.");
        if (doc && (p.getTitle() == null)) p.setTitle("JSS".equals(in.tag()) ? "Junior School fee structure" : "Primary fee structure");
        return p;
    }

    private Map<String, Object> view(SitePost p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("postId", p.getPostId()); m.put("kind", p.getKind()); m.put("title", p.getTitle()); m.put("tag", p.getTag());
        m.put("body", p.getBody()); m.put("eventDate", p.getEventDate()); m.put("published", Boolean.TRUE.equals(p.getPublished()));
        m.put("createdAt", p.getCreatedAt()); m.put("hasImage", p.isHasImage());
        m.put("image", p.isHasImage() ? "/api/public/posts/" + p.getPostId() + "/image" : null);
        return m;
    }
    private static String cut(String v, int max) { if (v == null || v.isBlank()) return null; String t = v.trim(); return t.length() > max ? t.substring(0, max) : t; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
