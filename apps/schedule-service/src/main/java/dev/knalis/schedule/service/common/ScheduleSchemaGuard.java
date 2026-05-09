package dev.knalis.schedule.service.common;

import dev.knalis.schedule.config.ScheduleSchemaGuardProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ScheduleSchemaGuard implements ApplicationRunner {

    private static final String SEMESTER_TABLE = "academic_semesters";
    private static final String SEMESTER_NUMBER_COLUMN = "semester_number";

    private final JdbcTemplate jdbcTemplate;
    private final ScheduleSchemaGuardProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Schedule schema guard is disabled.");
            return;
        }

        String schema = properties.getSchema();
        if (!tableExists(schema, SEMESTER_TABLE)) {
            throw new IllegalStateException(
                    "Database schema is missing " + SEMESTER_TABLE + ". "
                            + "Database schema is not migrated. Run schedule-service Flyway migrations."
            );
        }

        if (!columnExists(schema, SEMESTER_TABLE, SEMESTER_NUMBER_COLUMN)) {
            throw new IllegalStateException(
                    "Database schema is missing academic_semesters.semester_number. "
                            + "Database schema is not migrated. Run schedule-service Flyway migrations."
            );
        }
    }

    private boolean tableExists(String schema, String table) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
                Integer.class,
                schema,
                table
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String schema, String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = ? and table_name = ? and column_name = ?",
                Integer.class,
                schema,
                table,
                column
        );
        return count != null && count > 0;
    }
}
