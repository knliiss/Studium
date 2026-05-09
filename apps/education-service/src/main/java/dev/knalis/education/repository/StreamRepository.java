package dev.knalis.education.repository;

import dev.knalis.education.entity.Stream;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StreamRepository extends JpaRepository<Stream, UUID> {

    List<Stream> findAllByOrderByNameAsc();

    List<Stream> findAllBySpecialtyIdOrderByNameAsc(UUID specialtyId);

    List<Stream> findAllByStudyYearOrderByNameAsc(Integer studyYear);

    List<Stream> findAllByActiveOrderByNameAsc(boolean active);

    List<Stream> findAllBySpecialtyIdAndStudyYearAndActiveOrderByNameAsc(UUID specialtyId, Integer studyYear, boolean active);

    List<Stream> findAllBySpecialtyIdAndStudyYearOrderByNameAsc(UUID specialtyId, Integer studyYear);

    boolean existsBySpecialtyId(UUID specialtyId);
}
