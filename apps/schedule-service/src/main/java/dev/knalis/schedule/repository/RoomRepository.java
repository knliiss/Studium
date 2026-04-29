package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    
    boolean existsByCodeIgnoreCase(String code);
    
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    
    List<Room> findAllByOrderByCodeAsc();
}
