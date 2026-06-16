package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ClassSubject;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Subject;
import jnm.engineer.demo.repositories.ClassSubjectRepository;
import jnm.engineer.demo.repositories.SchoolClassRepository;
import jnm.engineer.demo.repositories.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassSubjectService {

    private final ClassSubjectRepository classSubjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;

    public List<ClassSubject> getSubjectsByClass(Long classId){
        return classSubjectRepository.findBySchoolClassClassId(classId);
    }

    public List<ClassSubject> getClassesBySubject(Long subjectId){
        return classSubjectRepository.findBySubjectSubjectId(subjectId);
    }

    public ClassSubject assignSubjectToClass(Long classId, Long subjectId){
        if (classSubjectRepository.existsBySchoolClassClassIdAndSubjectSubjectId(classId, subjectId)){
            throw new RuntimeException("Subject already assigned to this class");
        }
        SchoolClass schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        ClassSubject classSubject = new ClassSubject();
        classSubject.setSchoolClass(schoolClass);
        classSubject.setSubject(subject);
        return classSubjectRepository.save(classSubject);
    }

    @Transactional
    public void removeSubjectFromClass(Long classId, Long subjectId){
        classSubjectRepository.deleteBySchoolClassClassIdAndSubjectSubjectId(classId, subjectId);
    }

    public List<ClassSubject> getAllAssignments(){
        return classSubjectRepository.findAll();
    }
}