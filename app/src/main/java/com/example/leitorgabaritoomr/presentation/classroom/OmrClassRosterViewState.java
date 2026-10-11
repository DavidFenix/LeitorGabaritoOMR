package com.example.leitorgabaritoomr.presentation.classroom;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fotografia visual imutavel da lista de chamada de uma turma. */
public final class OmrClassRosterViewState {

    public static final class EnrollmentItem {
        private final String studentId;
        private final String registration;
        private final String name;
        private final int rosterPosition;

        private EnrollmentItem(
                OmrClassEnrollment enrollment,
                OmrStudentProfile student
        ) {
            studentId = student.getStudentId();
            registration = student.getRegistration();
            name = student.getName();
            rosterPosition = enrollment.getRosterPosition();
        }

        public String getStudentId() {
            return studentId;
        }

        public String getRegistration() {
            return registration;
        }

        public String getName() {
            return name;
        }

        public int getRosterPosition() {
            return rosterPosition;
        }

        public boolean hasStudentId(String candidate) {
            return candidate != null
                    && studentId.equals(candidate.trim());
        }
    }

    private final String classGroupId;
    private final String className;
    private final String gradeLevel;
    private final int schoolYear;
    private final String query;
    private final int totalCount;
    private final List<EnrollmentItem> visibleEnrollments;

    private OmrClassRosterViewState(
            OmrClassGroup classGroup,
            String query,
            int totalCount,
            List<EnrollmentItem> visibleEnrollments
    ) {
        classGroupId = classGroup.getClassGroupId();
        className = classGroup.getName();
        gradeLevel = classGroup.getGradeLevel();
        schoolYear = classGroup.getSchoolYear();
        this.query = query;
        this.totalCount = totalCount;
        this.visibleEnrollments = Collections.unmodifiableList(
                new ArrayList<>(visibleEnrollments)
        );
    }

    public static OmrClassRosterViewState from(
            OmrClassGroup classGroup,
            List<OmrClassEnrollment> enrollments,
            List<OmrStudentProfile> students,
            String query
    ) {
        if (classGroup == null
                || enrollments == null
                || students == null) {
            throw new IllegalArgumentException(
                    "A turma, as matriculas e os alunos sao obrigatorios."
            );
        }

        Map<String, OmrStudentProfile> studentsById = new HashMap<>();
        for (OmrStudentProfile student : students) {
            if (student == null
                    || studentsById.put(
                    student.getStudentId(), student
            ) != null) {
                throw new IllegalArgumentException(
                        "O catalogo possui aluno nulo ou repetido."
                );
            }
        }

        String normalizedQuery = normalizeSearchText(query).trim();
        List<EnrollmentItem> visible = new ArrayList<>();
        Set<String> enrolledStudents = new HashSet<>();
        Set<Integer> positions = new HashSet<>();

        for (OmrClassEnrollment enrollment : enrollments) {
            if (enrollment == null
                    || !enrollment.belongsToClassGroup(
                    classGroup.getClassGroupId()
            )) {
                throw new IllegalArgumentException(
                        "A lista possui matricula nula ou de outra turma."
                );
            }
            if (!enrolledStudents.add(enrollment.getStudentId())
                    || !positions.add(enrollment.getRosterPosition())) {
                throw new IllegalArgumentException(
                        "A lista possui aluno ou posicao repetida."
                );
            }

            OmrStudentProfile student = studentsById.get(
                    enrollment.getStudentId()
            );
            if (student == null) {
                throw new IllegalArgumentException(
                        "Toda matricula deve possuir um aluno cadastrado."
                );
            }

            if (matches(student, enrollment, normalizedQuery)) {
                visible.add(new EnrollmentItem(enrollment, student));
            }
        }

        return new OmrClassRosterViewState(
                classGroup,
                normalizedQuery,
                enrollments.size(),
                visible
        );
    }

    private static boolean matches(
            OmrStudentProfile student,
            OmrClassEnrollment enrollment,
            String query
    ) {
        if (query.isEmpty()) {
            return true;
        }

        return normalizeSearchText(student.getRegistration()).contains(query)
                || normalizeSearchText(student.getName()).contains(query)
                || Integer.toString(enrollment.getRosterPosition())
                .equals(query);
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public String getClassGroupId() {
        return classGroupId;
    }

    public String getClassName() {
        return className;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public int getSchoolYear() {
        return schoolYear;
    }

    public String getQuery() {
        return query;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getVisibleCount() {
        return visibleEnrollments.size();
    }

    public List<EnrollmentItem> getVisibleEnrollments() {
        return visibleEnrollments;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean isRosterEmpty() {
        return totalCount == 0;
    }

    public boolean hasNoMatches() {
        return totalCount > 0 && visibleEnrollments.isEmpty();
    }

    public EnrollmentItem findVisibleEnrollmentOrNull(String studentId) {
        for (EnrollmentItem item : visibleEnrollments) {
            if (item.hasStudentId(studentId)) {
                return item;
            }
        }
        return null;
    }
}
