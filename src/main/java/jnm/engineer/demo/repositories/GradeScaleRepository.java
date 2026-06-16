package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.GradingScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GradeScaleRepository extends JpaRepository<GradingScale, Long> {
    @Query("SELECT g FROM GradingScale g WHERE :mark >= g.minMark AND :mark <= g.maxMark")
    Optional<GradingScale> findByMark(@Param("mark") Double mark);
}
