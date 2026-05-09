package dev.knalis.education.service.common;

import dev.knalis.education.config.EducationSchemaGuardProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class EducationSchemaGuard implements ApplicationRunner {

    private static final String GROUPS_TABLE = "groups";
    private static final String SPECIALTIES_TABLE = "specialties";
    private static final String STREAMS_TABLE = "streams";
    private static final String CURRICULUM_PLANS_TABLE = "curriculum_plans";
    private static final String GROUP_CURRICULUM_OVERRIDES_TABLE = "group_curriculum_overrides";

    private static final String SPECIALTY_ID_COLUMN = "specialty_id";
    private static final String STUDY_YEAR_COLUMN = "study_year";
    private static final String STREAM_ID_COLUMN = "stream_id";
    private static final String SUBGROUP_MODE_COLUMN = "subgroup_mode";

    private final JdbcTemplate jdbcTemplate;
    private final EducationSchemaGuardProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("Education schema guard is disabled.");
            return;
        }

        String schema = properties.getSchema();
        if (!tableExists(schema, GROUPS_TABLE)) {
            throw new IllegalStateException(
                    "Database schema is missing education.groups. "
                            + "Database schema is not migrated. Run education-service Flyway migrations."
            );
        }

        List<String> missingElements = missingElements(schema);
        if (!missingElements.isEmpty()) {
            throw new IllegalStateException(
                    "Database schema is missing education academic structure columns. "
                            + "Missing: " + String.join(", ", missingElements) + ". "
                            + "Database schema is not migrated. Run education-service Flyway migrations."
            );
        }
    }

    private List<String> missingElements(String schema) {
        List<String> missing = new ArrayList<>();
        if (!columnExists(schema, GROUPS_TABLE, SPECIALTY_ID_COLUMN)) {
            missing.add("groups.specialty_id");
        }
        if (!columnExists(schema, GROUPS_TABLE, STUDY_YEAR_COLUMN)) {
            missing.add("groups.study_year");
        }
        if (!columnExists(schema, GROUPS_TABLE, STREAM_ID_COLUMN)) {
            missing.add("groups.stream_id");
        }
        if (!columnExists(schema, GROUPS_TABLE, SUBGROUP_MODE_COLUMN)) {
            missing.add("groups.subgroup_mode");
        }
        if (!tableExists(schema, SPECIALTIES_TABLE)) {
            missing.add(SPECIALTIES_TABLE);
        }
        if (!tableExists(schema, STREAMS_TABLE)) {
            missing.add(STREAMS_TABLE);
        }
        if (!tableExists(schema, CURRICULUM_PLANS_TABLE)) {
            missing.add(CURRICULUM_PLANS_TABLE);
        }
        if (!tableExists(schema, GROUP_CURRICULUM_OVERRIDES_TABLE)) {
            missing.add(GROUP_CURRICULUM_OVERRIDES_TABLE);
        }
        return missing;
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
