package jnm.engineer.demo.services;


import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.SubjectRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherServices {
    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;

    public List<Teacher> getAllTeachers(){
        return teacherRepository.findAll();
    }

    public Teacher getById(Long id){
        return teacherRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("the teacher does not exist"));
    }

    public Teacher getByEmail(String email){
        return teacherRepository.findByEmail(email)
                .orElseThrow(()-> new RuntimeException("email does not exist"));
    }

    public List<Teacher> searchByName(String name){
        return teacherRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(name, name);
    }

    public Teacher create (Teacher teacher){
        if (teacherRepository.existsByEmail(teacher.getEmail())){
            throw new RuntimeException("the teacher with this email already exists");
        }
        return teacherRepository.save(teacher);
    }

    public Teacher update(Long id, Teacher updated){
        Teacher existing = getById(id);
        existing.setFirstName(updated.getFirstName());
        existing.setLastName(updated.getLastName());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setSubjects(updated.getSubjects());
        return teacherRepository.save(existing);
    }

    public void delete(Long id) {
        Teacher teacher = getById(id);

        // Remove teacher from all subjects first
        List<Subject> subjects = subjectRepository.findByTeacherTeacherId(id);
        subjects.forEach(subject -> {
            subject.setTeacher(null);
            subjectRepository.save(subject);
        });

        // Remove teacher from all classes first
        List<SchoolClass> classes = schoolClassRepository.findByClassTeacherTeacherId(id);
        classes.forEach(cls -> {
            cls.setClassTeacher(null);
            schoolClassRepository.save(cls);
        });

        teacherRepository.deleteById(id);
    }
}
