package jnm.engineer.demo.repositories;

import jnm.engineer.demo.models.GradingScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeScaleRepository extends JpaRepository<GradingScale, Long> {

    // ✅ Fixed — returns List then take first, prevents NonUniqueResultException
    @Query("SELECT g FROM GradingScale g WHERE :mark >= g.minMark AND :mark <= g.maxMark ORDER BY g.minMark DESC")
    List<GradingScale> findScalesByMark(@Param("mark") Double mark);

    // Keep old method but delegate to list version safely
    default Optional<GradingScale> findByMark(Double mark) {
        List<GradingScale> results = findScalesByMark(mark);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}