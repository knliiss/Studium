package dev.knalis.schedule.controller;

import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.repository.RoomCapabilityRepository;
import dev.knalis.schedule.repository.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RoomCapabilitiesSecurityIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomCapabilityRepository roomCapabilityRepository;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("app.schedule.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
        registry.add("app.schedule.audit-service.base-url", () -> "http://localhost:8090");
        registry.add("app.schedule.audit-service.shared-secret", () -> "test-secret");
        registry.add("app.schedule.bootstrap.enabled", () -> false);
        registry.add("app.kafka.outbox.enabled", () -> false);
    }

    @AfterEach
    void tearDown() {
        roomCapabilityRepository.deleteAll();
        roomRepository.deleteAll();
    }

    @Test
    void teacherAndStudentCannotManageCapabilities() throws Exception {
        Room room = saveRoom();

        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", room.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", room.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageAndReadCapabilitiesAndIncludeInactive() throws Exception {
        Room room = saveRoom();

        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", room.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capabilities":[
                                  {"lessonType":"LABORATORY","priority":100,"active":true},
                                  {"lessonType":"PRACTICAL","priority":60,"active":true}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lessonType").value("LABORATORY"))
                .andExpect(jsonPath("$[0].priority").value(100));

        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", room.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capabilities":[
                                  {"lessonType":"PRACTICAL","priority":70,"active":true}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lessonType").value("PRACTICAL"))
                .andExpect(jsonPath("$[0].priority").value(70));

        mockMvc.perform(get("/api/v1/schedule/rooms/{id}/capabilities", room.getId())
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/schedule/rooms/{id}/capabilities", room.getId())
                        .queryParam("includeInactive", "true")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private Room saveRoom() {
        Room room = new Room();
        room.setCode("B-" + UUID.randomUUID());
        room.setBuilding("B");
        room.setFloor(2);
        room.setCapacity(30);
        room.setActive(true);
        return roomRepository.save(room);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt().jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("username", "user")
                        .tokenValue("test-token"))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
