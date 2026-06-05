package jnm.engineer.demo.services;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Teacher;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolClassService {

    private  final SchoolClassRepository schoolClassRepository;
    private final TeacherRepository teacherRepository;

    public List<SchoolClass> getAllSchoolClasses( ){
        return schoolClassRepository.findAll();
    }

    public SchoolClass getById(Long id){
        return schoolClassRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("the class is not found"));

    }

    public List<SchoolClass> getBySchoolClassName(String className){
        return schoolClassRepository.findByClassName(className);
    }

    public List<SchoolClass> getByClassTeacher(Long teacherId){
        return schoolClassRepository.findByClassTeacher(teacherId);
    }

    public SchoolClass create(SchoolClass schoolClass){
        return schoolClassRepository.save(schoolClass);
    }

    public SchoolClass update( Long id, SchoolClass updated){
        SchoolClass existing = getById(id);
        existing.setClassName(updated.getClassName());
        existing.setClassTeacher(updated.getClassTeacher());
        return schoolClassRepository.save(existing);
    }

    public SchoolClass assignClassTeacher(Long classId, Long teacherId){
        SchoolClass schoolClass = getById(classId);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(()-> new RuntimeException("teacher not found"));
        schoolClass.setClassTeacher(teacher);
        return schoolClassRepository.save(schoolClass);
    }


    public void delete(Long id){
        getById(id);
        schoolClassRepository.deleteById(id);
    }


}
