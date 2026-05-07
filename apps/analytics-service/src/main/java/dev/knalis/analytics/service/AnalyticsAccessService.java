package dev.knalis.analytics.service;

import dev.knalis.analytics.entity.StudentProgressSnapshot;
import dev.knalis.analytics.repository.RawAcademicEventRepository;
import dev.knalis.analytics.repository.StudentProgressSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsAccessService {

    private final StudentProgressSnapshotRepository studentProgressSnapshotRepository;
    private final RawAcademicEventRepository rawAcademicEventRepository;

    @Transactional(readOnly = true)
    public boolean canTeacherAccessStudent(UUID teacherId, UUID studentUserId) {
        List<UUID> groupIds = studentProgressSnapshotRepository
                .findAllByUserIdAndGroupIdIsNotNullOrderByUpdatedAtDesc(studentUserId).stream()
                .map(StudentProgressSnapshot::getGroupId)
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return false;
        }
        return rawAcademicEventRepository.existsByTeacherIdAndGroupIdIn(teacherId, groupIds);
    }
}
