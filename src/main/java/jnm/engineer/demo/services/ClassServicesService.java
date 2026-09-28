package jnm.engineer.demo.services;

import jnm.engineer.demo.models.OptionalCharge;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.repositories.OptionalChargeRepository;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.security.AccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * "Meals & Transport" — class teachers tick who takes lunch, porridge, swimming, a trip…
 * and who uses the bus, for their OWN class in the CURRENT term.
 *
 * Rules (checked here, on the server):
 *   • own class only (admin and bursar: any class)          • current term only
 *   • only charges the bursar marked "Teachers can tick"     • no amounts are shown or set
 *   • a learner with a special price (months / days / agreed) is locked — the bursar changes it
 *   • transport: destination and one/two ways only; the bus is left for the bursar
 * The actual charging is done by ExtraChargeService and TransportService, so every rule and
 * the history (who ticked, cancellations) stay exactly the same as on the Finance pages.
 * A save is all-or-nothing.
 */
@Service
@RequiredArgsConstructor
public class ClassServicesService {
    private static final String LOCK_PRICE = "Special price set by the bursar — ask the bursar to change it";

    private final AccessGuard accessGuard;
    private final CurrentTermService currentTermService;
    private final ExtraChargeService extraChargeService;
    private final TransportService transportService;
    private final OptionalChargeRepository chargeRepository;
    private final SchoolClassRepository schoolClassRepository;

    public record ChargeTicks(Long chargeId, List<Long> add, List<Long> remove) {}
    public record TripIn(Long studentId, Long routeId, String direction) {}
    public record SaveIn(Long classId, List<ChargeTicks> charges, List<TripIn> transport) {}

