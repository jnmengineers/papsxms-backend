package jnm.engineer.demo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.models.SmsLog;
import jnm.engineer.demo.models.SmsSettings;
import jnm.engineer.demo.repositories.SmsLogRepository;
import jnm.engineer.demo.repositories.SmsSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Sends SMS through Africa's Talking (form POST to /version1/messaging with an "apiKey" header).
 * The API key comes from the environment variable SMS_API_KEY when set (safest), else from the
 * settings saved by the admin; it is never returned to the screen. Every message is logged.
 */
@Service
@RequiredArgsConstructor
public class SmsService {
    static final String LIVE_URL = "https://api.africastalking.com/version1/messaging";
    static final String SANDBOX_URL = "https://api.sandbox.africastalking.com/version1/messaging";
    /** Tests point this at a local imitation of the provider. */
    static String urlOverride = null;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SmsSettingsRepository settingsRepository;
    private final SmsLogRepository logRepository;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record SettingsIn(String username, String apiKey, String senderId, Boolean sandbox, Boolean enabled) {}
    public record Outgoing(String phone, String message, Long guardianId) {}
    public record Result(String phone, boolean ok, String status, String cost, String error) {}

    @Transactional(readOnly = true)
    public Map<String, Object> settings() {
        SmsSettings s = current();
        String key = apiKey(s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", s.getUsername());
        m.put("senderId", s.getSenderId());
        m.put("sandbox", s.getSandbox() == null || s.getSandbox());
        m.put("enabled", Boolean.TRUE.equals(s.getEnabled()));
        m.put("keySaved", key != null);
        m.put("keyFrom", System.getenv("SMS_API_KEY") != null && !System.getenv("SMS_API_KEY").isBlank() ? "server" : key != null ? "settings" : null);
        m.put("keyEnding", key != null && key.length() > 4 ? key.substring(key.length() - 4) : null);
        m.put("ready", ready(s));
        return m;
    }

    @Transactional
    public Map<String, Object> saveSettings(SettingsIn in) {
        SmsSettings s = current();
        String user = in.username() == null ? null : in.username().trim();
        if (user == null || user.isEmpty()) throw bad("Enter the Africa's Talking username (\"sandbox\" for test mode).");
        if (in.senderId() != null && !in.senderId().isBlank() && !in.senderId().trim().matches("[A-Za-z0-9 ]{1,11}")) throw bad("A sender ID is up to 11 letters or digits.");
        s.setUsername(user.length() > 60 ? user.substring(0, 60) : user);
        if (in.apiKey() != null && !in.apiKey().isBlank()) s.setApiKey(in.apiKey().trim());      // blank = keep the saved key
        s.setSenderId(in.senderId() == null || in.senderId().isBlank() ? null : in.senderId().trim());
        s.setSandbox(in.sandbox() == null || in.sandbox());
        s.setEnabled(Boolean.TRUE.equals(in.enabled()));
        if (Boolean.TRUE.equals(s.getEnabled()) && apiKey(s) == null) throw bad("Enter the API key before switching SMS on.");
        settingsRepository.save(s);
        return Map.of("message", "SMS settings saved" + (Boolean.TRUE.equals(s.getEnabled()) ? (Boolean.TRUE.equals(s.getSandbox()) ? " — sandbox (test) mode." : " — LIVE: messages cost money.") : " — SMS is off."));
    }

    /** Sends each message on its own (each family's text is different). Returns one result per message. */
    @Transactional
    public List<Result> send(List<Outgoing> messages, String who) {
        SmsSettings s = current();
        if (!ready(s)) throw bad("SMS isn't set up. An admin enters the Africa's Talking details on Finance → Fee Reminders → SMS settings.");
        if (messages.size() > 500) throw bad("Send at most 500 messages at a time.");
        List<Result> out = new ArrayList<>();
        for (Outgoing m : messages) {
            Result r = sendOne(s, m.phone(), m.message());
            SmsLog l = new SmsLog();
            l.setPhone(m.phone()); l.setMessage(m.message().length() > 1000 ? m.message().substring(0, 1000) : m.message());
            l.setStatus(r.status()); l.setCost(r.cost()); l.setError(r.error() == null ? null : (r.error().length() > 300 ? r.error().substring(0, 300) : r.error()));
            l.setGuardianId(m.guardianId()); l.setSentAt(LocalDateTime.now()); l.setSentBy(who);
            logRepository.save(l);
            out.add(r);
        }
        return out;
    }

    private Result sendOne(SmsSettings s, String phone, String message) {
        try {
            StringBuilder form = new StringBuilder("username=").append(enc(s.getUsername())).append("&to=").append(enc(phone)).append("&message=").append(enc(message));
            if (s.getSenderId() != null && !Boolean.TRUE.equals(s.getSandbox())) form.append("&from=").append(enc(s.getSenderId()));
            String url = urlOverride != null ? urlOverride : (s.getSandbox() == null || s.getSandbox() ? SANDBOX_URL : LIVE_URL);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("apiKey", apiKey(s)).header("Accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 || res.statusCode() == 403) return new Result(phone, false, "FAILED", null, "The SMS provider refused the username or API key.");
            if (res.statusCode() >= 300) return new Result(phone, false, "FAILED", null, "The SMS provider answered " + res.statusCode() + ": " + shorten(res.body()));
            JsonNode recips = JSON.readTree(res.body()).path("SMSMessageData").path("Recipients");
            if (!recips.isArray() || recips.size() == 0) {
                return new Result(phone, false, "FAILED", null, JSON.readTree(res.body()).path("SMSMessageData").path("Message").asText("No recipient in the reply."));
            }
            JsonNode r = recips.get(0);
            String status = r.path("status").asText("");
            boolean ok = "Success".equalsIgnoreCase(status) || r.path("statusCode").asInt(0) == 101 || r.path("statusCode").asInt(0) == 100 || r.path("statusCode").asInt(0) == 102;
            return new Result(phone, ok, status.isEmpty() ? (ok ? "Success" : "FAILED") : status, r.path("cost").asText(null), ok ? null : "Not sent: " + status);
        } catch (Exception e) {
            return new Result(phone, false, "FAILED", null, "Could not reach the SMS provider (" + e.getClass().getSimpleName() + ").");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> log() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SmsLog l : logRepository.findTop200BySentAtAfterOrderBySentAtDesc(LocalDateTime.now().minusDays(90))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("phone", l.getPhone()); m.put("message", l.getMessage()); m.put("status", l.getStatus()); m.put("cost", l.getCost());
            m.put("error", l.getError()); m.put("sentAt", l.getSentAt() != null ? l.getSentAt().toString() : null); m.put("sentBy", l.getSentBy());
            out.add(m);
        }
        return out;
    }

    boolean ready() { return ready(current()); }
    private boolean ready(SmsSettings s) { return Boolean.TRUE.equals(s.getEnabled()) && s.getUsername() != null && apiKey(s) != null; }
    private static String apiKey(SmsSettings s) {
        String env = System.getenv("SMS_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        return s.getApiKey() == null || s.getApiKey().isBlank() ? null : s.getApiKey();
    }
    private SmsSettings current() { return settingsRepository.findById(1L).orElseGet(() -> { SmsSettings s = new SmsSettings(); s.setId(1L); s.setSandbox(true); s.setEnabled(false); return s; }); }
    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }
    private static String shorten(String v) { return v == null ? "" : v.length() > 150 ? v.substring(0, 150) : v; }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
