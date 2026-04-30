package dev.knalis.schedule.repository;

import dev.knalis.schedule.entity.TeacherDebt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TeacherDebtRepository extends JpaRepository<TeacherDebt, UUID> {
}
