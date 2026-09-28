package jnm.engineer.demo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.services.TimetableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * On start-up, loads the starting timetable ("timetable" in src/main/resources/default-settings.json):
 * bell times for a section that has none, and lessons per week for a grade that has none.
 * Runs after the settings seeder (sections and grades must exist). Never overwrites what the admin set.
 */
@Slf4j
@Component
@Order(100)   // after settings (10) and money groups (20)
@RequiredArgsConstructor
public class TimetableSeeder implements ApplicationRunner {
    private final TimetableService timetableService;

    @Override
    public void run(ApplicationArguments args) {
        ClassPathResource file = new ClassPathResource("default-settings.json");
        if (!file.exists()) return;
        try (InputStream in = file.getInputStream()) {
            TimetableService.Defaults d = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, TimetableService.Defaults.class);
            timetableService.seedDefaults(d).forEach(line -> log.info("Timetable: {}", line));
        } catch (Exception e) {
            log.error("Could not load the starting timetable: {}", e.getMessage());
        }
    }
}
