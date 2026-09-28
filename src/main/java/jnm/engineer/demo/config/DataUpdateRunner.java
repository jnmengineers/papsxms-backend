package jnm.engineer.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * One-time data updates, run automatically on start-up — nothing to run by hand in MySQL.
 *
 * Each script in src/main/resources/db/updates runs ONCE per database and is then recorded in
 * the app_data_updates table, so it never runs again. Data scripts (fees, charges, transport)
 * only load when their table is still empty, so they never overwrite what the bursar has
 * entered — on an existing database they are simply recorded as "skipped".
 *
 * Order: after school settings (10), before money groups (20, which puts Lunch/Porridge in Meals)
 * and the timetable (100). A failing script is logged and retried on the next start; the app
 * still starts.
 */
@Slf4j
@Component
@Order(15)
@RequiredArgsConstructor
public class DataUpdateRunner implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    /** id (= file name without .sql), and a "load only if this count is 0" check (null = always). */
    private record Update(String id, String onlyIfNoneIn, String description) {}

    private static final List<Update> UPDATES = List.of(
            new Update("01_finance_role", null, "bursar (ACCOUNTANT) role"),
            new Update("02_link_teacher_logins", null, "link teacher logins to teacher records"),
            new Update("03_fee_structures_2026", "SELECT COUNT(*) FROM fee_structures WHERE year_label = '2026'", "2026 fee structures"),
            new Update("04_extra_charges_2026", "SELECT COUNT(*) FROM optional_charges", "2026 extra charges"),
            new Update("05_transport_routes_2026", "SELECT COUNT(*) FROM transport_routes", "2026 transport destinations and fares"),
            new Update("06_jss_meals_split", null, "Junior School meals as Lunch + Porridge"),
            new Update("07_student_names_uppercase", null, "learner names in capitals"),
            new Update("08_teachers_tick_meals", null, "class teachers can tick Lunch and Porridge"));

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_data_updates ("
                + " id VARCHAR(100) NOT NULL PRIMARY KEY,"
                + " applied_at DATETIME NOT NULL,"
                + " result VARCHAR(20) NOT NULL,"
                + " note VARCHAR(255))");
        for (Update u : UPDATES) {
            Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM app_data_updates WHERE id = ?", Integer.class, u.id());
            if (done != null && done > 0) continue;
            try {
                if (u.onlyIfNoneIn() != null) {
                    Integer existing = jdbc.queryForObject(u.onlyIfNoneIn(), Integer.class);
                    if (existing != null && existing > 0) {
                        record(u, "SKIPPED", existing + " row(s) already there — left as they are");
                        log.info("Data update {} ({}): skipped, data already there", u.id(), u.description());
                        continue;
                    }
                }
                runScript(u.id());
                record(u, "APPLIED", u.description());
                log.info("Data update {} ({}): applied", u.id(), u.description());
            } catch (Exception e) {
                // Not recorded, so it is tried again on the next start. The app keeps starting.
                log.error("Data update {} ({}) FAILED — will retry on next start: {}", u.id(), u.description(), e.getMessage());
            }
        }
    }

    /** Runs one script in its own transaction: all of it, or none of it. */
    private void runScript(String id) throws Exception {
        ClassPathResource file = new ClassPathResource("db/updates/" + id + ".sql");
        try (Connection c = dataSource.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(c, file);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    private void record(Update u, String result, String note) {
        jdbc.update("INSERT INTO app_data_updates (id, applied_at, result, note) VALUES (?, NOW(), ?, ?)",
                u.id(), result, note.length() > 255 ? note.substring(0, 255) : note);
    }
}
