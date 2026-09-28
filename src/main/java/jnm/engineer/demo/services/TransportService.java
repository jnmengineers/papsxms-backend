package jnm.engineer.demo.services;

import jnm.engineer.demo.models.*;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.TransportRoute;
import jnm.engineer.demo.models.TransportSubscription;
import jnm.engineer.demo.models.Vehicle;
import jnm.engineer.demo.repositories.TransportRouteRepository;
import jnm.engineer.demo.repositories.TransportSubscriptionRepository;
import jnm.engineer.demo.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * School transport: routes (fares), the fleet (buses), and which learners ride each term.
 * A learner's transport for a term charges the route's TERMLY fare to their fee account
 * (FinanceService counts these in balances and statements).
 */
@Service
@RequiredArgsConstructor
public class TransportService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");

    private final TransportRouteRepository routeRepository;
    private final VehicleRepository vehicleRepository;
    private final TransportSubscriptionRepository subscriptionRepository;
    private final StudentService studentService;

    public record RouteIn(Long routeId, String name, BigDecimal oneWayMonthly, BigDecimal oneWayTermly,
                          BigDecimal twoWayMonthly, BigDecimal twoWayTermly, Boolean active,
                          BigDecimal oneWayDaily, BigDecimal twoWayDaily) {}
    public record VehicleIn(Long vehicleId, String registration, String name, Integer capacity,
                            String driverName, String driverPhone, Boolean active) {}
    /** routeId null = not using transport this term. */
    /** basis empty = keep the learner's current price (or full term for a new rider). See ChargePricing. */
    public record Change(Long studentId, Long routeId, String direction, Long vehicleId,
                         String basis, Integer quantity, BigDecimal amount, String note) {}
    public record ApplyIn(String yearLabel, Integer term, List<Change> changes) {}

    // ══ Routes ══════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> routes() {
        List<Map<String, Object>> out = new ArrayList<>();
        routeRepository.findAllByOrderByActiveDescNameAsc().forEach(r -> out.add(routeMap(r)));
        return out;
    }

    @Transactional
    public Map<String, Object> saveRoute(RouteIn in) {
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty() || name.length() > 60) throw bad("Give the destination a name (up to 60 letters).");
        boolean dup = in.routeId() == null ? routeRepository.existsByNameIgnoreCase(name)
                : routeRepository.existsByNameIgnoreCaseAndRouteIdNot(name, in.routeId());
        if (dup) throw new ResponseStatusException(HttpStatus.CONFLICT, "\"" + name + "\" already exists.");
        TransportRoute r = in.routeId() == null ? new TransportRoute()
                : routeRepository.findById(in.routeId()).orElseThrow(() -> notFound("Destination not found."));
        r.setName(name);
        r.setOneWayMonthly(money(in.oneWayMonthly(), "One way monthly"));
        r.setOneWayTermly(money(in.oneWayTermly(), "One way termly"));
        r.setTwoWayMonthly(money(in.twoWayMonthly(), "Two ways monthly"));
        r.setTwoWayTermly(money(in.twoWayTermly(), "Two ways termly"));
        r.setOneWayDaily(ChargePricing.optionalRate(in.oneWayDaily(), "One way daily"));
        r.setTwoWayDaily(ChargePricing.optionalRate(in.twoWayDaily(), "Two ways daily"));
        r.setActive(in.active() == null || in.active());
        routeRepository.save(r);
        Map<String, Object> out = new LinkedHashMap<>(routeMap(r));
        out.put("message", name + " saved. Learners already charged keep the fare they were charged.");
        return out;
    }

    // ══ Fleet ═══════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<Map<String, Object>> vehicles() {
        List<Map<String, Object>> out = new ArrayList<>();
        vehicleRepository.findAllByOrderByActiveDescNameAscRegistrationAsc().forEach(v -> out.add(vehicleMap(v)));
        return out;
    }

    @Transactional
    public Map<String, Object> saveVehicle(VehicleIn in) {
        String reg = in.registration() == null ? "" : in.registration().trim().toUpperCase().replaceAll("\\s+", " ");
        if (reg.length() < 4 || reg.length() > 15) throw bad("Enter the registration number, e.g. KDA 123X.");
        boolean dup = in.vehicleId() == null ? vehicleRepository.existsByRegistrationIgnoreCase(reg)
                : vehicleRepository.existsByRegistrationIgnoreCaseAndVehicleIdNot(reg, in.vehicleId());
        if (dup) throw new ResponseStatusException(HttpStatus.CONFLICT, reg + " is already in the fleet.");
        if (in.capacity() != null && (in.capacity() < 1 || in.capacity() > 100)) throw bad("Capacity must be between 1 and 100.");
        Vehicle v = in.vehicleId() == null ? new Vehicle()
                : vehicleRepository.findById(in.vehicleId()).orElseThrow(() -> notFound("Vehicle not found."));
        v.setRegistration(reg);
        v.setName(blankToNull(in.name(), 40));
        v.setCapacity(in.capacity());
        v.setDriverName(blankToNull(in.driverName(), 80));
        v.setDriverPhone(blankToNull(in.driverPhone(), 20));
        v.setActive(in.active() == null || in.active());
        vehicleRepository.save(v);
        Map<String, Object> out = new LinkedHashMap<>(vehicleMap(v));
        out.put("message", reg + " saved.");
        return out;
    }

    // ══ Learners using transport in a term ══════════════════════════════════
    /** Every learner with a class, and their transport for the term (if any). */
    @Transactional(readOnly = true)
    public Map<String, Object> term(String yearLabel, Integer term, Long classId) {
        String year = requireYear(yearLabel);
        requireTerm(term);
        Map<Long, TransportSubscription> subs = new HashMap<>();
        for (TransportSubscription s : subscriptionRepository.findByYearLabelAndTermAndCancelledFalse(year, term)) {
            subs.put(s.getStudent().getStudentId(), s);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Student st : studentService.getAllStudents()) {
            SchoolClass cls = st.getSchoolClass();
            if (cls == null) continue;
            if (classId != null && !classId.equals(cls.getClassId())) continue;
            TransportSubscription s = subs.get(st.getStudentId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("studentId", st.getStudentId());
            r.put("admissionNumber", st.getAdmissionNumber());
            r.put("name", st.getFirstName() + " " + st.getLastName());
            r.put("classId", cls.getClassId());
            r.put("className", cls.getClassName());
            r.put("routeId", s != null ? s.getRoute().getRouteId() : null);
            r.put("routeName", s != null ? s.getRoute().getName() : null);
            r.put("direction", s != null ? s.getDirection().name() : null);
            r.put("vehicleId", s != null && s.getVehicle() != null ? s.getVehicle().getVehicleId() : null);
            r.put("amount", s != null ? s.getAmount() : null);
            r.put("basis", s != null ? ChargePricing.parseStored(s.getBasis()).name() : null);
            r.put("quantity", s != null ? s.getQuantity() : null);
            r.put("priceNote", s != null ? s.getPriceNote() : null);
            r.put("priceLabel", s != null ? ChargePricing.label(s.getBasis(), s.getQuantity()).trim() : null);
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("className")))
                .thenComparing(r -> String.valueOf(r.get("name"))));
        return Map.of("yearLabel", year, "term", term, "learners", rows);
    }

    /**
     * Saves the term's transport changes — all or nothing.
     *  • new rider → charged the route's termly fare
     *  • different destination or one/two ways → old fare cancelled, new fare charged
     *  • only the bus changed → no money change
     *  • removed (routeId empty) → fare cancelled
     */
    @Transactional
    public Map<String, Object> apply(ApplyIn in, String who) {
        String year = requireYear(in.yearLabel());
        Integer term = in.term();
        requireTerm(term);
        Map<Long, TransportSubscription> current = new HashMap<>();
        for (TransportSubscription s : subscriptionRepository.findByYearLabelAndTermAndCancelledFalse(year, term)) {
            current.put(s.getStudent().getStudentId(), s);
        }
        LocalDateTime now = LocalDateTime.now();
        int added = 0, changed = 0, removed = 0, busOnly = 0;

        for (Change c : in.changes() == null ? List.<Change>of() : in.changes()) {
            TransportSubscription old = current.get(c.studentId());
            if (c.routeId() == null) {                                      // stop using transport
                if (old != null) { cancel(old, "Stopped using transport", who, now); removed++; }
                continue;
            }
            TransportRoute route = routeRepository.findById(c.routeId()).orElseThrow(() -> notFound("Destination not found."));
            TransportSubscription.Direction dir;
            try { dir = TransportSubscription.Direction.valueOf(String.valueOf(c.direction()).trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw bad("Choose one way or two ways."); }
            Vehicle vehicle = c.vehicleId() == null ? null
                    : vehicleRepository.findById(c.vehicleId()).orElseThrow(() -> notFound("Bus not found."));

            boolean two = dir == TransportSubscription.Direction.TWO_WAY;
            String what = "transport to " + route.getName() + " (" + label(dir) + ")";
            boolean sameTrip = old != null && old.getRoute().getRouteId().equals(route.getRouteId()) && old.getDirection() == dir;
            // No basis sent = keep the price the learner already has on this trip (or full term for a new one)
            boolean keepPrice = c.basis() == null || c.basis().isBlank();
            ChargePricing.Price price = keepPrice && sameTrip ? null
                    : ChargePricing.resolve(keepPrice ? null : c.basis(), c.quantity(), c.amount(), c.note(),
                        two ? route.getTwoWayTermly() : route.getOneWayTermly(),
                        two ? route.getTwoWayMonthly() : route.getOneWayMonthly(),
                        two ? route.getTwoWayDaily() : route.getOneWayDaily(), what);

            if (sameTrip && (price == null || (price.sameAs(old.getBasis(), old.getQuantity(), old.getAmount())
                    && Objects.equals(old.getPriceNote(), price.note())))) {
                // Same fare — only the bus may have changed
                Long oldBus = old.getVehicle() != null ? old.getVehicle().getVehicleId() : null;
                if (!Objects.equals(oldBus, c.vehicleId())) { old.setVehicle(vehicle); subscriptionRepository.save(old); busOnly++; }
                continue;
            }
            if (!sameTrip && !route.isActive()) throw bad(route.getName() + " is switched off. Switch it on, or choose another destination.");
            Student st;
            try { st = studentService.getById(c.studentId()); } catch (RuntimeException e) { throw notFound("Learner not found."); }

            if (old != null) {
                cancel(old, sameTrip ? "Price changed to " + price.amount().toPlainString() + price.label()
                                     : "Changed to " + route.getName() + " (" + label(dir) + ")", who, now);
                changed++;
            }
            else added++;

            TransportSubscription s = new TransportSubscription();
            s.setStudent(st);
            s.setRoute(route);
            s.setVehicle(vehicle);
            s.setDirection(dir);
            s.setSchoolClass(st.getSchoolClass());
            s.setYearLabel(year);
            s.setTerm(term);
            s.setDescription("Transport — " + route.getName() + " (" + label(dir) + ")");
            s.setAmount(price.amount());
            s.setBasis(price.basis().name());
            s.setQuantity(price.quantity());
            s.setRate(price.rate());
            s.setPriceNote(price.note());
            s.setCreatedBy(who);
            s.setCreatedAt(now);
            subscriptionRepository.save(s);
            current.put(st.getStudentId(), s);
        }
        return Map.of("added", added, "changed", changed, "removed", removed, "busOnly", busOnly,
                "message", "Transport saved: " + added + " new, " + changed + " changed, " + removed + " removed"
                        + (busOnly > 0 ? ", " + busOnly + " bus change(s)" : "") + ".");
    }

    // ══ helpers ═════════════════════════════════════════════════════════════
    private static void cancel(TransportSubscription s, String reason, String who, LocalDateTime now) {
        s.setCancelled(true);
        s.setCancelReason(reason);
        s.setCancelledBy(who);
        s.setCancelledAt(now);
    }

    private static String label(TransportSubscription.Direction d) { return d == TransportSubscription.Direction.TWO_WAY ? "two ways" : "one way"; }

    private static BigDecimal money(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0) throw bad(label + " must be more than 0.");
        if (v.stripTrailingZeros().scale() > 2) throw bad(label + " can have at most 2 decimal places.");
        if (v.compareTo(MAX_AMOUNT) > 0) throw bad(label + " looks too large — please check it.");
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String blankToNull(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static Map<String, Object> routeMap(TransportRoute r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("routeId", r.getRouteId());
        m.put("name", r.getName());
        m.put("oneWayMonthly", r.getOneWayMonthly());
        m.put("oneWayTermly", r.getOneWayTermly());
        m.put("twoWayMonthly", r.getTwoWayMonthly());
        m.put("twoWayTermly", r.getTwoWayTermly());
        m.put("oneWayDaily", r.getOneWayDaily());
        m.put("twoWayDaily", r.getTwoWayDaily());
        m.put("active", r.isActive());
        return m;
    }

    private static Map<String, Object> vehicleMap(Vehicle v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vehicleId", v.getVehicleId());
        m.put("registration", v.getRegistration());
        m.put("name", v.getName());
        m.put("capacity", v.getCapacity());
        m.put("driverName", v.getDriverName());
        m.put("driverPhone", v.getDriverPhone());
        m.put("active", v.isActive());
        return m;
    }

    private static String requireYear(String y) {
        String year = y == null ? "" : y.trim();
        if (!year.matches("\\d{4}")) throw bad("Year must be 4 digits, e.g. 2026.");
        return year;
    }

    private static void requireTerm(Integer t) { if (t == null || t < 1 || t > 3) throw bad("Term must be 1, 2 or 3."); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
