package dev.knalis.schedule.service.common;

import dev.knalis.schedule.config.ScheduleSchemaGuardProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleSchemaGuardTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void schemaValidationPassesWhenSemesterNumberExists() {
        ScheduleSchemaGuardProperties properties = new ScheduleSchemaGuardProperties();
        properties.setEnabled(true);
        properties.setAutoRepair(false);
        properties.setSchema("schedule");

        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("schedule"),
                eq("academic_semesters")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("schedule"),
                eq("academic_semesters"),
                eq("semester_number")
        )).thenReturn(1);

        ScheduleSchemaGuard guard = new ScheduleSchemaGuard(jdbcTemplate, properties);
        assertDoesNotThrow(() -> guard.run(applicationArguments));

        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void missingColumnFailsFast() {
        ScheduleSchemaGuardProperties properties = new ScheduleSchemaGuardProperties();
        properties.setEnabled(true);
        properties.setAutoRepair(false);
        properties.setSchema("schedule");

        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("schedule"),
                eq("academic_semesters")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("schedule"),
                eq("academic_semesters"),
                eq("semester_number")
        )).thenReturn(0);

        ScheduleSchemaGuard guard = new ScheduleSchemaGuard(jdbcTemplate, properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> guard.run(applicationArguments));
        assertEquals(
                "Database schema is missing academic_semesters.semester_number. "
                        + "Database schema is not migrated. Run schedule-service Flyway migrations.",
                exception.getMessage()
        );
        verify(jdbcTemplate, never()).execute(contains("alter table"));
    }

    @Test
    void missingTableFailsFast() {
        ScheduleSchemaGuardProperties properties = new ScheduleSchemaGuardProperties();
        properties.setEnabled(true);
        properties.setAutoRepair(false);
        properties.setSchema("schedule");

        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("schedule"),
                eq("academic_semesters")
        )).thenReturn(0);

        ScheduleSchemaGuard guard = new ScheduleSchemaGuard(jdbcTemplate, properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> guard.run(applicationArguments));
        assertEquals(
                "Database schema is missing academic_semesters. "
                        + "Database schema is not migrated. Run schedule-service Flyway migrations.",
                exception.getMessage()
        );
        verify(jdbcTemplate, never()).update(anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }
}
