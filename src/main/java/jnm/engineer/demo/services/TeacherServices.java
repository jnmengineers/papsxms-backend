package jnm.engineer.demo.services;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.SubjectRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherServices {

    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final SchoolClassRepository schoolClassRepository;

    public List<Teacher> getAllTeachers(){
        return teacherRepository.findAll();
    }

    public Teacher getById(Long id){
        return teacherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Teacher not found with id: " + id));
    }

    public List<Teacher> searchByName(String name){
        return teacherRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(name, name);
    }

    public Teacher create(Teacher teacher){
        return teacherRepository.save(teacher);
    }

    public Teacher update(Long id, Teacher updated){
        Teacher existing = getById(id);
        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        return teacherRepository.save(existing);
    }

    @Transactional
    public void delete(Long id){
        getById(id);

        // Remove teacher from all subjects
        List<Subject> subjects = subjectRepository.findByTeacherTeacherId(id);
        subjects.forEach(subject -> {
            subject.setTeacher(null);
            subjectRepository.save(subject);
        });

        // Remove teacher from all classes
        List<SchoolClass> classes = schoolClassRepository.findByClassTeacherTeacherId(id);
        classes.forEach(cls -> {
            cls.setClassTeacher(null);
            schoolClassRepository.save(cls);
        });

        teacherRepository.deleteById(id);
    }
}