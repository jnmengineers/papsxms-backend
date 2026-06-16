package jnm.engineer.demo.services;

import jnm.engineer.demo.models.ReportCard;
import jnm.engineer.demo.models.Result;
import jnm.engineer.demo.models.SchoolClass;
import jnm.engineer.demo.models.Student;
import jnm.engineer.demo.models.User;
import jnm.engineer.demo.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SchoolClassService {

    private final SchoolClassRepository schoolClassRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final ResultRepository resultRepository;
    private final ReportCardRepository reportCardRepository;
    private final UserRepository userRepository;
    private final ClassSubjectRepository classSubjectRepository;
    private final ExamScheduleRepository examScheduleRepository;

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
        // Auto set gradeLevel from className if missing
        if (schoolClass.getGradeLevel() == null || schoolClass.getGradeLevel().isEmpty()) {
            schoolClass.setGradeLevel(extractGradeFromClassName(schoolClass.getClassName()));
        }
        // Auto set section from gradeLevel if missing
        if (schoolClass.getSection() == null || schoolClass.getSection().isEmpty()) {
            schoolClass.setSection(extractSectionFromGrade(schoolClass.getGradeLevel()));
        }
        // Auto set meanTarget from section if missing
        if (schoolClass.getMeanTarget() == null) {
            schoolClass.setMeanTarget(extractTargetFromSection(schoolClass.getSection()));
        }
        return schoolClassRepository.save(schoolClass);
    }

    private String extractGradeFromClassName(String className) {
        if (className == null) return "";
        String name = className.trim().toUpperCase();
        if (name.startsWith("PP2")) return "PP2";
        if (name.startsWith("PP1")) return "PP1";
        if (name.startsWith("PG")) return "PG";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(G[1-9])([^0-9]|$)")
                .matcher(name);
        return m.find() ? m.group(1) : className;
    }

    private String extractSectionFromGrade(String grade) {
        if (grade == null) return "LOWER_PRIMARY";
        if (java.util.Arrays.asList("PG", "PP1", "PP2").contains(grade)) return "PRE_SCHOOL";
        if (java.util.Arrays.asList("G1", "G2", "G3").contains(grade)) return "LOWER_PRIMARY";
        if (java.util.Arrays.asList("G4", "G5", "G6").contains(grade)) return "UPPER_PRIMARY";
        if (java.util.Arrays.asList("G7", "G8", "G9").contains(grade)) return "JUNIOR_SCHOOL";
        return "LOWER_PRIMARY";
    }

    private Double extractTargetFromSection(String section) {
        if (section == null) return 80.0;
        if (section.equals("UPPER_PRIMARY")) return 70.0;
        if (section.equals("JUNIOR_SCHOOL")) return 65.0;
        return 80.0;
    }

    public SchoolClass update(Long id, SchoolClass updated) {
        SchoolClass existing = getById(id);

        String oldClassName = existing.getClassName();

        existing.setClassName(updated.getClassName());
        existing.setStream(updated.getStream());
        existing.setGradeLevel(updated.getGradeLevel());
        existing.setSection(updated.getSection());
        existing.setMeanTarget(updated.getMeanTarget());

        SchoolClass saved = schoolClassRepository.save(existing);

        // ✅ Cascade update — sync denormalized className/stream on all students in this class
        List<Student> students = studentRepository.findBySchoolClassClassId(id);
        students.forEach(student -> {
            student.setClassName(saved.getClassName());
            student.setStream(saved.getStream());
            studentRepository.save(student);
        });

        return saved;
    }

    public SchoolClass assignClassTeacher(Long classId, Long teacherId) {
        SchoolClass schoolClass = getById(classId);
        jnm.engineer.demo.models.Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));
        schoolClass.setClassTeacher(teacher);
        return schoolClassRepository.save(schoolClass);
    }

    // ✅ New — unassign the class teacher (sets classTeacher to null)
    public SchoolClass unassignClassTeacher(Long classId) {
        SchoolClass schoolClass = getById(classId);
        schoolClass.setClassTeacher(null);
        return schoolClassRepository.save(schoolClass);
    }

    @Transactional
    public void delete(Long id) {
        getById(id);

        // Step 1 — Remove linkedClass from any users
        List<User> linkedUsers = userRepository.findByLinkedClassClassId(id);
        linkedUsers.forEach(user -> {
            user.setLinkedClass(null);
            userRepository.save(user);
        });

        // Step 2 — Delete class subjects
        List<jnm.engineer.demo.models.ClassSubject> classSubjects =
                classSubjectRepository.findBySchoolClassClassId(id);
        classSubjectRepository.deleteAll(classSubjects);

        // Step 3 — Delete exam schedules
        List<jnm.engineer.demo.models.ExamSchedule> examSchedules =
                examScheduleRepository.findBySchoolClassClassId(id);
        examScheduleRepository.deleteAll(examSchedules);

        // Step 4 — Get all students in this class
        List<Student> students = studentRepository.findBySchoolClassClassId(id);

        // Step 5 — For each student delete results and report cards
        for (Student student : students) {
            Long studentId = student.getStudentId();
            List<ReportCard> reportCards = reportCardRepository.findByStudentStudentId(studentId);
            reportCardRepository.deleteAll(reportCards);
            List<Result> results = resultRepository.findByStudentStudentId(studentId);
            resultRepository.deleteAll(results);
        }

        // Step 6 — Remove class reference from all students
        students.forEach(student -> {
            student.setSchoolClass(null);
            studentRepository.save(student);
        });

        // Step 7 — Delete the class
        schoolClassRepository.deleteById(id);
    }
}