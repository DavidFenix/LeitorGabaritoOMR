package com.example.leitorgabaritoomr.presentation.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Fotografia visual das aplicações e do progresso de suas correções. */
public final class OmrAssessmentApplicationListViewState {

    public static final class ApplicationItem {
        private final String applicationId;
        private final String assessmentId;
        private final int assessmentVersion;
        private final String assessmentTitle;
        private final String subject;
        private final String classGroupId;
        private final String frozenClassName;
        private final int schoolYear;
        private final long createdAtEpochMillis;
        private final int participantCount;
        private final int correctedParticipantCount;

        private ApplicationItem(
                OmrAssessmentApplication application,
                OmrAssessmentDefinition assessment,
                String frozenClassName,
                int participantCount,
                int correctedParticipantCount
        ) {
            applicationId = application.getApplicationId();
            assessmentId = application.getAssessmentId();
            assessmentVersion = application.getAssessmentVersion();
            assessmentTitle = assessment.getTitle();
            subject = assessment.getSubject();
            classGroupId = application.getClassGroupId();
            this.frozenClassName = frozenClassName;
            schoolYear = assessment.getSchoolYear();
            createdAtEpochMillis = application.getCreatedAtEpochMillis();
            this.participantCount = participantCount;
            this.correctedParticipantCount = correctedParticipantCount;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public String getAssessmentId() {
            return assessmentId;
        }

        public int getAssessmentVersion() {
            return assessmentVersion;
        }

        public String getAssessmentTitle() {
            return assessmentTitle;
        }

        public String getSubject() {
            return subject;
        }

        public String getClassGroupId() {
            return classGroupId;
        }

        public String getFrozenClassName() {
            return frozenClassName;
        }

        public int getSchoolYear() {
            return schoolYear;
        }

        public long getCreatedAtEpochMillis() {
            return createdAtEpochMillis;
        }

        public int getParticipantCount() {
            return participantCount;
        }

        public int getCorrectedParticipantCount() {
            return correctedParticipantCount;
        }

        public int getPendingParticipantCount() {
            return participantCount - correctedParticipantCount;
        }

        public boolean isComplete() {
            return correctedParticipantCount == participantCount;
        }

        public boolean hasApplicationId(String candidate) {
            return candidate != null
                    && applicationId.equals(candidate.trim());
        }
    }

    private final String query;
    private final int totalCount;
    private final List<ApplicationItem> visibleApplications;

    private OmrAssessmentApplicationListViewState(
            String query,
            int totalCount,
            List<ApplicationItem> visibleApplications
    ) {
        this.query = query;
        this.totalCount = totalCount;
        this.visibleApplications = Collections.unmodifiableList(
                new ArrayList<>(visibleApplications)
        );
    }

    public static OmrAssessmentApplicationListViewState from(
            List<OmrAssessmentApplication> applications,
            List<OmrAssessmentDefinition> assessments,
            List<OmrAssessmentParticipant> participants,
            List<OmrAssessmentAttempt> attempts,
            String query
    ) {
        if (applications == null || assessments == null
                || participants == null || attempts == null) {
            throw new IllegalArgumentException(
                    "Os catálogos das aplicações são obrigatórios."
            );
        }

        Map<String, OmrAssessmentDefinition> assessmentByIdentity =
                indexAssessments(assessments);
        Map<String, OmrAssessmentApplication> applicationById =
                indexApplications(applications);
        Map<String, List<OmrAssessmentParticipant>> rosterByApplication =
                indexParticipants(participants, applicationById);
        Map<String, Set<String>> correctedStudentsByApplication =
                indexAttempts(attempts, applicationById, rosterByApplication);

        String normalizedQuery = normalizeSearchText(query).trim();
        List<ApplicationItem> visible = new ArrayList<>();

        for (OmrAssessmentApplication application : applications) {
            OmrAssessmentDefinition assessment = assessmentByIdentity.get(
                    assessmentIdentity(
                            application.getAssessmentId(),
                            application.getAssessmentVersion()
                    )
            );
            if (assessment == null) {
                throw new IllegalArgumentException(
                        "Toda aplicação deve referenciar uma versão de prova."
                );
            }

            List<OmrAssessmentParticipant> roster = rosterByApplication.get(
                    application.getApplicationId()
            );
            if (roster == null || roster.isEmpty()) {
                throw new IllegalArgumentException(
                        "Toda aplicação deve possuir uma chamada congelada."
                );
            }

            String frozenClassName = roster.get(0).getClassName();
            for (OmrAssessmentParticipant participant : roster) {
                if (!frozenClassName.equals(participant.getClassName())) {
                    throw new IllegalArgumentException(
                            "A chamada congelada possui mais de uma turma."
                    );
                }
            }

            Set<String> corrected = correctedStudentsByApplication.get(
                    application.getApplicationId()
            );
            int correctedCount = corrected == null ? 0 : corrected.size();
            ApplicationItem item = new ApplicationItem(
                    application,
                    assessment,
                    frozenClassName,
                    roster.size(),
                    correctedCount
            );
            if (matches(item, normalizedQuery)) {
                visible.add(item);
            }
        }

        return new OmrAssessmentApplicationListViewState(
                normalizedQuery,
                applications.size(),
                visible
        );
    }

