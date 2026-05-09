package dev.knalis.education.repository;

import dev.knalis.education.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID specialtyId);

    List<Specialty> findAllByOrderByCodeAsc();

    List<Specialty> findAllByActiveTrueOrderByCodeAsc();
}
