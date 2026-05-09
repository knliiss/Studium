package dev.knalis.schedule.service.room;

import dev.knalis.schedule.dto.request.CreateRoomRequest;
import dev.knalis.schedule.dto.request.UpdateRoomCapabilitiesRequest;
import dev.knalis.schedule.dto.request.UpdateRoomRequest;
import dev.knalis.schedule.dto.request.UpsertRoomCapabilityRequest;
import dev.knalis.schedule.dto.response.RoomCapabilityResponse;
import dev.knalis.schedule.dto.response.RoomResponse;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.entity.RoomCapability;
import dev.knalis.schedule.exception.RoomCapabilityAlreadyExistsException;
import dev.knalis.schedule.exception.RoomCapabilityInvalidPriorityException;
import dev.knalis.schedule.exception.RoomNotFoundException;
import dev.knalis.schedule.exception.ScheduleConflictException;
import dev.knalis.schedule.factory.room.RoomFactory;
import dev.knalis.schedule.mapper.RoomMapper;
import dev.knalis.schedule.repository.RoomCapabilityRepository;
import dev.knalis.schedule.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {
    
    private final RoomRepository roomRepository;
    private final RoomCapabilityRepository roomCapabilityRepository;
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

    @Transactional(readOnly = true)
    public List<RoomCapabilityResponse> getRoomCapabilities(UUID roomId, boolean includeInactive) {
        requireRoom(roomId);
        List<RoomCapability> capabilities = includeInactive
                ? roomCapabilityRepository.findAllByRoomIdOrderByPriorityDescCreatedAtAsc(roomId)
                : roomCapabilityRepository.findAllByRoomIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(roomId);
        return capabilities.stream().map(this::toCapabilityResponse).toList();
    }

    @Transactional
    public List<RoomCapabilityResponse> updateRoomCapabilities(UUID roomId, UpdateRoomCapabilitiesRequest request) {
        requireRoom(roomId);
        List<UpsertRoomCapabilityRequest> items = request.capabilities();
        LinkedHashSet<LessonType> lessonTypes = new LinkedHashSet<>();
        for (UpsertRoomCapabilityRequest item : items) {
            if (!lessonTypes.add(item.lessonType())) {
                throw new RoomCapabilityAlreadyExistsException(roomId, item.lessonType());
            }
            if (item.priority() == null || item.priority() <= 0) {
                throw new RoomCapabilityInvalidPriorityException(roomId, item.lessonType(), item.priority());
            }
        }

        Map<LessonType, RoomCapability> existingByType = roomCapabilityRepository
                .findAllByRoomIdOrderByPriorityDescCreatedAtAsc(roomId).stream()
                .collect(LinkedHashMap::new, (map, capability) -> map.put(capability.getLessonType(), capability), Map::putAll);

        for (UpsertRoomCapabilityRequest item : items) {
            RoomCapability capability = existingByType.get(item.lessonType());
            if (capability == null) {
                capability = new RoomCapability();
                capability.setRoomId(roomId);
                capability.setLessonType(item.lessonType());
            }
            capability.setPriority(item.priority());
            capability.setActive(item.active());
            roomCapabilityRepository.save(capability);
            existingByType.remove(item.lessonType());
        }

        for (RoomCapability remaining : existingByType.values()) {
            if (remaining.isActive()) {
                remaining.setActive(false);
                roomCapabilityRepository.save(remaining);
            }
        }

        return roomCapabilityRepository.findAllByRoomIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(roomId).stream()
                .map(this::toCapabilityResponse)
                .toList();
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

    private Room requireRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException(roomId));
    }

    private RoomCapabilityResponse toCapabilityResponse(RoomCapability capability) {
        return new RoomCapabilityResponse(
                capability.getId(),
                capability.getRoomId(),
                capability.getLessonType(),
                capability.getPriority(),
                capability.isActive(),
                capability.getCreatedAt(),
                capability.getUpdatedAt()
        );
    }
}