    private static Map<String, OmrAssessmentDefinition> indexAssessments(
            List<OmrAssessmentDefinition> assessments
    ) {
        Map<String, OmrAssessmentDefinition> byIdentity =
                new LinkedHashMap<>();
        for (OmrAssessmentDefinition assessment : assessments) {
            if (assessment == null || byIdentity.put(
                    assessmentIdentity(
                            assessment.getAssessmentId(),
                            assessment.getVersion()
                    ),
                    assessment
            ) != null) {
                throw new IllegalArgumentException(
                        "O catálogo possui prova nula ou versão repetida."
                );
            }
        }
        return byIdentity;
    }

    private static Map<String, OmrAssessmentApplication> indexApplications(
            List<OmrAssessmentApplication> applications
    ) {
        Map<String, OmrAssessmentApplication> byId = new LinkedHashMap<>();
        for (OmrAssessmentApplication application : applications) {
            if (application == null || byId.put(
                    application.getApplicationId(), application
            ) != null) {
                throw new IllegalArgumentException(
                        "O catálogo possui aplicação nula ou repetida."
                );
            }
        }
        return byId;
    }

    private static Map<String, List<OmrAssessmentParticipant>>
    indexParticipants(
            List<OmrAssessmentParticipant> participants,
            Map<String, OmrAssessmentApplication> applicationById
    ) {
        Map<String, List<OmrAssessmentParticipant>> byApplication =
                new HashMap<>();
        Map<String, Set<String>> studentsByApplication = new HashMap<>();
        for (OmrAssessmentParticipant participant : participants) {
            if (participant == null || !applicationById.containsKey(
                    participant.getApplicationId()
            )) {
                throw new IllegalArgumentException(
                        "Todo participante deve pertencer ao catálogo."
                );
            }
            Set<String> students = studentsByApplication.computeIfAbsent(
                    participant.getApplicationId(),
                    ignored -> new HashSet<>()
            );
            if (!students.add(participant.getStudentId())) {
                throw new IllegalArgumentException(
                        "A chamada possui aluno repetido."
                );
            }
            byApplication.computeIfAbsent(
                    participant.getApplicationId(),
                    ignored -> new ArrayList<>()
            ).add(participant);
        }
        return byApplication;
    }

    private static Map<String, Set<String>> indexAttempts(
            List<OmrAssessmentAttempt> attempts,
            Map<String, OmrAssessmentApplication> applicationById,
            Map<String, List<OmrAssessmentParticipant>> rosterByApplication
    ) {
        Map<String, Set<String>> rosterStudents = new HashMap<>();
        for (Map.Entry<String, List<OmrAssessmentParticipant>> entry
                : rosterByApplication.entrySet()) {
            Set<String> ids = new HashSet<>();
            for (OmrAssessmentParticipant participant : entry.getValue()) {
                ids.add(participant.getStudentId());
            }
            rosterStudents.put(entry.getKey(), ids);
        }

        Set<String> attemptIds = new HashSet<>();
        Map<String, Set<String>> corrected = new HashMap<>();
        for (OmrAssessmentAttempt attempt : attempts) {
            if (attempt == null || !attemptIds.add(attempt.getAttemptId())
                    || !applicationById.containsKey(
                    attempt.getApplicationId()
            )) {
                throw new IllegalArgumentException(
                        "O histórico possui tentativa nula, repetida ou órfã."
                );
            }
            Set<String> students = rosterStudents.get(
                    attempt.getApplicationId()
            );
            if (students == null
                    || !students.contains(attempt.getStudentId())) {
                throw new IllegalArgumentException(
                        "Toda tentativa deve pertencer a um participante."
                );
            }
            corrected.computeIfAbsent(
                    attempt.getApplicationId(),
                    ignored -> new HashSet<>()
            ).add(attempt.getStudentId());
        }
        return corrected;
    }

    private static boolean matches(
            ApplicationItem item,
            String query
    ) {
        if (query.isEmpty()) {
            return true;
        }
        return normalizeSearchText(item.assessmentTitle).contains(query)
                || normalizeSearchText(item.subject).contains(query)
                || normalizeSearchText(item.frozenClassName).contains(query)
                || normalizeSearchText(item.applicationId).contains(query)
                || normalizeSearchText(item.assessmentId).contains(query)
                || normalizeSearchText(item.classGroupId).contains(query)
                || Integer.toString(item.schoolYear).contains(query)
                || Integer.toString(item.assessmentVersion).equals(query);
    }

    private static String assessmentIdentity(String id, int version) {
        return id + "\u0000" + version;
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
        return visibleApplications.size();
    }

    public List<ApplicationItem> getVisibleApplications() {
        return visibleApplications;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean isCatalogEmpty() {
        return totalCount == 0;
    }

    public boolean hasNoMatches() {
        return totalCount > 0 && visibleApplications.isEmpty();
    }

    public ApplicationItem findVisibleApplicationOrNull(
            String applicationId
    ) {
        for (ApplicationItem item : visibleApplications) {
            if (item.hasApplicationId(applicationId)) {
                return item;
            }
        }
        return null;
    }
}
