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

            // ✅ All RESULTS for this section (used for all mean calculations)
            List<Result> sectionResults = allResults.stream()
                    .filter(r -> {
                        if (r.getStudent() == null) return false;
                        if (r.getStudent().getSchoolClass() == null) return false;
                        String gradeLevel = r.getStudent().getSchoolClass().getGradeLevel();
                        return gradeLevel != null && sectionInfo.grades.contains(gradeLevel);
                    })
                    .collect(Collectors.toList());

            // Report cards still used for student counts / target counts
            List<ReportCard> sectionCards = allCards.stream()
                    .filter(card -> {
                        if (card.getStudent() == null) return false;
                        if (card.getStudent().getSchoolClass() == null) return false;
                        String gradeLevel = card.getStudent().getSchoolClass().getGradeLevel();
                        return gradeLevel != null && sectionInfo.grades.contains(gradeLevel);
                    })
                    .collect(Collectors.toList());

            if (!sectionResults.isEmpty()) {
                double sectionAvg = sectionCards.stream()
                        .mapToDouble(ReportCard::getAverageMarks)
                        .average()
                        .orElse(0.0);

                long distinctStudents = sectionResults.stream()
                        .map(r -> r.getStudent().getStudentId())
                        .distinct().count();

                sectionData.put("totalStudents", (int) distinctStudents);
                sectionData.put("sectionAverage", Math.round(sectionAvg * 100.0) / 100.0);
                sectionData.put("meetingTarget", sectionAvg >= sectionInfo.target);
                sectionData.put("aboveTarget", sectionCards.stream()
                        .filter(c -> c.getAverageMarks() >= sectionInfo.target).count());
                sectionData.put("belowTarget", sectionCards.stream()
                        .filter(c -> c.getAverageMarks() < sectionInfo.target).count());

                // ✅ Group RESULTS by GRADE LEVEL (combines all streams: G6B + G6Y → G6)
                Map<String, List<Result>> resultsByGrade = sectionResults.stream()
                        .collect(Collectors.groupingBy(
                                r -> r.getStudent().getSchoolClass().getGradeLevel()
                        ));

                List<Map<String, Object>> classBreakdown = new ArrayList<>();

                resultsByGrade.forEach((gradeLevel, gradeResults) -> {
                    Map<String, Object> classData = new LinkedHashMap<>();

                    // ✅ Subject means across the WHOLE grade (all streams combined)
                    Map<String, List<Result>> gradeBySubject = gradeResults.stream()
                            .filter(r -> r.getSubject() != null && r.getSubject().getSubjectName() != null)
                            .collect(Collectors.groupingBy(r -> r.getSubject().getSubjectName()));

                    List<Map<String, Object>> gradeSubjectPerformance = new ArrayList<>();
                    double gradeTotalMean = 0.0;
                    for (Map.Entry<String, List<Result>> entry : gradeBySubject.entrySet()) {
                        double subjectAvg = entry.getValue().stream()
                                .mapToDouble(Result::getMarksObtained)
                                .average().orElse(0.0);
                        Map<String, Object> subjectData = new LinkedHashMap<>();
                        subjectData.put("subjectName", entry.getKey());
                        subjectData.put("average", Math.round(subjectAvg * 100.0) / 100.0);
                        subjectData.put("meetingTarget", subjectAvg >= sectionInfo.target);
                        gradeSubjectPerformance.add(subjectData);
                        gradeTotalMean += subjectAvg;
                    }
                    gradeSubjectPerformance.sort((a, b) ->
                            Double.compare((Double) b.get("average"), (Double) a.get("average")));

                    int gradeSubjectCount = gradeBySubject.size();
                    double gradeMeanOfSubjects = gradeSubjectCount > 0 ? gradeTotalMean / gradeSubjectCount : 0.0;

                    long gradeStudents = gradeResults.stream()
                            .map(r -> r.getStudent().getStudentId())
                            .distinct().count();

                    classData.put("className", gradeLevel);
                    classData.put("totalStudents", (int) gradeStudents);
                    // ✅ classAverage = TOTAL MEAN of subjects (sum of subject means, no division)
                    classData.put("classAverage", Math.round(gradeTotalMean * 100.0) / 100.0);
                    classData.put("subjectCount", gradeSubjectCount);
                    classData.put("meetingTarget", gradeMeanOfSubjects >= sectionInfo.target);
                    classData.put("subjectPerformance", gradeSubjectPerformance);

                    // ✅ Sub-group by className + STREAM (fixes streams merging after DB rename)
                    Map<String, List<Result>> byStream = gradeResults.stream()
                            .collect(Collectors.groupingBy(r -> {
                                String gl = r.getStudent().getSchoolClass().getGradeLevel();
                                String st = r.getStudent().getSchoolClass().getStream();
                                return st != null ? gl + st.charAt(0) : gl;
                            }));

                    List<Map<String, Object>> streamBreakdown = new ArrayList<>();
                    byStream.forEach((streamLabel, streamResults) -> {
                        Map<String, Object> streamData = new LinkedHashMap<>();

                        Map<String, List<Result>> streamBySubject = streamResults.stream()
                                .filter(r -> r.getSubject() != null && r.getSubject().getSubjectName() != null)
                                .collect(Collectors.groupingBy(r -> r.getSubject().getSubjectName()));

                        List<Map<String, Object>> subjectPerformance = new ArrayList<>();
                        double streamTotalMean = 0.0;
                        for (Map.Entry<String, List<Result>> entry : streamBySubject.entrySet()) {
                            double subjectAvg = entry.getValue().stream()
                                    .mapToDouble(Result::getMarksObtained)
                                    .average().orElse(0.0);
                            Map<String, Object> subjectData = new LinkedHashMap<>();
                            subjectData.put("subjectName", entry.getKey());
                            subjectData.put("average", Math.round(subjectAvg * 100.0) / 100.0);
                            subjectData.put("meetingTarget", subjectAvg >= sectionInfo.target);
                            subjectPerformance.add(subjectData);
                            streamTotalMean += subjectAvg;
                        }
                        subjectPerformance.sort((a, b) ->
                                Double.compare((Double) b.get("average"), (Double) a.get("average")));

                        int streamSubjectCount = streamBySubject.size();
                        double streamMeanOfSubjects = streamSubjectCount > 0 ? streamTotalMean / streamSubjectCount : 0.0;

                        long streamStudents = streamResults.stream()
                                .map(r -> r.getStudent().getStudentId())
                                .distinct().count();

                        streamData.put("className", streamLabel); // e.g. "Grade 1 (Blue)"
                        streamData.put("totalStudents", (int) streamStudents);
                        // ✅ classAverage = TOTAL MEAN of subjects for this stream
                        streamData.put("classAverage", Math.round(streamTotalMean * 100.0) / 100.0);
                        streamData.put("subjectCount", streamSubjectCount);
                        streamData.put("meetingTarget", streamMeanOfSubjects >= sectionInfo.target);
                        streamData.put("subjectPerformance", subjectPerformance);
                        streamBreakdown.add(streamData);
                    });

                    // ✅ Sort streams by total mean DESCENDING
                    streamBreakdown.sort((a, b) ->
                            Double.compare((Double) b.get("classAverage"), (Double) a.get("classAverage")));
                    classData.put("streams", streamBreakdown);

                    classBreakdown.add(classData);
                });

                // ✅ Sort grades by TOTAL MEAN DESCENDING within the section
                classBreakdown.sort((a, b) ->
                        Double.compare((Double) b.get("classAverage"), (Double) a.get("classAverage")));

                sectionData.put("classBreakdown", classBreakdown);
            } else {
                sectionData.put("totalStudents", 0);
                sectionData.put("classBreakdown", new ArrayList<>());
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
