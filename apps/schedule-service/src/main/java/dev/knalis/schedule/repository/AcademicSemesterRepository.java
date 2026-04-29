package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.AcademicSemester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicSemesterRepository extends JpaRepository<AcademicSemester, UUID> {
    
    boolean existsByActiveTrue();
    
    boolean existsByActiveTrueAndIdNot(UUID id);
    
    Optional<AcademicSemester> findFirstByActiveTrueOrderByStartDateDesc();

    Optional<AcademicSemester> findFirstByNameOrderByStartDateDesc(String name);

    List<AcademicSemester> findAllByActiveTrue();

    List<AcademicSemester> findAllByOrderByStartDateDesc();
    
    @Query("""
            select semester from AcademicSemester semester
            where semester.startDate <= :dateTo
              and semester.endDate >= :dateFrom
            order by semester.startDate asc
            """)
    List<AcademicSemester> findAllOverlapping(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );
}
