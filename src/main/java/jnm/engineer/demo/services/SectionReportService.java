package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.repositories.ReportCardRepository;
import jnm.engineer.demo.repositories.ResultRepository;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionReportService {

    private final ReportCardRepository reportCardRepository;
    private final ResultRepository resultRepository;
    private final SchoolClassRepository schoolClassRepository;

    public Map<String, Object> getSectionReport(Long examId) {
        Map<String, Object> report = new LinkedHashMap<>();

        Map<String, SectionInfo> sections = new LinkedHashMap<>();
        sections.put("PRE_SCHOOL", new SectionInfo("Pre-School", List.of("PG", "PP1", "PP2"), 80.0));
        sections.put("LOWER_PRIMARY", new SectionInfo("Lower Primary", List.of("G1", "G2", "G3"), 80.0));
        sections.put("UPPER_PRIMARY", new SectionInfo("Upper Primary", List.of("G4", "G5", "G6"), 70.0));
        sections.put("JUNIOR_SCHOOL", new SectionInfo("Junior School", List.of("G7", "G8", "G9"), 65.0));

        List<ReportCard> allCards = reportCardRepository.findByExamExamId(examId);
        List<Result> allResults = resultRepository.findByExamExamId(examId);

        sections.forEach((key, sectionInfo) -> {
            Map<String, Object> sectionData = new LinkedHashMap<>();
            sectionData.put("sectionName", sectionInfo.name);
            sectionData.put("meanTarget", sectionInfo.target);
            sectionData.put("grades", sectionInfo.grades);

            // Null-safe filter — only cards whose student+schoolClass+gradeLevel is in this section
            List<ReportCard> sectionCards = allCards.stream()
                    .filter(card -> {
                        if (card.getStudent() == null) return false;
                        if (card.getStudent().getSchoolClass() == null) return false;
                        String gradeLevel = card.getStudent().getSchoolClass().getGradeLevel();
                        if (gradeLevel == null) return false;
                        return sectionInfo.grades.contains(gradeLevel);
                    })
                    .collect(Collectors.toList());

            if (!sectionCards.isEmpty()) {
                double sectionAvg = sectionCards.stream()
                        .mapToDouble(ReportCard::getAverageMarks)
                        .average()
                        .orElse(0.0);

                sectionData.put("totalStudents", sectionCards.size());
                sectionData.put("sectionAverage", Math.round(sectionAvg * 100.0) / 100.0);
                sectionData.put("meetingTarget", sectionAvg >= sectionInfo.target);
                sectionData.put("aboveTarget", sectionCards.stream()
                        .filter(c -> c.getAverageMarks() >= sectionInfo.target).count());
                sectionData.put("belowTarget", sectionCards.stream()
                        .filter(c -> c.getAverageMarks() < sectionInfo.target).count());

                // ✅ Group by GRADE LEVEL — the "parent class" (e.g. G1 covers G1R, G1B, G1Y)
                Map<String, List<ReportCard>> byGrade = sectionCards.stream()
                        .filter(card -> card.getStudent().getSchoolClass() != null
                                && card.getStudent().getSchoolClass().getGradeLevel() != null)
                        .collect(Collectors.groupingBy(
                                card -> card.getStudent().getSchoolClass().getGradeLevel()
                        ));

                List<Map<String, Object>> classBreakdown = new ArrayList<>();
                byGrade.forEach((gradeLevel, gradeCards) -> {
                    Map<String, Object> classData = new LinkedHashMap<>();
                    double gradeAvg = gradeCards.stream()
                            .mapToDouble(ReportCard::getAverageMarks)
                            .average()
                            .orElse(0.0);

                    // className here represents the PARENT grade (e.g. "G1")
                    classData.put("className", gradeLevel);
                    classData.put("totalStudents", gradeCards.size());
                    classData.put("classAverage", Math.round(gradeAvg * 100.0) / 100.0);
                    classData.put("meetingTarget", gradeAvg >= sectionInfo.target);
                    classData.put("topStudent", gradeCards.stream()
                            .max(Comparator.comparingDouble(ReportCard::getAverageMarks))
                            .map(c -> c.getStudent().getFirstName() + " " + c.getStudent().getLastName())
                            .orElse("N/A"));
                    classData.put("topStudentAvg", gradeCards.stream()
                            .mapToDouble(ReportCard::getAverageMarks)
                            .max().orElse(0.0));

                    // ✅ Sub-group by actual stream className (e.g. G1R, G1B, G1Y) under this grade
                    Map<String, List<ReportCard>> byStream = gradeCards.stream()
                            .filter(card -> card.getStudent().getSchoolClass() != null
                                    && card.getStudent().getSchoolClass().getClassName() != null)
                            .collect(Collectors.groupingBy(
                                    card -> card.getStudent().getSchoolClass().getClassName()
                            ));

                    List<Map<String, Object>> streamBreakdown = new ArrayList<>();
                    byStream.forEach((streamClassName, streamCards) -> {
                        Map<String, Object> streamData = new LinkedHashMap<>();
                        double streamAvg = streamCards.stream()
                                .mapToDouble(ReportCard::getAverageMarks)
                                .average().orElse(0.0);

                        streamData.put("className", streamClassName); // e.g. G1R
                        streamData.put("totalStudents", streamCards.size());
                        streamData.put("classAverage", Math.round(streamAvg * 100.0) / 100.0);
                        streamData.put("meetingTarget", streamAvg >= sectionInfo.target);
                        streamData.put("topStudent", streamCards.stream()
                                .max(Comparator.comparingDouble(ReportCard::getAverageMarks))
                                .map(c -> c.getStudent().getFirstName() + " " + c.getStudent().getLastName())
                                .orElse("N/A"));
                        streamData.put("topStudentAvg", streamCards.stream()
                                .mapToDouble(ReportCard::getAverageMarks)
                                .max().orElse(0.0));

                        // Subject performance for this specific stream
                        List<Result> streamResults = allResults.stream()
                                .filter(r -> r.getStudent() != null
                                        && r.getStudent().getSchoolClass() != null
                                        && streamClassName.equals(r.getStudent().getSchoolClass().getClassName()))
                                .collect(Collectors.toList());

                        Map<String, List<Result>> bySubject = streamResults.stream()
                                .filter(r -> r.getSubject() != null && r.getSubject().getSubjectName() != null)
                                .collect(Collectors.groupingBy(r -> r.getSubject().getSubjectName()));

                        List<Map<String, Object>> subjectPerformance = new ArrayList<>();
                        bySubject.forEach((subjectName, subjectResults) -> {
                            Map<String, Object> subjectData = new LinkedHashMap<>();
                            double subjectAvg = subjectResults.stream()
                                    .mapToDouble(Result::getMarksObtained)
                                    .average().orElse(0.0);
                            subjectData.put("subjectName", subjectName);
                            subjectData.put("average", Math.round(subjectAvg * 100.0) / 100.0);
                            subjectData.put("meetingTarget", subjectAvg >= sectionInfo.target);
                            subjectPerformance.add(subjectData);
                        });
                        subjectPerformance.sort((a, b) ->
                                Double.compare((Double) b.get("average"), (Double) a.get("average")));

                        streamData.put("subjectPerformance", subjectPerformance);
                        streamBreakdown.add(streamData);
                    });

                    // Sort streams alphabetically (G1B, G1R, G1Y)
                    streamBreakdown.sort((a, b) ->
                            ((String) a.get("className")).compareTo((String) b.get("className")));

                    classData.put("streams", streamBreakdown);

                    // ✅ Also aggregate subject performance across the WHOLE grade (all streams combined)
                    List<Result> gradeResults = allResults.stream()
                            .filter(r -> r.getStudent() != null
                                    && r.getStudent().getSchoolClass() != null
                                    && gradeLevel.equals(r.getStudent().getSchoolClass().getGradeLevel()))
                            .collect(Collectors.toList());

                    Map<String, List<Result>> gradeBySubject = gradeResults.stream()
                            .filter(r -> r.getSubject() != null && r.getSubject().getSubjectName() != null)
                            .collect(Collectors.groupingBy(r -> r.getSubject().getSubjectName()));

                    List<Map<String, Object>> gradeSubjectPerformance = new ArrayList<>();
                    gradeBySubject.forEach((subjectName, subjectResults) -> {
                        Map<String, Object> subjectData = new LinkedHashMap<>();
                        double subjectAvg = subjectResults.stream()
                                .mapToDouble(Result::getMarksObtained)
                                .average().orElse(0.0);
                        subjectData.put("subjectName", subjectName);
                        subjectData.put("average", Math.round(subjectAvg * 100.0) / 100.0);
                        subjectData.put("meetingTarget", subjectAvg >= sectionInfo.target);
                        gradeSubjectPerformance.add(subjectData);
                    });
                    gradeSubjectPerformance.sort((a, b) ->
                            Double.compare((Double) b.get("average"), (Double) a.get("average")));

                    classData.put("subjectPerformance", gradeSubjectPerformance);
                    classBreakdown.add(classData);
                });

                // Sort grades alphabetically (G1, G2, G3 ... or PG, PP1, PP2)
                classBreakdown.sort((a, b) ->
                        ((String) a.get("className")).compareTo((String) b.get("className")));

                sectionData.put("classBreakdown", classBreakdown);

                List<Map<String, Object>> topPerformers = sectionCards.stream()
                        .sorted((a, b) -> Double.compare(b.getAverageMarks(), a.getAverageMarks()))
                        .limit(5)
                        .map(card -> {
                            Map<String, Object> performer = new LinkedHashMap<>();
                            performer.put("name", card.getStudent().getFirstName() + " " + card.getStudent().getLastName());
                            performer.put("class", card.getStudent().getSchoolClass() != null
                                    ? card.getStudent().getSchoolClass().getClassName() : "N/A");
                            performer.put("average", card.getAverageMarks());
                            performer.put("rank", card.getClassRank());
                            return performer;
                        })
                        .collect(Collectors.toList());

                sectionData.put("topPerformers", topPerformers);
            } else {
                sectionData.put("totalStudents", 0);
                sectionData.put("classBreakdown", new ArrayList<>());
                sectionData.put("topPerformers", new ArrayList<>());
                sectionData.put("message", "No data available for this section");
            }

            report.put(key, sectionData);
        });

        return report;
    }

    static class SectionInfo {
        String name;
        List<String> grades;
        Double target;

        SectionInfo(String name, List<String> grades, Double target) {
            this.name = name;
            this.grades = grades;
            this.target = target;
        }
    }
}