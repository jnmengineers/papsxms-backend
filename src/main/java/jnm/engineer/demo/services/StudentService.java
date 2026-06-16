package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ResultRepository resultRepository;
    private final ReportCardRepository reportCardRepository;

    public List<Student> getAllStudents(){
        return studentRepository.findAll();
    }

    public Student getById(Long id){
        return studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("student not found with id: " + id));
    }

    public List<Student> getByClassName(String className){
        return studentRepository.findByClassName(className);
    }

    public List<Student> GetByClass(Long classId){
        return studentRepository.findBySchoolClassClassId(classId);
    }

    public List<Student> searchByName(String name){
        return studentRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(name, name);
    }

    public Student create(Student student, Long classId){
        if (classId != null) {
            SchoolClass schoolClass = schoolClassRepository.findById(classId)
                    .orElseThrow(() -> new RuntimeException("Class not found with id: " + classId));
            student.setSchoolClass(schoolClass);
            student.setClassName(schoolClass.getClassName());
            student.setStream(schoolClass.getStream());
        }
        return studentRepository.save(student);
    }

    public Student update(Long id, Student updated){
        Student existing = getById(id);
        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setDateOfBirth(updated.getDateOfBirth());
        existing.setGender(updated.getGender());
        existing.setClassName(updated.getClassName());
        existing.setStream(updated.getStream());
        existing.setAdmissionNumber(updated.getAdmissionNumber());
        return studentRepository.save(existing);
    }

    public Student assignToClass(Long studentId, Long classId){
        Student student = getById(studentId);
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("class with id" + classId + "not found"));
        student.setSchoolClass(schoolClass);
        student.setClassName(schoolClass.getClassName());
        student.setStream(schoolClass.getStream());
        return studentRepository.save(student);
    }

    @Transactional
    public void delete(Long id){
        Student student = getById(id);

        // Delete report cards first
        List<ReportCard> reportCards = reportCardRepository.findByStudentStudentId(id);
        reportCardRepository.deleteAll(reportCards);

        // Delete results
        List<Result> results = resultRepository.findByStudentStudentId(id);
        resultRepository.deleteAll(results);

        // Now delete student
        studentRepository.deleteById(id);
    }
}