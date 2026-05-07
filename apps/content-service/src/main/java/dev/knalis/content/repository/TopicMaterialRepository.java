package dev.knalis.content.repository;

import dev.knalis.content.entity.TopicMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TopicMaterialRepository extends JpaRepository<TopicMaterial, UUID> {

    List<TopicMaterial> findAllByTopicIdOrderByCreatedAtAsc(UUID topicId);

    List<TopicMaterial> findAllByTopicIdAndVisibleTrueOrderByCreatedAtAsc(UUID topicId);
}