    // ══ Which classes may I open? ═══════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> myClasses() {
        AccessGuard.Scope scope = accessGuard.currentScope();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SchoolClass c : schoolClassRepository.findAll()) {
            if (!(scope.isAll() || scope.ownClassIds().contains(c.getClassId()))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("classId", c.getClassId());
            m.put("className", c.getClassName());
            m.put("stream", c.getStream());
            m.put("gradeLevel", c.getGradeLevel());
            m.put("section", c.getSection());
            out.add(m);
        }
        CurrentTermService.Term t = currentTermService.current();
        return Map.of("classes", out, "yearLabel", t.yearLabel(), "term", t.term(), "termLabel", t.label());
    }

    // ══ The class sheet ═════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> sheet(Long classId) {
        SchoolClass cls = ownClass(classId);
        CurrentTermService.Term t = currentTermService.current();
        return build(cls, t).toMap(t);
    }

    // ══ Save ════════════════════════════════════════════════════════════════
    @Transactional
    public Map<String, Object> save(SaveIn in, String who) {
        SchoolClass cls = ownClass(in.classId());
        CurrentTermService.Term t = currentTermService.current();
        Sheet now = build(cls, t);
        List<String> problems = new ArrayList<>();
        int ticked = 0, unticked = 0, trips = 0;

        // ── charges ──
        for (ChargeTicks ct : in.charges() == null ? List.<ChargeTicks>of() : in.charges()) {
            Map<Long, Map<String, Object>> rows = now.chargeRows.get(ct.chargeId());
            if (rows == null) { problems.add("One of the charges can't be ticked here (it may have been switched off) — refresh the page"); continue; }
            String name = now.chargeNames.get(ct.chargeId());
            List<Long> add = new ArrayList<>(), remove = new ArrayList<>();
            for (Long sid : ct.add() == null ? List.<Long>of() : ct.add()) {
                Map<String, Object> r = rows.get(sid);
                if (r == null) { problems.add("A learner is not in this class — refresh the page"); continue; }
                if (Boolean.TRUE.equals(r.get("ticked"))) continue;                  // already charged
                String lock = lockOf(r);
                if (lock != null) { problems.add(r.get("name") + " — " + name + ": " + lock); continue; }
                add.add(sid);
            }
            for (Long sid : ct.remove() == null ? List.<Long>of() : ct.remove()) {
                Map<String, Object> r = rows.get(sid);
                if (r == null) { problems.add("A learner is not in this class — refresh the page"); continue; }
                if (!Boolean.TRUE.equals(r.get("ticked"))) continue;                 // nothing to remove
                String lock = lockOf(r);
                if (lock != null) { problems.add(r.get("name") + " — " + name + ": " + lock); continue; }
                remove.add(sid);
            }
            if (problems.isEmpty() && (!add.isEmpty() || !remove.isEmpty())) {
                extraChargeService.apply(ct.chargeId(), new ExtraChargeService.ApplyIn(t.yearLabel(), t.term(), add, remove), who);
                ticked += add.size();
                unticked += remove.size();
            }
        }

        // ── transport ──
        List<TransportService.Change> changes = new ArrayList<>();
        for (TripIn tr : in.transport() == null ? List.<TripIn>of() : in.transport()) {
            Map<String, Object> r = now.transportRows.get(tr.studentId());
            if (r == null) { problems.add("A learner is not in this class — refresh the page"); continue; }
            Long curRoute = (Long) r.get("routeId");
            String curDir = (String) r.get("direction");
            String dir = tr.routeId() == null ? null : (tr.direction() == null ? "TWO_WAY" : tr.direction().trim().toUpperCase());
            if (Objects.equals(curRoute, tr.routeId()) && (tr.routeId() == null || Objects.equals(curDir, dir))) continue;   // no change
            if (tr.routeId() != null && !now.activeRouteIds.contains(tr.routeId())) {
                problems.add(r.get("name") + ": that destination is switched off or no longer exists — refresh the page");
                continue;
            }
            if (curRoute != null && !"TERM".equals(r.get("basis"))) { problems.add(r.get("name") + " — transport: " + LOCK_PRICE); continue; }
            // New trip starts at the full-term fare; the bus is chosen by the bursar
            changes.add(new TransportService.Change(tr.studentId(), tr.routeId(), dir, null, null, null, null, null));
        }

        if (!problems.isEmpty()) {
            // Throwing rolls back anything already applied above — a save is all-or-nothing
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing was saved: " + String.join("; ", new LinkedHashSet<>(problems)));
        }
        if (!changes.isEmpty()) {
            transportService.apply(new TransportService.ApplyIn(t.yearLabel(), t.term(), changes), who);
            trips = changes.size();
        }
        if (ticked + unticked + trips == 0) return Map.of("message", "No changes to save.");
        return Map.of("message", "Saved for " + t.label() + ": " + ticked + " ticked, " + unticked + " unticked"
                + (trips > 0 ? ", " + trips + " transport change(s)" : "") + ". The bursar will see these on the learners' accounts.");
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    private SchoolClass ownClass(Long classId) {
        if (classId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the class.");
        if (!accessGuard.currentScope().isOwnClass(classId))
            throw AccessGuard.forbidden("You can only tick learners in your own class.");
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found."));
    }

    /** Why a teacher can't change this charge row (null = free to change). */
    private static String lockOf(Map<String, Object> r) {
        if (Boolean.TRUE.equals(r.get("locked"))) return "charged in " + r.get("chargedIn") + " (once only)";
        if (Boolean.TRUE.equals(r.get("ticked")) && r.get("basis") != null && !"TERM".equals(r.get("basis"))) return LOCK_PRICE;
        return null;
    }

    /** Everything the page needs, read through the same services the Finance pages use. */
    private Sheet build(SchoolClass cls, CurrentTermService.Term t) {
        Sheet s = new Sheet();
        s.cls = cls;
        List<OptionalCharge> charges = chargeRepository.findAllByOrderByActiveDescNameAsc().stream()
                .filter(c -> c.isActive() && c.teachersMayTick() && c.appliesTo(cls)).toList();
        for (OptionalCharge c : charges) {
            Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) extraChargeService
                    .learners(c.getChargeId(), t.yearLabel(), t.term(), cls.getClassId()).get("learners");
            for (Map<String, Object> r : list) rows.put((Long) r.get("studentId"), r);
            s.chargeRows.put(c.getChargeId(), rows);
            s.chargeNames.put(c.getChargeId(), c.getName());
            s.charges.add(Map.of("chargeId", c.getChargeId(), "name", c.getName(), "kind", c.getKind().name()));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> riders = (List<Map<String, Object>>) transportService
                .term(t.yearLabel(), t.term(), cls.getClassId()).get("learners");
        for (Map<String, Object> r : riders) s.transportRows.put((Long) r.get("studentId"), r);
        for (Map<String, Object> route : transportService.routes()) {
            if (!Boolean.TRUE.equals(route.get("active"))) continue;
            s.activeRouteIds.add((Long) route.get("routeId"));
            s.routes.add(Map.of("routeId", route.get("routeId"), "name", route.get("name")));
        }
        return s;
    }

    private static final class Sheet {
        SchoolClass cls;
        final List<Map<String, Object>> charges = new ArrayList<>();
        final Map<Long, String> chargeNames = new HashMap<>();
        final Map<Long, Map<Long, Map<String, Object>>> chargeRows = new LinkedHashMap<>();
        final Map<Long, Map<String, Object>> transportRows = new LinkedHashMap<>();
        final Set<Long> activeRouteIds = new HashSet<>();
        final List<Map<String, Object>> routes = new ArrayList<>();

        /** What the teacher's page gets — ticks and locks, never amounts. */
        Map<String, Object> toMap(CurrentTermService.Term t) {
            List<Map<String, Object>> learners = new ArrayList<>();
            for (Map<String, Object> tr : transportRows.values()) {
                Long sid = (Long) tr.get("studentId");
                Map<String, Object> ticks = new LinkedHashMap<>();
                for (Map.Entry<Long, Map<Long, Map<String, Object>>> e : chargeRows.entrySet()) {
                    Map<String, Object> r = e.getValue().get(sid);
                    if (r == null) continue;                        // charge doesn't apply to this learner
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("ticked", Boolean.TRUE.equals(r.get("ticked")));
                    String lock = lockOf(r);
                    if (lock != null) cell.put("lock", lock);
                    ticks.put(String.valueOf(e.getKey()), cell);
                }
                Map<String, Object> trip = new LinkedHashMap<>();
                trip.put("routeId", tr.get("routeId"));
                trip.put("direction", tr.get("direction"));
                if (tr.get("routeId") != null && !"TERM".equals(tr.get("basis"))) trip.put("lock", LOCK_PRICE);
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("studentId", sid);
                l.put("admissionNumber", tr.get("admissionNumber"));
                l.put("name", tr.get("name"));
                l.put("charges", ticks);
                l.put("transport", trip);
                learners.add(l);
            }
            learners.sort(Comparator.comparing(l -> String.valueOf(l.get("name"))));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("classId", cls.getClassId());
            out.put("className", cls.getClassName());
            out.put("yearLabel", t.yearLabel());
            out.put("term", t.term());
            out.put("termLabel", t.label());
            out.put("charges", charges);
            out.put("routes", routes);
            out.put("learners", learners);
            return out;
        }
    }
}
