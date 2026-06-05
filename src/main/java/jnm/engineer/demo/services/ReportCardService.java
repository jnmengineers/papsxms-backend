package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.repositories.ReportCardRepository;
import jnm.engineer.demo.repositories.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportCardService {

    private final ReportCardRepository reportCardRepository;
    private final ResultRepository resultRepository;

    public List<ReportCard> getAllReportCards(){
        return reportCardRepository.findAll();
    }

    public ReportCard getById(Long id){
        return reportCardRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("the report card doesn't exist"));
    }

    public List<ReportCard> getByStudent(Long studentId){
       return reportCardRepository.findByStudentStudentId(studentId);
    }

    public List<ReportCard> getByExam(Long examId){
        return reportCardRepository.findByExamExamId(examId);
    }

    public ReportCard getByStudentAndExam(Long studentId, Long examId){
        return reportCardRepository.findByStudentStudentIdAndExamExamId(studentId, examId)
                .orElseThrow(()-> new RuntimeException("The report card does not exist"));
    }

    public ReportCard create(ReportCard reportCard){
        boolean exists = reportCardRepository.existsByStudentStudentIdAndExamExamId(
                reportCard.getStudent().getStudentId(),
                reportCard.getExam().getExamId());

        if (exists){
            throw new RuntimeException("Report card already exists for this student");
        }
        return reportCardRepository.save(reportCard);
    }
    //Autogenerate report card
    public ReportCard generateFromResults(Long studentId, Long examId){
        List<Result> results = resultRepository.findByStudentStudentIdAndExamExamId(studentId, examId);

        if (results.isEmpty()){
            throw new RuntimeException("No results found the student in this exam");
        }

        double total = results.stream().mapToDouble(Result::getMarksObtained).sum();
        double average = total/results.size();

        ReportCard reportCard = reportCardRepository
                .findByStudentStudentIdAndExamExamId(studentId, examId)
                .orElse(new ReportCard());
        reportCard.setStudent(results.get(0).getStudent());
        reportCard.setExam(results.get(0).getExam());
        reportCard.setTotalMarks(total);
        reportCard.setAverageMarks(average);

        return reportCardRepository.save(reportCard);
    }

    public ReportCard update(Long id, ReportCard updated){
        ReportCard existing = getById(id);
        existing.setTermRank(updated.getTermRank());
        existing.setClassRank(updated.getClassRank());
        existing.setTotalMarks(updated.getTotalMarks());
        existing.setAverageMarks(updated.getAverageMarks());
        existing.setTeacherComment(updated.getTeacherComment());
        existing.setPrincipalComment(updated.getPrincipalComment());
        return reportCardRepository.save(existing);
    }

    public void delete(Long id){
        getById(id);
        reportCardRepository.deleteById(id);
    }
}
