package com.ogjg.daitgym.routine.repository;

import com.ogjg.daitgym.domain.routine.Routine;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, String> {

    Optional<Slice<Routine>> findAll(Pageable pageable);

    Optional<Slice<Routine>> findAllByUserEmail(String email, Pageable pageable);

    Optional<Slice<Routine>> findByUserEmailIn(List<String> followerEmails, Pageable pageable);

}
