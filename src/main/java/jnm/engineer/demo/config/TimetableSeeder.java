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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        ClassPathResource file = new ClassPathResource("default-settings.json");
        if (!file.exists()) return;
        try (InputStream in = file.getInputStream()) {
            TimetableService.Defaults d = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, TimetableService.Defaults.class);
            timetableService.seedDefaults(d).forEach(line -> log.info("Timetable: {}", line));
            // Once: add subjects still missing from grades that already had some numbers — e.g. shared
            // subjects (English, Maths, Kiswahili, CRE…) that the first version couldn't find.
            // Recorded in app_data_updates so a subject you later set to 0 is not brought back.
            String once = "09_timetable_fill_missing_subjects";
            Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM app_data_updates WHERE id = ?", Integer.class, once);
            if (done == null || done == 0) {
                timetableService.seedDefaults(d, true).forEach(line -> log.info("Timetable (missing subjects): {}", line));
                jdbc.update("INSERT INTO app_data_updates (id, applied_at, result, note) VALUES (?, NOW(), 'APPLIED', ?)", once, "lessons per week for missing subjects");
            }
            // Every lesson gets its currently allocated teacher (fixes lessons placed before teachers were allocated)
            log.info("Timetable teachers: {}", timetableService.syncTeachers(null).get("message"));
        } catch (Exception e) {
            log.error("Could not load the starting timetable: {}", e.getMessage());
        }
    }
}