package com.example.leitorgabaritoomr.presentation.student;

import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fotografia visual imutável do catálogo permanente de alunos.
 *
 * A ordem fornecida pelo repositório é preservada. A pesquisa ignora
 * maiúsculas, minúsculas e acentos, mas nunca converte matrículas para
 * números, mantendo zeros à esquerda significativos.
 */
public final class OmrStudentListViewState {

    public static final class StudentItem {
        private final String studentId;
        private final String registration;
        private final String name;

        private StudentItem(OmrStudentProfile student) {
            studentId = student.getStudentId();
            registration = student.getRegistration();
            name = student.getName();
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

        public boolean hasStudentId(String candidate) {
            return candidate != null
                    && studentId.equals(candidate.trim());
        }
    }

    private final String query;
    private final int totalCount;
    private final List<StudentItem> visibleStudents;

    private OmrStudentListViewState(
            String query,
            int totalCount,
            List<StudentItem> visibleStudents
    ) {
        this.query = query;
        this.totalCount = totalCount;
        this.visibleStudents = Collections.unmodifiableList(
                new ArrayList<>(visibleStudents)
        );
    }

    public static OmrStudentListViewState from(
            List<OmrStudentProfile> students,
            String query
    ) {
        if (students == null) {
            throw new IllegalArgumentException(
                    "A lista de alunos é obrigatória."
            );
        }

        String normalizedQuery = normalizeQuery(query);
        List<StudentItem> visible = new ArrayList<>();
        Set<String> studentIds = new HashSet<>();
        Set<String> registrations = new HashSet<>();

        for (OmrStudentProfile student : students) {
            if (student == null) {
                throw new IllegalArgumentException(
                        "A lista de alunos não pode conter valores nulos."
                );
            }

            if (!studentIds.add(student.getStudentId())) {
                throw new IllegalArgumentException(
                        "A lista possui um identificador de aluno repetido."
                );
            }

            if (!registrations.add(student.getRegistration())) {
                throw new IllegalArgumentException(
                        "A lista possui uma matrícula repetida."
                );
            }

            if (matches(student, normalizedQuery)) {
                visible.add(new StudentItem(student));
            }
        }

        return new OmrStudentListViewState(
                normalizedQuery,
                students.size(),
                visible
        );
    }

    private static boolean matches(
            OmrStudentProfile student,
            String normalizedQuery
    ) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        return normalizeSearchText(student.getRegistration())
                .contains(normalizedQuery)
                || normalizeSearchText(student.getName())
                .contains(normalizedQuery);
    }

    private static String normalizeQuery(String value) {
        return normalizeSearchText(value).trim();
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public String getQuery() {
        return query;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getVisibleCount() {
        return visibleStudents.size();
    }

    public List<StudentItem> getVisibleStudents() {
        return visibleStudents;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean isCatalogEmpty() {
        return totalCount == 0;
    }

    public boolean hasNoMatches() {
        return totalCount > 0 && visibleStudents.isEmpty();
    }

    public StudentItem findVisibleStudentOrNull(
            String studentId
    ) {
        for (StudentItem student : visibleStudents) {
            if (student.hasStudentId(studentId)) {
                return student;
            }
        }

        return null;
    }
}
