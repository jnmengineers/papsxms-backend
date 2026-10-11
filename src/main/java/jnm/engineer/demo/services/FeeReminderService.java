package jnm.engineer.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.FeeReminderLog;
import jnm.engineer.demo.models.FeeReminderSettings;
import jnm.engineer.demo.models.Guardian;
import jnm.engineer.demo.repositories.FeeReminderLogRepository;
import jnm.engineer.demo.repositories.FeeReminderSettingsRepository;
import jnm.engineer.demo.repositories.GuardianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;

/**
 * Fee reminders: one message per family (a parent with every owing child and balance), from the
 * bursar's template. Sending happens outside (WhatsApp link, or a CSV for a bulk-SMS service);
 * marking a family as reminded keeps a log.
 */
@Service
@RequiredArgsConstructor
public class FeeReminderService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CHANNELS = Set.of("WHATSAPP", "SMS", "CALL", "OTHER");
    private final FinanceService financeService;
    private final GuardianService guardianService;
    private final GuardianRepository guardianRepository;
    private final FeeReminderLogRepository logRepository;
    private final FeeReminderSettingsRepository settingsRepository;
    private final SmsService smsService;

    public record SettingsIn(String template, BigDecimal minBalance) {}
    public record SentChild(Long studentId, BigDecimal balance) {}
    public record SentFamily(Long guardianId, List<SentChild> children) {}
    public record SentIn(String channel, List<SentFamily> families) {}
    public record SmsIn(List<Long> guardianIds, Long classId, BigDecimal minBalance) {}

    @Transactional
    public Map<String, Object> settings() {
        FeeReminderSettings s = current();
        return Map.of("template", s.getTemplate(), "minBalance", s.getMinBalance());
    }

    @Transactional
    public Map<String, Object> saveSettings(SettingsIn in) {
        String t = in.template() == null ? "" : in.template().trim();
        if (t.length() < 20) throw bad("Write the reminder message.");
        if (t.length() > 1000) throw bad("Keep the message under 1000 characters.");
        if (!t.contains("{children}") && !t.contains("{total}")) throw bad("Include {children} or {total} so the parent sees the balance.");
        if (in.minBalance() == null || in.minBalance().signum() < 0) throw bad("Enter the smallest balance to remind about (0 or more).");
        FeeReminderSettings s = current();
        s.setTemplate(t);
        s.setMinBalance(in.minBalance().setScale(2, RoundingMode.HALF_UP));
        settingsRepository.save(s);
        return Map.of("message", "Reminder message saved.");
    }

    /** Families to remind: each parent with their owing children (balance ≥ min), and owing learners with no parent recorded. */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> families(Long classId, BigDecimal minBalance) {
        FeeReminderSettings s = current();
        BigDecimal min = minBalance != null ? minBalance : s.getMinBalance();
        List<Map<String, Object>> rows = (List<Map<String, Object>>) financeService.balances(classId).get("students");
        Map<Long, Map<String, Object>> owing = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            BigDecimal bal = (BigDecimal) r.get("balance");
            if (bal != null && bal.signum() > 0 && bal.compareTo(min) >= 0) owing.put(((Number) r.get("studentId")).longValue(), r);
        }
        Map<Long, LocalDate> last = new HashMap<>();
        for (FeeReminderLog l : logRepository.findBySentOnGreaterThanEqual(LocalDate.now().minusYears(1))) {
            if (l.getGuardianId() != null) last.merge(l.getGuardianId(), l.getSentOn(), (a, b) -> a.isAfter(b) ? a : b);
        }
        Set<Long> covered = new HashSet<>();
        List<Map<String, Object>> families = new ArrayList<>();
        Map<Long, List<Long>> kids = guardianService.childrenByGuardian();
        for (Guardian g : guardianRepository.findAllByOrderByFullNameAsc()) {
            List<Map<String, Object>> children = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Long sid : kids.getOrDefault(g.getGuardianId(), List.of())) {
                Map<String, Object> r = owing.get(sid);
                if (r == null) continue;
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("studentId", sid);
                c.put("name", r.get("name"));
                c.put("className", r.get("className"));
                c.put("admissionNumber", r.get("admissionNumber"));
                c.put("balance", r.get("balance"));
                children.add(c);
                total = total.add((BigDecimal) r.get("balance"));
                covered.add(sid);
            }
            if (children.isEmpty()) continue;
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("guardianId", g.getGuardianId());
            f.put("parent", g.getFullName());
            f.put("phone", g.getPhone());
            f.put("children", children);
            f.put("total", total);
            f.put("lastReminded", last.containsKey(g.getGuardianId()) ? last.get(g.getGuardianId()).toString() : null);
            f.put("message", message(s.getTemplate(), g.getFullName(), children, total));
            families.add(f);
        }
        List<Map<String, Object>> noParent = owing.entrySet().stream().filter(e -> !covered.contains(e.getKey())).map(Map.Entry::getValue).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minBalance", min);
        out.put("template", s.getTemplate());
        out.put("families", families);
        out.put("noParent", noParent);
        out.put("smsReady", smsService.ready());
        out.put("totalOwed", families.stream().map(f -> (BigDecimal) f.get("total")).reduce(BigDecimal.ZERO, BigDecimal::add));
        return out;
    }

    @Transactional
    public Map<String, Object> markSent(SentIn in, String who) {
        String ch = in == null || in.channel() == null ? "OTHER" : in.channel().trim().toUpperCase();
        if (!CHANNELS.contains(ch)) throw bad("How was it sent? WhatsApp, SMS, call or other.");
        if (in.families() == null || in.families().isEmpty()) throw bad("Choose the families that were reminded.");
        LocalDate today = LocalDate.now();
        int n = 0;
        for (SentFamily f : in.families()) {
            for (SentChild c : f.children() == null ? List.<SentChild>of() : f.children()) {
                FeeReminderLog l = new FeeReminderLog();
                l.setGuardianId(f.guardianId());
                l.setStudentId(c.studentId());
                l.setSentOn(today);
                l.setChannel(ch);
                l.setBalance(c.balance());
                l.setSentBy(who);
                logRepository.save(l);
            }
            n++;
        }
        return Map.of("message", n + " famil" + (n == 1 ? "y" : "ies") + " marked as reminded today.");
    }

    /**
     * Sends the reminder SMS to the chosen families. Messages are rebuilt here from the current
     * balances (not taken from the browser). Families whose SMS went through are marked as reminded.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> sendSms(SmsIn in, String who) {
        if (in == null || in.guardianIds() == null || in.guardianIds().isEmpty()) throw bad("Choose the families to send to.");
        Set<Long> want = new HashSet<>(in.guardianIds());
        List<Map<String, Object>> fams = ((List<Map<String, Object>>) families(in.classId(), in.minBalance()).get("families")).stream()
                .filter(f -> want.contains(((Number) f.get("guardianId")).longValue())).toList();
        if (fams.isEmpty()) throw bad("None of those families owes the amount chosen any more.");
        List<SmsService.Result> res = smsService.send(fams.stream().map(f -> new SmsService.Outgoing((String) f.get("phone"), (String) f.get("message"), ((Number) f.get("guardianId")).longValue())).toList(), who);
        List<Map<String, Object>> results = new ArrayList<>();
        List<SentFamily> ok = new ArrayList<>();
        for (int i = 0; i < fams.size(); i++) {
            Map<String, Object> f = fams.get(i);
            SmsService.Result r = res.get(i);
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("guardianId", f.get("guardianId")); x.put("parent", f.get("parent")); x.put("phone", f.get("phone"));
            x.put("ok", r.ok()); x.put("status", r.status()); x.put("cost", r.cost()); x.put("error", r.error());
            results.add(x);
            if (r.ok()) ok.add(new SentFamily(((Number) f.get("guardianId")).longValue(),
                    ((List<Map<String, Object>>) f.get("children")).stream().map(c -> new SentChild(((Number) c.get("studentId")).longValue(), (BigDecimal) c.get("balance"))).toList()));
        }
        if (!ok.isEmpty()) markSent(new SentIn("SMS", ok), who);
        long failed = results.size() - ok.size();
        return Map.of("results", results, "sent", ok.size(), "failed", failed,
                "message", ok.size() + " SMS sent" + (failed > 0 ? ", " + failed + " not sent — see the list." : "."));
    }

    static String message(String template, String parent, List<Map<String, Object>> children, BigDecimal total) {
        DecimalFormat k = new DecimalFormat("#,##0.00");
        StringJoiner kids = new StringJoiner("; ");
        for (Map<String, Object> c : children) {
            kids.add(c.get("name") + (c.get("className") != null ? " (" + c.get("className") + ")" : "") + " KES " + k.format(c.get("balance")));
        }
        String first = parent == null ? "" : parent.split("\\s+")[0];
        String p = first.isEmpty() ? "Parent" : first.charAt(0) + first.substring(1).toLowerCase();
        return template.replace("{parent}", p).replace("{children}", kids.toString()).replace("{total}", "KES " + k.format(total));
    }

    private FeeReminderSettings current() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            FeeReminderSettings s = new FeeReminderSettings();
            s.setId(1L);
            try (InputStream in = new ClassPathResource("default-settings.json").getInputStream()) {
                JsonNode n = JSON.readTree(in).get("feeReminder");
                s.setTemplate(n.get("template").asText());
                s.setMinBalance(new BigDecimal(n.get("minBalance").asText()).setScale(2, RoundingMode.HALF_UP));
            } catch (Exception e) {
                s.setTemplate("Dear {parent}, kindly note the fee balance for {children}. Total: {total}. Thank you.");
                s.setMinBalance(BigDecimal.ZERO.setScale(2));
            }
            return settingsRepository.save(s);
        });
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
