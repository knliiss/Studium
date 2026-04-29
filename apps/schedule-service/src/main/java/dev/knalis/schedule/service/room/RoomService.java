package dev.knalis.schedule.service.room;

import dev.knalis.schedule.dto.request.CreateRoomRequest;
import dev.knalis.schedule.dto.request.UpdateRoomRequest;
import dev.knalis.schedule.dto.response.RoomResponse;
import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.exception.RoomNotFoundException;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.factory.room.RoomFactory;
import dev.knalis.schedule.mapper.RoomMapper;
import dev.knalis.schedule.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {
    
    private final RoomRepository roomRepository;
    private final RoomFactory roomFactory;
    private final RoomMapper roomMapper;
    
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        assertCodeAvailable(request.code(), null);
        
        Room room = roomFactory.newRoom(
                request.code(),
                request.building(),
                request.floor(),
                request.capacity(),
                request.active()
        );
        
        return roomMapper.toResponse(roomRepository.save(room));
    }
    
    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAllByOrderByCodeAsc().stream()
                .map(roomMapper::toResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
        return roomMapper.toResponse(room);
    }
    
    @Transactional
    public RoomResponse updateRoom(UUID roomId, UpdateRoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
        
        assertCodeAvailable(request.code(), roomId);
        
        room.setCode(request.code().trim());
        room.setBuilding(request.building().trim());
        room.setFloor(request.floor());
        room.setCapacity(request.capacity());
        room.setActive(request.active());
        
        return roomMapper.toResponse(roomRepository.save(room));
    }
    
    private void assertCodeAvailable(String code, UUID roomId) {
        boolean exists = roomId == null
                ? roomRepository.existsByCodeIgnoreCase(code.trim())
                : roomRepository.existsByCodeIgnoreCaseAndIdNot(code.trim(), roomId);
        
        if (exists) {
            throw new ScheduleConflictException(
                    "ROOM_CODE_ALREADY_EXISTS",
                    "Room code already exists",
                    Map.of("code", code.trim())
            );
        }
    }
}
