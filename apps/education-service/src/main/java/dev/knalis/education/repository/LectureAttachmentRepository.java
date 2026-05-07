package dev.knalis.education.repository;

import dev.knalis.education.entity.LectureAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LectureAttachmentRepository extends JpaRepository<LectureAttachment, UUID> {

    List<LectureAttachment> findAllByLectureIdOrderByCreatedAtAsc(UUID lectureId);

    Optional<LectureAttachment> findByIdAndLectureId(UUID id, UUID lectureId);

    boolean existsByLectureId(UUID lectureId);
}

