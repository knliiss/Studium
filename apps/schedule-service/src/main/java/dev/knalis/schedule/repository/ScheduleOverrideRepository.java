package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.ScheduleOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScheduleOverrideRepository extends JpaRepository<ScheduleOverride, UUID> {
    
    List<ScheduleOverride> findAllByDateOrderByCreatedAtAsc(LocalDate date);
    
    List<ScheduleOverride> findAllBySemesterIdInAndDateBetween(Collection<UUID> semesterIds, LocalDate dateFrom, LocalDate dateTo);
    
    List<ScheduleOverride> findAllByTemplateIdAndDate(UUID templateId, LocalDate date);
    
    List<ScheduleOverride> findAllBySemesterIdAndDateAndSlotId(UUID semesterId, LocalDate date, UUID slotId);
}
