package dev.knalis.schedule.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.schedule.dto.response.RoomCapabilityResponse;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.exception.RoomCapabilityAlreadyExistsException;
import dev.knalis.schedule.exception.RoomCapabilityInvalidPriorityException;
import dev.knalis.schedule.exception.RoomNotFoundException;
import dev.knalis.schedule.service.room.RoomService;
import dev.knalis.shared.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import(GlobalExceptionHandler.class)
class RoomCapabilityControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoomService roomService;

    @Test
    void ownerCanUpdateRoomCapabilities() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID capabilityId = UUID.randomUUID();
        when(roomService.updateRoomCapabilities(eq(roomId), any())).thenReturn(List.of(new RoomCapabilityResponse(
                capabilityId,
                roomId,
                LessonType.LABORATORY,
                100,
                true,
                Instant.now(),
                Instant.now()
        )));

        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", roomId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "capabilities", List.of(Map.of(
                                        "lessonType", "LABORATORY",
                                        "priority", 100,
                                        "active", true
                                ))
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(capabilityId.toString()))
                .andExpect(jsonPath("$[0].lessonType").value("LABORATORY"))
                .andExpect(jsonPath("$[0].priority").value(100))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void includeInactiveFilterIsPassedToService() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomService.getRoomCapabilities(roomId, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedule/rooms/{id}/capabilities", roomId)
                        .queryParam("includeInactive", "true")
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT")))
                .andExpect(status().isOk());

        verify(roomService).getRoomCapabilities(roomId, true);
    }

    @Test
    void invalidPriorityReturnsValidationError() throws Exception {
        UUID roomId = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", roomId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "capabilities", List.of(Map.of(
                                        "lessonType", "PRACTICAL",
                                        "priority", 0,
                                        "active", true
                                ))
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void duplicateTypeReturnsConflictFromService() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(roomService.updateRoomCapabilities(eq(roomId), any()))
                .thenThrow(new RoomCapabilityAlreadyExistsException(roomId, LessonType.PRACTICAL));

        mockMvc.perform(put("/api/v1/schedule/rooms/{id}/capabilities", roomId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "capabilities", List.of(Map.of(
                                        "lessonType", "PRACTICAL",
                                        "priority", 10,
                                        "active", true
                                ))
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_CAPABILITY_ALREADY_EXISTS"));
    }

    @Test
    void missingRoomReturnsNotFoundEnvelopeWithRequestId() throws Exception {
        UUID roomId = UUID.randomUUID();
        String requestId = "room-capabilities-not-found";
        when(roomService.getRoomCapabilities(roomId, false)).thenThrow(new RoomNotFoundException(roomId));

        mockMvc.perform(get("/api/v1/schedule/rooms/{id}/capabilities", roomId)
                        .header("X-Request-Id", requestId)
                        .with(jwtFor(UUID.randomUUID(), "ROLE_STUDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt().jwt(jwt -> jwt
                        .subject(userId.toString())
                        .claim("username", "user")
                        .tokenValue("test-token"))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
