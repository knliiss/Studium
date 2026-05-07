package dev.knalis.education.repository;

import dev.knalis.education.entity.Lecture;
import dev.knalis.education.entity.LectureStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface LectureRepository extends JpaRepository<Lecture, UUID> {

    Page<Lecture> findAllByTopicId(UUID topicId, Pageable pageable);

    Page<Lecture> findAllByTopicIdAndStatusIn(UUID topicId, Collection<LectureStatus> statuses, Pageable pageable);
}

