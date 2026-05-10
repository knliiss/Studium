package dev.knalis.education.repository;

import dev.knalis.education.entity.TopicMaterial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TopicMaterialRepository extends JpaRepository<TopicMaterial, UUID> {

    Page<TopicMaterial> findAllByTopicId(UUID topicId, Pageable pageable);

    Page<TopicMaterial> findAllByTopicIdAndVisibleIsTrueAndArchivedIsFalse(UUID topicId, Pageable pageable);
}

