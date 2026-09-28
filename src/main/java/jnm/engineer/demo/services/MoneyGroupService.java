package jnm.engineer.demo.services;

import jnm.engineer.demo.models.FeeItemGroup;
import jnm.engineer.demo.models.MoneyGroup;
import jnm.engineer.demo.models.OptionalCharge;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Money groups ("vote heads") and what belongs to each:
 *   • fee-structure items, by name (e.g. "Exam fee" → Tuition & Fees)
 *   • extra charges (set on the charge itself, e.g. Lunch → Meals)
 *   • transport fares → the group marked "transport"
 * Anything not assigned goes to the group marked "fees default".
 * The order of the groups is the payment priority within a term (first = paid first).
 */
@Service
@RequiredArgsConstructor
public class MoneyGroupService {
    private final MoneyGroupRepository groupRepository;
    private final FeeItemGroupRepository feeItemGroupRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final InvoiceRepository invoiceRepository;
    private final OptionalChargeRepository chargeRepository;
    private final ExpenseRepository expenseRepository;

    public record GroupIn(Long groupId, String name, String color, Boolean active, Boolean feesDefault, Boolean transport) {}
    public record ItemIn(String itemName, Long groupId) {}
    public record ChargeGroupIn(Long chargeId, Long groupId) {}
    public record Seed(String name, String color, Boolean feesDefault, Boolean transport, List<String> charges) {}
    public record Defaults(List<Seed> moneyGroups) {}

