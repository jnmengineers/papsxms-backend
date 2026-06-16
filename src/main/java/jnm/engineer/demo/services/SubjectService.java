package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ClassSubject;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.ClassSubjectRepository;
import jnm.engineer.demo.repositories.ResultRepository;
import jnm.engineer.demo.repositories.SubjectRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final ResultRepository resultRepository;
    private final ClassSubjectRepository classSubjectRepository;

    public List<Subject> getAllSubjects(){
        return subjectRepository.findAll();
    }

    public Subject getById(Long id){
        return subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found with id: " + id));
    }

    public List<Subject> getByGradeLevel(String gradeLevel){
        return subjectRepository.findByGradeLevel(gradeLevel);
    }

    public List<Subject> getByTeacher(Long teacherId){
        return subjectRepository.findByTeacherTeacherId(teacherId);
    }

    public Subject create(Subject subject){
        return subjectRepository.save(subject);
    }

    public Subject update(Long id, Subject updated){
        Subject existing = getById(id);
        existing.setSubjectName(updated.getSubjectName());
        existing.setSubjectCode(updated.getSubjectCode());
        existing.setGradeLevel(updated.getGradeLevel());
        return subjectRepository.save(existing);
    }

    public Subject assignTeacher(Long subjectId, Long teacherId){
        Subject subject = getById(subjectId);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found with id: " + teacherId));
        subject.setTeacher(teacher);
        return subjectRepository.save(subject);
    }

    @Transactional
    public void delete(Long id) {
        // Step 1 — Delete results linked to this subject
        List<Result> results = resultRepository.findBySubjectSubjectId(id);
        resultRepository.deleteAll(results);

        // Step 2 — Delete class_subjects links
        List<ClassSubject> classSubjects = classSubjectRepository.findBySubjectSubjectId(id);
        classSubjectRepository.deleteAll(classSubjects);

        // Step 3 — Delete the subject
        subjectRepository.deleteById(id);
    }
}