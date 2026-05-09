package dev.knalis.schedule.service.room;

import dev.knalis.schedule.dto.request.UpdateRoomCapabilitiesRequest;
import dev.knalis.schedule.dto.request.UpsertRoomCapabilityRequest;
import dev.knalis.schedule.entity.LessonType;
import dev.knalis.schedule.entity.Room;
import dev.knalis.schedule.entity.RoomCapability;
import dev.knalis.schedule.exception.RoomCapabilityAlreadyExistsException;
import dev.knalis.schedule.exception.RoomCapabilityInvalidPriorityException;
import dev.knalis.schedule.factory.room.RoomFactory;
import dev.knalis.schedule.mapper.RoomMapper;
import dev.knalis.schedule.repository.RoomCapabilityRepository;
import dev.knalis.schedule.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomCapabilityRepository roomCapabilityRepository;

    @Mock
    private RoomMapper roomMapper;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, roomCapabilityRepository, new RoomFactory(), roomMapper);
    }

    @Test
    void updateCapabilitiesRejectsDuplicateLessonTypes() {
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room(roomId)));

        assertThrows(
                RoomCapabilityAlreadyExistsException.class,
                () -> roomService.updateRoomCapabilities(
                        roomId,
                        new UpdateRoomCapabilitiesRequest(List.of(
                                new UpsertRoomCapabilityRequest(LessonType.LABORATORY, 100, true),
                                new UpsertRoomCapabilityRequest(LessonType.LABORATORY, 90, true)
                        ))
                )
        );
    }

    @Test
    void updateCapabilitiesRejectsInvalidPriority() {
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room(roomId)));
        assertThrows(
                RoomCapabilityInvalidPriorityException.class,
                () -> roomService.updateRoomCapabilities(
                        roomId,
                        new UpdateRoomCapabilitiesRequest(List.of(
                                new UpsertRoomCapabilityRequest(LessonType.PRACTICAL, 0, true)
                        ))
                )
        );
    }

    @Test
    void getRoomCapabilitiesReturnsActiveOnlyByDefault() {
        UUID roomId = UUID.randomUUID();
        RoomCapability capability = new RoomCapability();
        capability.setId(UUID.randomUUID());
        capability.setRoomId(roomId);
        capability.setLessonType(LessonType.LECTURE);
        capability.setPriority(20);
        capability.setActive(true);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room(roomId)));
        when(roomCapabilityRepository.findAllByRoomIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(roomId))
                .thenReturn(List.of(capability));

        assertEquals(1, roomService.getRoomCapabilities(roomId, false).size());
    }

    private Room room(UUID id) {
        Room room = new Room();
        room.setId(id);
        room.setCode("A-101");
        room.setBuilding("A");
        room.setFloor(1);
        room.setCapacity(30);
        room.setActive(true);
        return room;
    }
}