    // ══ Read ════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (MoneyGroup g : groupRepository.findAllByOrderByPriorityAscNameAsc()) {
            Map<String, Object> m = groupMap(g);
            m.put("expenses", expenseRepository.countByGroupGroupId(g.getGroupId()));
            groups.add(m);
        }
        // Every fee item name we know of: fee structures + anything already on an invoice
        Map<String, String> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        feeStructureRepository.findAll().forEach(fs -> fs.getItems().forEach(i -> names.putIfAbsent(i.getName().trim(), i.getName().trim())));
        invoiceRepository.distinctLineNames().forEach(n -> { if (n != null) names.putIfAbsent(n.trim(), n.trim()); });
        feeItemGroupRepository.findAll().forEach(f -> names.putIfAbsent(f.getItemName(), f.getItemName()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (String n : names.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemName", n);
            m.put("groupId", feeItemGroupRepository.findByItemNameIgnoreCase(n).map(f -> f.getGroup().getGroupId()).orElse(null));
            items.add(m);
        }
        List<Map<String, Object>> charges = new ArrayList<>();
        for (OptionalCharge c : chargeRepository.findAllByOrderByActiveDescNameAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chargeId", c.getChargeId());
            m.put("name", c.getName());
            m.put("sections", c.sectionList());
            m.put("active", c.isActive());
            m.put("groupId", c.getGroup() != null ? c.getGroup().getGroupId() : null);
            charges.add(m);
        }
        return Map.of("groups", groups, "feeItems", items, "charges", charges);
    }

    // ══ Save the groups (the list's order = payment priority) ═══════════════
    @Transactional
    public Map<String, Object> saveGroups(List<GroupIn> in) {
        if (in == null || in.isEmpty()) throw bad("Add at least one group.");
        List<MoneyGroup> existing = groupRepository.findAll();
        Set<Long> sent = new HashSet<>();
        for (GroupIn g : in) if (g.groupId() != null) sent.add(g.groupId());
        for (MoneyGroup g : existing) {
            if (!sent.contains(g.getGroupId()))
                throw bad("\"" + g.getName() + "\" is missing. Groups can't be deleted — switch them off instead.");
        }
        Set<String> seen = new HashSet<>();
        int fees = 0, transport = 0;
        for (GroupIn g : in) {
            String name = g.name() == null ? "" : g.name().trim();
            if (name.isEmpty()) throw bad("Every group needs a name.");
            if (name.length() > 60) throw bad("\"" + name + "\" is too long (60 letters at most).");
            if (!seen.add(name.toLowerCase())) throw bad("Two groups are called \"" + name + "\".");
            boolean active = g.active() == null || g.active();
            if (Boolean.TRUE.equals(g.feesDefault())) { fees++; if (!active) throw bad("\"" + name + "\" can't be switched off while it's the fees group."); }
            if (Boolean.TRUE.equals(g.transport())) { transport++; if (!active) throw bad("\"" + name + "\" can't be switched off while it's the transport group."); }
        }
        if (fees != 1) throw bad("Choose exactly one group for school fees (and anything not given a group).");
        if (transport > 1) throw bad("Choose only one group for transport fares.");

        int priority = 1;
        for (GroupIn g : in) {
            MoneyGroup m = g.groupId() == null ? new MoneyGroup()
                    : groupRepository.findById(g.groupId()).orElseThrow(() -> bad("A group no longer exists — refresh the page."));
            m.setName(g.name().trim());
            m.setColor(color(g.color()));
            m.setActive(g.active() == null || g.active());
            m.setFeesDefault(Boolean.TRUE.equals(g.feesDefault()));
            m.setTransport(Boolean.TRUE.equals(g.transport()));
            m.setPriority(priority++);
            groupRepository.save(m);
        }
        return Map.of("message", "Money groups saved. Reports use the new order straight away.");
    }

    /** Which group each fee-structure item belongs to (empty group = the fees group). */
    @Transactional
    public Map<String, Object> saveItems(List<ItemIn> in) {
        int n = 0;
        for (ItemIn i : in == null ? List.<ItemIn>of() : in) {
            String name = i.itemName() == null ? "" : i.itemName().trim();
            if (name.isEmpty() || name.length() > 80) continue;
            Optional<FeeItemGroup> cur = feeItemGroupRepository.findByItemNameIgnoreCase(name);
            if (i.groupId() == null) { cur.ifPresent(feeItemGroupRepository::delete); n++; continue; }
            MoneyGroup g = groupRepository.findById(i.groupId()).orElseThrow(() -> bad("A group no longer exists — refresh the page."));
            FeeItemGroup f = cur.orElseGet(FeeItemGroup::new);
            f.setItemName(name);
            f.setGroup(g);
            feeItemGroupRepository.save(f);
            n++;
        }
        return Map.of("message", n + " fee item(s) saved.");
    }

    /** Which group each extra charge belongs to (empty group = the fees group). */
    @Transactional
    public Map<String, Object> saveChargeGroups(List<ChargeGroupIn> in) {
        int n = 0;
        for (ChargeGroupIn c : in == null ? List.<ChargeGroupIn>of() : in) {
            if (c.chargeId() == null) continue;
            OptionalCharge ch = chargeRepository.findById(c.chargeId()).orElseThrow(() -> bad("A charge no longer exists — refresh the page."));
            ch.setGroup(c.groupId() == null ? null
                    : groupRepository.findById(c.groupId()).orElseThrow(() -> bad("A group no longer exists — refresh the page.")));
            chargeRepository.save(ch);
            n++;
        }
        return Map.of("message", n + " charge(s) saved.");
    }

    // ══ Starting values (only when there are no groups yet) ═════════════════
    @Transactional
    public void seedDefaults(Defaults d) {
        if (d == null || d.moneyGroups() == null || d.moneyGroups().isEmpty() || groupRepository.count() > 0) return;
        int priority = 1;
        boolean anyFees = d.moneyGroups().stream().anyMatch(s -> Boolean.TRUE.equals(s.feesDefault()));
        List<OptionalCharge> charges = chargeRepository.findAll();
        for (Seed s : d.moneyGroups()) {
            if (s.name() == null || s.name().isBlank()) continue;
            MoneyGroup g = new MoneyGroup();
            g.setName(s.name().trim());
            g.setColor(color(s.color()));
            g.setPriority(priority);
            g.setFeesDefault(Boolean.TRUE.equals(s.feesDefault()) || (!anyFees && priority == 1));
            g.setTransport(Boolean.TRUE.equals(s.transport()));
            groupRepository.save(g);
            for (String chargeName : s.charges() == null ? List.<String>of() : s.charges()) {
                for (OptionalCharge c : charges) {
                    if (c.getGroup() == null && c.getName().equalsIgnoreCase(chargeName.trim())) { c.setGroup(g); chargeRepository.save(c); }
                }
            }
            priority++;
        }
    }

    // ══ For the reports ═════════════════════════════════════════════════════
    /** Answers "which group does this belong to?" — built once per report. */
    public final class Resolver {
        final Map<Long, MoneyGroup> groups = new LinkedHashMap<>();
        final Map<String, Long> itemGroups = new HashMap<>();
        Long feesDefault, transport;

        Resolver() {
            for (MoneyGroup g : groupRepository.findAllByOrderByPriorityAscNameAsc()) {
                groups.put(g.getGroupId(), g);
                if (g.isFeesDefault()) feesDefault = g.getGroupId();
                if (g.isTransport()) transport = g.getGroupId();
            }
            if (feesDefault == null && !groups.isEmpty()) feesDefault = groups.keySet().iterator().next();
            for (FeeItemGroup f : feeItemGroupRepository.findAll()) itemGroups.put(f.getItemName().trim().toLowerCase(), f.getGroup().getGroupId());
        }
        public Long ofFeeItem(String name) { return itemGroups.getOrDefault(name == null ? "" : name.trim().toLowerCase(), feesDefault); }
        public Long ofCharge(OptionalCharge c) { return c != null && c.getGroup() != null ? c.getGroup().getGroupId() : feesDefault; }
        public Long ofTransport() { return transport != null ? transport : feesDefault; }
        /** 1 = paid first; unknown groups last. */
        public int priority(Long groupId) { MoneyGroup g = groupId == null ? null : groups.get(groupId); return g == null || g.getPriority() == null ? 9999 : g.getPriority(); }
        public Collection<MoneyGroup> all() { return groups.values(); }
        public MoneyGroup get(Long id) { return id == null ? null : groups.get(id); }
    }

    @Transactional(readOnly = true)
    public Resolver resolver() { return new Resolver(); }

    public static Map<String, Object> groupMap(MoneyGroup g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupId", g.getGroupId());
        m.put("name", g.getName());
        m.put("color", g.getColor());
        m.put("priority", g.getPriority());
        m.put("active", g.isActive());
        m.put("feesDefault", g.isFeesDefault());
        m.put("transport", g.isTransport());
        return m;
    }

    private static String color(String c) {
        String v = c == null ? "" : c.trim();
        return v.matches("#[0-9A-Fa-f]{6}") ? v : "#6c757d";
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
