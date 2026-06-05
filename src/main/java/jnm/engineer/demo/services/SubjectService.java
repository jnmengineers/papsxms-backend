package jnm.engineer.demo.services;

import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.SubjectRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectService {
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;

    public List<Subject> getAllSubjects(){
        return subjectRepository.findAll();
    }

    public Subject getById(Long id){
        return subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("subject  not found with id: " + id));
    }

    public List<Subject> getByGradeLevel(String gradeLevel){
        return subjectRepository.findByGradeLevel(gradeLevel);
    }

    public List<Subject> getByTeacher(Long teacherId){
        return subjectRepository.findByTeacherTeacherId(teacherId);
    }

    public Subject create(Subject subject){
       if(subjectRepository.existsBySubjectCode(subject.getSubjectCode())){
           throw new RuntimeException("subject code " + subject.getSubjectCode() + "already exists.");
       }
       return subjectRepository.save(subject);
    }

    public Subject update(Long id, Subject updated){
        Subject existing = getById(id);
        existing.setSubjectName(updated.getSubjectName());
        existing.setSubjectCode(updated.getSubjectCode());
        existing.setGradeLevel(updated.getGradeLevel());
        existing.setTeacher(updated.getTeacher());

        return subjectRepository.save(existing);
    }

    public Subject assignTeacher(Long subjectId, Long teacherId){
        Subject subject = getById(subjectId);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(()-> new RuntimeException("Teacher not found with id: " + teacherId));
        subject.setTeacher(teacher);
        return subjectRepository.save(subject);
    }

    public void delete(Long id) {
        getById(id);
        subjectRepository.deleteById(id);
    }
}
