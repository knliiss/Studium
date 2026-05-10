package dev.knalis.gateway.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewaySecurityConfigContractTest {

    @Test
    void studentStartAndFinishRoutesRemainAllowedAndTeacherOnlyRuleStillExists() throws Exception {
        Path configPath = Path.of("src/main/java/dev/knalis/gateway/config/GatewaySecurityConfig.java");
        String content = Files.readString(configPath);

        assertTrue(content.contains(".pathMatchers(HttpMethod.POST, \"/api/v1/testing/tests/*/start\").hasAnyRole(\"OWNER\", \"ADMIN\", \"STUDENT\")"));
        assertTrue(content.contains(".pathMatchers(HttpMethod.POST, \"/api/v1/testing/tests/*/finish\").hasAnyRole(\"OWNER\", \"ADMIN\", \"STUDENT\")"));
        assertTrue(content.contains(".pathMatchers(HttpMethod.POST, \"/api/v1/testing/tests/**\").hasAnyRole(\"OWNER\", \"ADMIN\", \"TEACHER\")"));
    }
}
