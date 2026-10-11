package com.example.leitorgabaritoomr.application.classroom;

import com.example.leitorgabaritoomr.application.student.OmrStudentRepository;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Cadastro manual e importação idempotente de turmas e listas escolares. */
public final class OmrClassRosterRegistrationService {

    private static final int MAX_ID_ATTEMPTS = 32;

    private final OmrClassGroupRepository classRepository;
    private final OmrClassEnrollmentRepository enrollmentRepository;
    private final OmrStudentRepository studentRepository;
    private final OmrClassRosterBatchRepository batchRepository;
    private final ClassGroupIdGenerator classIdGenerator;
    private final StudentIdGenerator studentIdGenerator;

    public OmrClassRosterRegistrationService(
            OmrClassGroupRepository classRepository,
            OmrClassEnrollmentRepository enrollmentRepository,
            OmrStudentRepository studentRepository,
            OmrClassRosterBatchRepository batchRepository
    ) {
        this(
                classRepository,
                enrollmentRepository,
                studentRepository,
                batchRepository,
                () -> "class-" + UUID.randomUUID(),
                () -> "student-" + UUID.randomUUID()
        );
    }

    OmrClassRosterRegistrationService(
            OmrClassGroupRepository classRepository,
            OmrClassEnrollmentRepository enrollmentRepository,
            OmrStudentRepository studentRepository,
            OmrClassRosterBatchRepository batchRepository,
            ClassGroupIdGenerator classIdGenerator,
            StudentIdGenerator studentIdGenerator
    ) {
        if (classRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório de turmas é obrigatório."
            );
        }
        if (enrollmentRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório de matrículas é obrigatório."
            );
        }
        if (studentRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório de alunos é obrigatório."
            );
        }
        if (batchRepository == null) {
            throw new IllegalArgumentException(
                    "O repositório transacional é obrigatório."
            );
        }
        if (classIdGenerator == null || studentIdGenerator == null) {
            throw new IllegalArgumentException(
                    "Os geradores de identificadores são obrigatórios."
            );
        }
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
        this.classIdGenerator = classIdGenerator;
        this.studentIdGenerator = studentIdGenerator;
    }

    public OmrClassGroup registerOrUpdateClass(
            String name,
            String gradeLevel,
            int schoolYear
    ) {
        return registerOrUpdateClass(
                null, name, gradeLevel, schoolYear
        );
    }

    /** Cria pela chave natural ou edita a turma indicada sem trocar seu ID. */
    public OmrClassGroup registerOrUpdateClass(
            String existingClassGroupId,
            String name,
            String gradeLevel,
            int schoolYear
    ) {
        String normalizedName = requiredWords(
                name, "O nome da turma é obrigatório."
        );
        String normalizedGrade = requiredWords(
                gradeLevel, "A etapa da turma é obrigatória."
        );
        requireSchoolYear(schoolYear);

        ClassIndex classes = indexClasses(classRepository.loadAll());
        String naturalKey = classKey(normalizedName, schoolYear);
        OmrClassGroup naturalMatch = classes.byNaturalKey.get(naturalKey);
        String existingId = text(existingClassGroupId);
        String id;

        if (existingId == null) {
            id = naturalMatch == null
                    ? nextClassId(classes.ids)
                    : naturalMatch.getClassGroupId();
        } else {
            if (!classes.byId.containsKey(existingId)) {
                throw new IllegalArgumentException(
                        "A turma que seria editada não foi encontrada."
                );
            }
            if (naturalMatch != null
                    && !naturalMatch.getClassGroupId().equals(existingId)) {
                throw new IllegalArgumentException(
                        "Já existe outra turma com esse nome no ano letivo."
                );
            }
            id = existingId;
        }

        OmrClassGroup result = new OmrClassGroup(
                id, normalizedName, normalizedGrade, schoolYear
        );
        classRepository.save(result);
        return result;
    }

    /** Matricula um aluno existente com posição livre na turma. */
    public OmrClassEnrollment enrollStudent(
            String classGroupId,
            String studentId,
            int rosterPosition
    ) {
        String classId = requiredText(
                classGroupId, "A turma é obrigatória."
        );
        String normalizedStudentId = requiredText(
                studentId, "O aluno é obrigatório."
        );
        if (rosterPosition <= 0) {
            throw new IllegalArgumentException(
                    "A posição na chamada deve ser positiva."
            );
        }

        OmrClassGroup targetClass = classRepository.findByIdOrNull(classId);
        if (targetClass == null) {
            throw new IllegalArgumentException(
                    "A turma informada não está cadastrada."
            );
        }
        if (studentRepository.findByIdOrNull(normalizedStudentId) == null) {
            throw new IllegalArgumentException(
                    "O aluno informado não está cadastrado."
            );
        }

        for (OmrClassEnrollment current
                : enrollmentRepository.loadForClassGroup(classId)) {
            if (current.getRosterPosition() == rosterPosition
                    && !current.getStudentId().equals(normalizedStudentId)) {
                throw new IllegalArgumentException(
                        "A posição já está ocupada por outro aluno."
                );
            }
        }

        for (OmrClassEnrollment current
                : enrollmentRepository.loadForStudent(normalizedStudentId)) {
            if (current.getClassGroupId().equals(classId)) {
                continue;
            }
            OmrClassGroup otherClass = classRepository.findByIdOrNull(
                    current.getClassGroupId()
            );
            if (otherClass == null) {
                throw new IllegalStateException(
                        "Há uma matrícula ligada a uma turma inexistente."
                );
            }
            if (otherClass.getSchoolYear() == targetClass.getSchoolYear()) {
                throw new IllegalArgumentException(
                        "O aluno já pertence a outra turma no mesmo ano letivo."
                );
            }
        }

        OmrClassEnrollment result = new OmrClassEnrollment(
                classId, normalizedStudentId, rosterPosition
        );
        enrollmentRepository.save(result);
        return result;
    }

    public ImportSummary importPreview(
            OmrClassRosterCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A pré-visualização da lista escolar é obrigatória."
            );
        }
        if (!preview.isReadyToImport()) {
            throw new IllegalArgumentException(
                    "O CSV deve estar sem erros antes da importação."
            );
        }

        ClassIndex existingClasses = indexClasses(classRepository.loadAll());
        StudentIndex existingStudents = indexStudents(
                studentRepository.loadAll()
        );
        LinkedHashMap<String, OmrClassGroup> classes =
                new LinkedHashMap<>();
        LinkedHashMap<String, OmrStudentProfile> students =
                new LinkedHashMap<>();
        List<OmrClassEnrollment> enrollments = new ArrayList<>();
        Set<String> enrollmentKeys = new HashSet<>();
        Set<String> positionKeys = new HashSet<>();
        Map<String, String> classByStudentYear = new LinkedHashMap<>();
        int createdClasses = 0;
        int updatedClasses = 0;
        int createdStudents = 0;
        int updatedStudents = 0;

        for (OmrClassRosterCsvParser.Row row : preview.getRows()) {
            String className = requiredWords(
                    row.getClassName(), "O nome da turma é obrigatório."
            );
            String grade = requiredWords(
                    row.getGradeLevel(), "A etapa da turma é obrigatória."
            );
            requireSchoolYear(row.getSchoolYear());
            String registration = requiredText(
                    row.getRegistration(),
                    "A matrícula do aluno é obrigatória."
            ).toUpperCase(Locale.ROOT);
            String studentName = requiredWords(
                    row.getStudentName(), "O nome do aluno é obrigatório."
            );
            if (row.getRosterPosition() <= 0) {
                throw new IllegalArgumentException(
                        "A posição na chamada deve ser positiva."
                );
            }

            String naturalKey = classKey(className, row.getSchoolYear());
            OmrClassGroup classGroup = classes.get(naturalKey);
            if (classGroup == null) {
                OmrClassGroup existing = existingClasses.byNaturalKey.get(
                        naturalKey
                );
                String classId;
                if (existing == null) {
                    classId = nextClassId(existingClasses.ids);
                    existingClasses.ids.add(classId);
                    createdClasses++;
                } else {
                    classId = existing.getClassGroupId();
                    updatedClasses++;
                }
                classGroup = new OmrClassGroup(
                        classId,
                        className,
                        grade,
                        row.getSchoolYear()
                );
                classes.put(naturalKey, classGroup);
            } else if (!identity(classGroup.getGradeLevel()).equals(
                    identity(grade)
            )) {
                throw new IllegalArgumentException(
                        "A mesma turma possui etapas diferentes no lote."
                );
            }

            OmrStudentProfile student = students.get(registration);
            if (student == null) {
                OmrStudentProfile existing =
                        existingStudents.byRegistration.get(registration);
                String resolvedStudentId;
                if (existing == null) {
                    resolvedStudentId = nextStudentId(existingStudents.ids);
                    existingStudents.ids.add(resolvedStudentId);
                    createdStudents++;
                } else {
                    resolvedStudentId = existing.getStudentId();
                    updatedStudents++;
                }
                student = new OmrStudentProfile(
                        resolvedStudentId,
                        registration,
                        studentName
                );
                students.put(registration, student);
            } else if (!identity(student.getName()).equals(
                    identity(studentName)
            )) {
                throw new IllegalArgumentException(
                        "A mesma matrícula possui nomes diferentes no lote."
                );
            }

            String enrollmentKey = classGroup.getClassGroupId()
                    + "\u0000" + student.getStudentId();
            String positionKey = classGroup.getClassGroupId()
                    + "\u0000" + row.getRosterPosition();
            if (!enrollmentKeys.add(enrollmentKey)) {
                throw new IllegalArgumentException(
                        "O aluno está repetido na turma importada."
                );
            }
            if (!positionKeys.add(positionKey)) {
                throw new IllegalArgumentException(
                        "A posição está repetida na turma importada."
                );
            }
            String studentYearKey = student.getStudentId()
                    + "\u0000" + row.getSchoolYear();
            String earlierClass = classByStudentYear.putIfAbsent(
                    studentYearKey,
                    classGroup.getClassGroupId()
            );
            if (earlierClass != null
                    && !earlierClass.equals(classGroup.getClassGroupId())) {
                throw new IllegalArgumentException(
                        "O aluno aparece em mais de uma turma no mesmo ano letivo."
                );
            }
            enrollments.add(new OmrClassEnrollment(
                    classGroup.getClassGroupId(),
                    student.getStudentId(),
                    row.getRosterPosition()
            ));
        }

        List<OmrClassGroup> classList = new ArrayList<>(classes.values());
        List<OmrStudentProfile> studentList =
                new ArrayList<>(students.values());
        batchRepository.replaceRosters(
                classList,
                studentList,
                enrollments
        );
        return new ImportSummary(
                createdClasses,
                updatedClasses,
                createdStudents,
                updatedStudents,
                classList,
                studentList,
                enrollments
        );
    }

    private ClassIndex indexClasses(List<OmrClassGroup> values) {
        if (values == null) {
            throw new IllegalStateException(
                    "O repositório retornou uma lista de turmas inválida."
            );
        }
        ClassIndex result = new ClassIndex();
        for (OmrClassGroup value : values) {
            if (value == null
                    || result.byId.putIfAbsent(
                    value.getClassGroupId(), value
            ) != null) {
                throw new IllegalStateException(
                        "Há identificadores de turma duplicados no catálogo."
                );
            }
            String naturalKey = classKey(
                    value.getName(), value.getSchoolYear()
            );
            if (result.byNaturalKey.putIfAbsent(
                    naturalKey, value
            ) != null) {
                throw new IllegalStateException(
                        "Há turmas duplicadas por nome e ano letivo."
                );
            }
            result.ids.add(value.getClassGroupId());
        }
        return result;
    }

    private StudentIndex indexStudents(List<OmrStudentProfile> values) {
        if (values == null) {
            throw new IllegalStateException(
                    "O repositório retornou uma lista de alunos inválida."
            );
        }
        StudentIndex result = new StudentIndex();
        for (OmrStudentProfile value : values) {
            if (value == null || !result.ids.add(value.getStudentId())) {
                throw new IllegalStateException(
                        "Há identificadores de aluno duplicados no catálogo."
                );
            }
            if (result.byRegistration.putIfAbsent(
                    value.getRegistration(), value
            ) != null) {
                throw new IllegalStateException(
                        "Há matrículas de aluno duplicadas no catálogo."
                );
            }
        }
        return result;
    }

    private String nextClassId(Set<String> reserved) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String candidate = text(classIdGenerator.createClassGroupId());
            if (candidate != null && !reserved.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Não foi possível gerar um identificador único para a turma."
        );
    }

    private String nextStudentId(Set<String> reserved) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String candidate = text(studentIdGenerator.createStudentId());
            if (candidate != null && !reserved.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Não foi possível gerar um identificador único para o aluno."
        );
    }

    private static String classKey(String name, int year) {
        return OmrClassRosterCsvParser.classKey(name, year);
    }

    private static String requiredWords(String value, String message) {
        return requiredText(value, message).replaceAll("\\s+", " ");
    }

    private static String requiredText(String value, String message) {
        String normalized = text(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }

    private static String identity(String value) {
        return Normalizer.normalize(
                value.trim().replaceAll("\\s+", " "),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static void requireSchoolYear(int year) {
        if (year <= 0) {
            throw new IllegalArgumentException(
                    "O ano letivo deve ser positivo."
            );
        }
    }

    interface ClassGroupIdGenerator {
        String createClassGroupId();
    }

    interface StudentIdGenerator {
        String createStudentId();
    }

    public static final class ImportSummary {
        private final int createdClassCount;
        private final int updatedClassCount;
        private final int createdStudentCount;
        private final int updatedStudentCount;
        private final List<OmrClassGroup> classGroups;
        private final List<OmrStudentProfile> students;
        private final List<OmrClassEnrollment> enrollments;

        private ImportSummary(
                int createdClassCount,
                int updatedClassCount,
                int createdStudentCount,
                int updatedStudentCount,
                List<OmrClassGroup> classGroups,
                List<OmrStudentProfile> students,
                List<OmrClassEnrollment> enrollments
        ) {
            this.createdClassCount = createdClassCount;
            this.updatedClassCount = updatedClassCount;
            this.createdStudentCount = createdStudentCount;
            this.updatedStudentCount = updatedStudentCount;
            this.classGroups = immutable(classGroups);
            this.students = immutable(students);
            this.enrollments = immutable(enrollments);
        }

        private static <T> List<T> immutable(List<T> values) {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }

        public int getCreatedClassCount() { return createdClassCount; }
        public int getUpdatedClassCount() { return updatedClassCount; }
        public int getProcessedClassCount() { return classGroups.size(); }
        public int getCreatedStudentCount() { return createdStudentCount; }
        public int getUpdatedStudentCount() { return updatedStudentCount; }
        public int getProcessedStudentCount() { return students.size(); }
        public int getEnrollmentCount() { return enrollments.size(); }
        public List<OmrClassGroup> getClassGroups() { return classGroups; }
        public List<OmrStudentProfile> getStudents() { return students; }
        public List<OmrClassEnrollment> getEnrollments() {
            return enrollments;
        }
    }

    private static final class ClassIndex {
        private final Map<String, OmrClassGroup> byNaturalKey =
                new LinkedHashMap<>();
        private final Map<String, OmrClassGroup> byId =
                new LinkedHashMap<>();
        private final Set<String> ids = new HashSet<>();
    }

    private static final class StudentIndex {
        private final Map<String, OmrStudentProfile> byRegistration =
                new LinkedHashMap<>();
        private final Set<String> ids = new HashSet<>();
    }
}
