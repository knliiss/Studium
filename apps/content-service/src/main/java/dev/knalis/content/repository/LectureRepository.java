package dev.knalis.content.repository;

import dev.knalis.content.entity.Lecture;
import dev.knalis.content.entity.LectureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LectureRepository extends JpaRepository<Lecture, UUID> {

    List<Lecture> findAllByTopicIdOrderByOrderIndexAscCreatedAtAsc(UUID topicId);

    List<Lecture> findAllByTopicIdAndStatusOrderByOrderIndexAscCreatedAtAsc(UUID topicId, LectureStatus status);
}

