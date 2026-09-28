package jnm.engineer.demo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.services.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * On start-up, fills any EMPTY settings table from src/main/resources/default-settings.json.
 * Existing settings are never overwritten. No school values are written in Java code.
 */
@Slf4j
@Component
@Order(10)   // settings (10) → money groups (20) → timetable (100)
@RequiredArgsConstructor
public class SettingsSeeder implements ApplicationRunner {
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        ClassPathResource file = new ClassPathResource("default-settings.json");
        if (!file.exists()) {
            log.warn("default-settings.json not found — settings tables left as they are.");
            return;
        }
        try (InputStream in = file.getInputStream()) {
            SettingsService.Defaults defaults = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, SettingsService.Defaults.class);
            settingsService.seedDefaults(defaults);
        } catch (Exception e) {
            log.error("Could not read default-settings.json: {}", e.getMessage());
        }
    }
}
