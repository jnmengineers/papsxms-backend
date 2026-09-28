package jnm.engineer.demo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jnm.engineer.demo.services.MoneyGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * On start-up, if there are NO money groups yet, creates the starting ones listed under
 * "moneyGroups" in src/main/resources/default-settings.json. Never changes existing groups.
 */
@Slf4j
@Component
@Order(20)   // settings (10) → money groups (20) → timetable (100)
@RequiredArgsConstructor
public class MoneyGroupSeeder implements ApplicationRunner {
    private final MoneyGroupService moneyGroupService;

    @Override
    public void run(ApplicationArguments args) {
        ClassPathResource file = new ClassPathResource("default-settings.json");
        if (!file.exists()) return;
        try (InputStream in = file.getInputStream()) {
            // Its own reader, so it doesn't depend on how the app's JSON mapper is set up
            MoneyGroupService.Defaults d = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, MoneyGroupService.Defaults.class);
            moneyGroupService.seedDefaults(d);
        } catch (Exception e) {
            log.error("Could not create the starting money groups: {}", e.getMessage());
        }
    }
}
