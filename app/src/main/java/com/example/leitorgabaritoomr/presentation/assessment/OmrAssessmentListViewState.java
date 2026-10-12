package com.example.leitorgabaritoomr.presentation.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Fotografia visual imutável das versões mais recentes das provas. */
public final class OmrAssessmentListViewState {

    public static final class AssessmentItem {
        private final String assessmentId;
        private final int version;
        private final String title;
        private final String subject;
        private final String gradeLevel;
        private final String academicPeriod;
        private final int schoolYear;
        private final String answerKeyId;
        private final int answerKeyVersion;

        private AssessmentItem(
                OmrAssessmentDefinition assessment
        ) {
            assessmentId = assessment.getAssessmentId();
            version = assessment.getVersion();
            title = assessment.getTitle();
            subject = assessment.getSubject();
            gradeLevel = assessment.getGradeLevel();
            academicPeriod = assessment.getAcademicPeriod();
            schoolYear = assessment.getSchoolYear();
            answerKeyId = assessment.getAnswerKeyId();
            answerKeyVersion = assessment.getAnswerKeyVersion();
        }

        public String getAssessmentId() {
            return assessmentId;
        }

        public int getVersion() {
            return version;
        }

        public String getTitle() {
            return title;
        }

        public String getSubject() {
            return subject;
        }

        public String getGradeLevel() {
            return gradeLevel;
        }

        public String getAcademicPeriod() {
            return academicPeriod;
        }

        public int getSchoolYear() {
            return schoolYear;
        }

        public String getAnswerKeyId() {
            return answerKeyId;
        }

        public int getAnswerKeyVersion() {
            return answerKeyVersion;
        }

        public boolean hasAssessmentId(String candidate) {
            return candidate != null
                    && assessmentId.equals(candidate.trim());
        }
    }

    private final String query;
    private final int totalCount;
    private final List<AssessmentItem> visibleAssessments;

    private OmrAssessmentListViewState(
            String query,
            int totalCount,
            List<AssessmentItem> visibleAssessments
    ) {
        this.query = query;
        this.totalCount = totalCount;
        this.visibleAssessments = Collections.unmodifiableList(
                new ArrayList<>(visibleAssessments)
        );
    }

    public static OmrAssessmentListViewState from(
            List<OmrAssessmentDefinition> latestAssessments,
            String query
    ) {
        if (latestAssessments == null) {
            throw new IllegalArgumentException(
                    "A lista de provas é obrigatória."
            );
        }

        String normalizedQuery = normalizeSearchText(query).trim();
        List<AssessmentItem> visible = new ArrayList<>();
        Set<String> assessmentIds = new HashSet<>();

        for (OmrAssessmentDefinition assessment
                : latestAssessments) {
            if (assessment == null) {
                throw new IllegalArgumentException(
                        "A lista de provas não pode conter valores nulos."
                );
            }

            if (!assessmentIds.add(assessment.getAssessmentId())) {
                throw new IllegalArgumentException(
                        "A lista deve conter apenas a versão mais recente"
                                + " de cada prova."
                );
            }

            if (matches(assessment, normalizedQuery)) {
                visible.add(new AssessmentItem(assessment));
            }
        }

        return new OmrAssessmentListViewState(
                normalizedQuery,
                latestAssessments.size(),
                visible
        );
    }

    private static boolean matches(
            OmrAssessmentDefinition assessment,
            String query
    ) {
        if (query.isEmpty()) {
            return true;
        }

        return normalizeSearchText(assessment.getTitle())
                .contains(query)
                || normalizeSearchText(assessment.getSubject())
                .contains(query)
                || normalizeSearchText(assessment.getGradeLevel())
                .contains(query)
                || normalizeSearchText(assessment.getAcademicPeriod())
                .contains(query)
                || normalizeSearchText(assessment.getAssessmentId())
                .contains(query)
                || normalizeSearchText(assessment.getAnswerKeyId())
                .contains(query)
                || Integer.toString(assessment.getSchoolYear())
                .contains(query);
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('\u00ba', ' ')
                .replace('\u00aa', ' ')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ");
    }

    public String getQuery() {
        return query;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getVisibleCount() {
        return visibleAssessments.size();
    }

    public List<AssessmentItem> getVisibleAssessments() {
        return visibleAssessments;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean isCatalogEmpty() {
        return totalCount == 0;
    }

    public boolean hasNoMatches() {
        return totalCount > 0 && visibleAssessments.isEmpty();
    }

    public AssessmentItem findVisibleAssessmentOrNull(
            String assessmentId
    ) {
        for (AssessmentItem assessment : visibleAssessments) {
            if (assessment.hasAssessmentId(assessmentId)) {
                return assessment;
            }
        }

        return null;
    }
}
