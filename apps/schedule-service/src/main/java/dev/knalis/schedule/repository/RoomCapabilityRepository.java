package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.RoomCapability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomCapabilityRepository extends JpaRepository<RoomCapability, UUID> {

    List<RoomCapability> findAllByRoomIdOrderByPriorityDescCreatedAtAsc(UUID roomId);

    List<RoomCapability> findAllByRoomIdAndActiveTrueOrderByPriorityDescCreatedAtAsc(UUID roomId);
}
