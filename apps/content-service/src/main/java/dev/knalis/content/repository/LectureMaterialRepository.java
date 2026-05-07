package dev.knalis.content.repository;

import dev.knalis.content.entity.LectureMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LectureMaterialRepository extends JpaRepository<LectureMaterial, UUID> {

    List<LectureMaterial> findAllByLectureIdOrderByCreatedAtAsc(UUID lectureId);
}

