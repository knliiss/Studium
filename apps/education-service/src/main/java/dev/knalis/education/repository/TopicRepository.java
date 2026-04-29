package dev.knalis.education.repository;

import dev.knalis.education.entity.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
    
    boolean existsBySubjectIdAndOrderIndex(UUID subjectId, int orderIndex);
    
    Page<Topic> findAllBySubjectId(UUID subjectId, Pageable pageable);

    Page<Topic> findAllByTitleContainingIgnoreCaseOrderByTitleAsc(String title, Pageable pageable);

    List<Topic> findAllBySubjectIdOrderByOrderIndexAscCreatedAtAsc(UUID subjectId);
}
