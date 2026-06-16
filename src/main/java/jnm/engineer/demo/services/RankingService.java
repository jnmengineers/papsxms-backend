package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.repositories.ReportCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final ReportCardRepository reportCardRepository;

    @Transactional
    public void calculateRanksForExam(Long examId) {
        List<ReportCard> allCards = reportCardRepository.findByExamExamId(examId);

        if (allCards.isEmpty()) return;

        // ✅ Group by class — for classRank
        Map<Long, List<ReportCard>> byClass = allCards.stream()
                .filter(card -> card.getStudent() != null
                        && card.getStudent().getSchoolClass() != null
                        && card.getStudent().getSchoolClass().getClassId() != null)
                .collect(Collectors.groupingBy(
                        card -> card.getStudent().getSchoolClass().getClassId()
                ));

        // ✅ Group by grade level — for termRank (G1R + G1B + G1Y ranked together)
        Map<String, List<ReportCard>> byGrade = allCards.stream()
                .filter(card -> card.getStudent() != null
                        && card.getStudent().getSchoolClass() != null
                        && card.getStudent().getSchoolClass().getGradeLevel() != null)
                .collect(Collectors.groupingBy(
                        card -> card.getStudent().getSchoolClass().getGradeLevel()
                ));

        // Calculate class ranks
        byClass.forEach((classId, cards) -> assignRanks(cards, true));

        // Calculate grade ranks (termRank)
        byGrade.forEach((grade, cards) -> assignRanks(cards, false));

        // Save all
        reportCardRepository.saveAll(allCards);
    }

    private void assignRanks(List<ReportCard> cards, boolean isClassRank) {
        // Sort by average marks descending
        cards.sort((a, b) -> Double.compare(
                b.getAverageMarks() != null ? b.getAverageMarks() : 0,
                a.getAverageMarks() != null ? a.getAverageMarks() : 0
        ));

        int rank = 1;
        Double prevAvg = null;

        for (int i = 0; i < cards.size(); i++) {
            ReportCard card = cards.get(i);
            double avg = card.getAverageMarks() != null ? card.getAverageMarks() : 0;

            if (prevAvg != null && avg != prevAvg) {
                rank = i + 1;
            }

            if (isClassRank) {
                card.setClassRank(rank);
            } else {
                card.setTermRank(rank);
            }

            prevAvg = avg;
        }
    }
}