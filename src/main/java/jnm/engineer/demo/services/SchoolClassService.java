package jnm.engineer.demo.services;

import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolClassService {

    private final SchoolClassRepository schoolClassRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final UserService userService;
    private final SettingsService settingsService;

    public List<SchoolClass> getAllSchoolClasses() {
        return schoolClassRepository.findAll();
    }

    public SchoolClass getById(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Class not found with id: " + id));
    }

    public List<SchoolClass> getBySchoolClassName(String className) {
        return schoolClassRepository.findByClassNameContainingIgnoreCase(className);
    }

    public List<SchoolClass> getByClassTeacher(Long teacherId) {
        return schoolClassRepository.findByClassTeacherTeacherId(teacherId);
    }

    public SchoolClass create(SchoolClass schoolClass) {
        // Fill in what's missing from School Settings: grade from the class name,
        // section from the grade, mean target from the section.
        if (schoolClass.getGradeLevel() == null || schoolClass.getGradeLevel().isEmpty()) {
            String grade = settingsService.gradeFromClassName(schoolClass.getClassName());
            if (grade == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose the grade — \"" + schoolClass.getClassName() + "\" doesn't start with a known grade.");
            schoolClass.setGradeLevel(grade);
        }
        if (schoolClass.getSection() == null || schoolClass.getSection().isEmpty()) {
            String section = settingsService.sectionOfGrade(schoolClass.getGradeLevel());
            if (section == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Grade " + schoolClass.getGradeLevel() + " isn't set up in School Settings.");
            schoolClass.setSection(section);
        }
        if (schoolClass.getMeanTarget() == null) {
            schoolClass.setMeanTarget(settingsService.targetOfSection(schoolClass.getSection()));
        }
        return schoolClassRepository.save(schoolClass);
    }

    @Transactional
    public SchoolClass update(Long id, SchoolClass updated) {
        SchoolClass existing = getById(id);
        existing.setClassName(updated.getClassName());
        existing.setStream(updated.getStream());
        existing.setGradeLevel(updated.getGradeLevel());
        existing.setSection(updated.getSection());
        existing.setMeanTarget(updated.getMeanTarget());
        SchoolClass saved = schoolClassRepository.save(existing);

        // Keep the copies of className and stream on each student up to date
        List<Student> students = studentRepository.findBySchoolClassClassId(id);
        students.forEach(student -> {
            student.setClassName(saved.getClassName());
            student.setStream(saved.getStream());
            studentRepository.save(student);
        });

        return saved;
    }

    /** The class after assigning, plus the teacher's login details if a NEW login was created. */
    public record ClassTeacherResult(SchoolClass schoolClass, String username, String temporaryPassword) {}

    @Transactional
    public ClassTeacherResult assignClassTeacher(Long classId, Long teacherId) {
        SchoolClass schoolClass = getById(classId);
        jnm.engineer.demo.models.Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));
        schoolClass.setClassTeacher(teacher);
        SchoolClass saved = schoolClassRepository.save(schoolClass);

        // Auto-create or update the teacher's login, linked to this class.
        // A NEW login gets a random temporary password (no longer the phone number),
        // returned here so the admin can see it once and pass it on privately.
        UserService.TeacherLogin login = userService.createOrUpdateTeacherUser(teacher, saved);

        return new ClassTeacherResult(saved, login.user().getUsername(), login.temporaryPassword());
    }

    public SchoolClass unassignClassTeacher(Long classId) {
        SchoolClass schoolClass = getById(classId);
        schoolClass.setClassTeacher(null);
        return schoolClassRepository.save(schoolClass);
    }

    /**
     * Deletes an EMPTY class.
     * SAFETY: this used to delete every mark and report card of every learner in the class.
     * Now a class that still has learners is refused — move them to another class first.
     */
    @Transactional
    public void delete(Long id) {
        getById(id);
        if (!studentRepository.findBySchoolClassClassId(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This class still has learners. Move them to another class first.");
        }

        // Logins that pointed at this class no longer do
        List<User> linkedUsers = userRepository.findByLinkedClassClassId(id);
        linkedUsers.forEach(user -> {
            user.setLinkedClass(null);
            userRepository.save(user);
        });

        // The class's subject list and exam timetable go with it
        classSubjectRepository.deleteAll(classSubjectRepository.findBySchoolClassClassId(id));
        examScheduleRepository.deleteAll(examScheduleRepository.findBySchoolClassClassId(id));

        schoolClassRepository.deleteById(id);
    }
}
