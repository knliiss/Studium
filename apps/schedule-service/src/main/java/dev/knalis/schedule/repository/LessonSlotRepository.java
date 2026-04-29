package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.LessonSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonSlotRepository extends JpaRepository<LessonSlot, UUID> {
    
    boolean existsByNumber(Integer number);
    
    boolean existsByNumberAndIdNot(Integer number, UUID id);
    
    List<LessonSlot> findAllByOrderByNumberAsc();
}
