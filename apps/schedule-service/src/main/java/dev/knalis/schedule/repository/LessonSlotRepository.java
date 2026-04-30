package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.LessonSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonSlotRepository extends JpaRepository<LessonSlot, UUID> {

    boolean existsByNumber(Integer number);

    boolean existsByNumberAndIdNot(Integer number, UUID id);

    Optional<LessonSlot> findByNumber(Integer number);

    List<LessonSlot> findAllByOrderByNumberAsc();
}
