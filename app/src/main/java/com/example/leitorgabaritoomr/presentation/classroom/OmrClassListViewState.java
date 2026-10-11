package com.example.leitorgabaritoomr.presentation.classroom;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fotografia imutavel da lista local de turmas. */
public final class OmrClassListViewState {

    public static final class ClassItem {
        private final String classGroupId;
        private final String name;
        private final String gradeLevel;
        private final int schoolYear;
        private final int studentCount;

        private ClassItem(
                OmrClassGroup classGroup,
                int studentCount
        ) {
            classGroupId = classGroup.getClassGroupId();
            name = classGroup.getName();
            gradeLevel = classGroup.getGradeLevel();
            schoolYear = classGroup.getSchoolYear();
            this.studentCount = studentCount;
        }

        public String getClassGroupId() {
            return classGroupId;
        }

        public String getName() {
            return name;
        }

        public String getGradeLevel() {
            return gradeLevel;
        }

        public int getSchoolYear() {
            return schoolYear;
        }

        public int getStudentCount() {
            return studentCount;
        }

        public boolean hasClassGroupId(String candidate) {
            return candidate != null
                    && classGroupId.equals(candidate.trim());
        }
    }

    private final String query;
    private final int totalCount;
    private final List<ClassItem> visibleClasses;

    private OmrClassListViewState(
            String query,
            int totalCount,
            List<ClassItem> visibleClasses
    ) {
        this.query = query;
        this.totalCount = totalCount;
        this.visibleClasses = Collections.unmodifiableList(
                new ArrayList<>(visibleClasses)
        );
    }

    public static OmrClassListViewState from(
            List<OmrClassGroup> classGroups,
            Map<String, Integer> studentCounts,
            String query
    ) {
        if (classGroups == null || studentCounts == null) {
            throw new IllegalArgumentException(
                    "As turmas e suas contagens sao obrigatorias."
            );
        }

        String normalizedQuery = normalizeSearchText(query).trim();
        List<ClassItem> visible = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        for (OmrClassGroup classGroup : classGroups) {
            if (classGroup == null) {
                throw new IllegalArgumentException(
                        "A lista de turmas nao pode conter valores nulos."
                );
            }
            if (!ids.add(classGroup.getClassGroupId())) {
                throw new IllegalArgumentException(
                        "A lista possui um identificador de turma repetido."
                );
            }

            Integer count = studentCounts.get(
                    classGroup.getClassGroupId()
            );
            if (count == null || count < 0) {
                throw new IllegalArgumentException(
                        "Toda turma deve possuir uma contagem valida."
                );
            }

            if (matches(classGroup, normalizedQuery)) {
                visible.add(new ClassItem(classGroup, count));
            }
        }

        return new OmrClassListViewState(
                normalizedQuery,
                classGroups.size(),
                visible
        );
    }

    private static boolean matches(
            OmrClassGroup classGroup,
            String query
    ) {
        if (query.isEmpty()) {
            return true;
        }

        return normalizeSearchText(classGroup.getName()).contains(query)
                || normalizeSearchText(classGroup.getGradeLevel())
                .contains(query)
                || Integer.toString(classGroup.getSchoolYear())
                .contains(query);
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public String getQuery() {
        return query;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getVisibleCount() {
        return visibleClasses.size();
    }

    public List<ClassItem> getVisibleClasses() {
        return visibleClasses;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean isCatalogEmpty() {
        return totalCount == 0;
    }

    public boolean hasNoMatches() {
        return totalCount > 0 && visibleClasses.isEmpty();
    }

    public ClassItem findVisibleClassOrNull(String classGroupId) {
        for (ClassItem item : visibleClasses) {
            if (item.hasClassGroupId(classGroupId)) {
                return item;
            }
        }
        return null;
    }
}
