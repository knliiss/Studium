package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.request.CreateRoomRequest;
import dev.knalis.schedule.dto.request.UpdateRoomRequest;
import dev.knalis.schedule.dto.response.RoomResponse;
import dev.knalis.schedule.service.room.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule/rooms")
@RequiredArgsConstructor
public class RoomController {
    
    private final RoomService roomService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request);
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<RoomResponse> getRooms() {
        return roomService.getRooms();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public RoomResponse getRoom(@PathVariable("id") UUID roomId) {
        return roomService.getRoom(roomId);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public RoomResponse updateRoom(
            @PathVariable("id") UUID roomId,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        return roomService.updateRoom(roomId, request);
    }
}
