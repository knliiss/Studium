package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, UUID> {
    
    List<ScheduleTemplate> findAllBySemesterIdAndActiveTrueOrderByCreatedAtAsc(UUID semesterId);

    List<ScheduleTemplate> findAllBySemesterIdOrderByCreatedAtAsc(UUID semesterId);
    
    List<ScheduleTemplate> findAllByGroupIdAndActiveTrueOrderByCreatedAtDesc(UUID groupId);

    List<ScheduleTemplate> findAllByGroupIdOrderByCreatedAtDesc(UUID groupId);
    
    List<ScheduleTemplate> findAllBySemesterIdInAndActiveTrue(Collection<UUID> semesterIds);
    
    List<ScheduleTemplate> findAllBySemesterIdAndDayOfWeekAndSlotIdAndActiveTrue(
            UUID semesterId,
            DayOfWeek dayOfWeek,
            UUID slotId
    );
}
