package dev.knalis.education.service.common;

import dev.knalis.education.config.EducationSchemaGuardProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationSchemaGuardTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void schemaValidationPassesWhenAcademicStructureExists() {
        EducationSchemaGuardProperties properties = new EducationSchemaGuardProperties();
        properties.setEnabled(true);
        properties.setAutoRepair(false);
        properties.setSchema("education");

        mockGroupsTableExists();
        mockCompleteAcademicStructure();

        EducationSchemaGuard guard = new EducationSchemaGuard(jdbcTemplate, properties);
        guard.run(applicationArguments);

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    void missingAcademicStructureFailsFast() {
        EducationSchemaGuardProperties properties = new EducationSchemaGuardProperties();
        properties.setEnabled(true);
        properties.setAutoRepair(false);
        properties.setSchema("education");

        mockGroupsTableExists();
        mockMissingAcademicStructure();

        EducationSchemaGuard guard = new EducationSchemaGuard(jdbcTemplate, properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> guard.run(applicationArguments));
        assertEquals(
                "Database schema is missing education academic structure columns. "
                        + "Missing: groups.specialty_id, groups.study_year, groups.stream_id, groups.subgroup_mode, specialties, streams, curriculum_plans, group_curriculum_overrides. "
                        + "Database schema is not migrated. Run education-service Flyway migrations.",
                exception.getMessage()
        );
        verify(jdbcTemplate, never()).execute(contains("alter table"));
    }

    @Test
    void missingGroupsTableFailsFast() {
        EducationSchemaGuardProperties properties = new EducationSchemaGuardProperties();
        properties.setEnabled(true);
        properties.setAutoRepair(false);
        properties.setSchema("education");

        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("groups")
        )).thenReturn(0);

        EducationSchemaGuard guard = new EducationSchemaGuard(jdbcTemplate, properties);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> guard.run(applicationArguments));
        assertEquals(
                "Database schema is missing education.groups. "
                        + "Database schema is not migrated. Run education-service Flyway migrations.",
                exception.getMessage()
        );
        verify(jdbcTemplate, never()).update(anyString());
    }

    private void mockGroupsTableExists() {
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("groups")
        )).thenReturn(1);
    }

    private void mockMissingAcademicStructure() {
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("specialty_id")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("study_year")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("stream_id")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("subgroup_mode")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("specialties")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("streams")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("curriculum_plans")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("group_curriculum_overrides")
        )).thenReturn(0);
    }

    private void mockCompleteAcademicStructure() {
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("specialty_id")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("study_year")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("stream_id")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.columns"),
                eq(Integer.class),
                eq("education"),
                eq("groups"),
                eq("subgroup_mode")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("specialties")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("streams")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("curriculum_plans")
        )).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("information_schema.tables"),
                eq(Integer.class),
                eq("education"),
                eq("group_curriculum_overrides")
        )).thenReturn(1);
    }
}
