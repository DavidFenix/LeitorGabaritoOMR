package com.example.leitorgabaritoomr.presentation.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Estado somente de leitura da chamada congelada de uma aplicação. */
public final class OmrAssessmentApplicationRosterViewState {

    public static final class ParticipantItem {
        private final String studentId;
        private final String registration;
        private final String studentName;
        private final int rosterPosition;
        private final int latestRevision;
        private final String latestHistoryRecordId;
        private final long latestRecordedAtEpochMillis;

        private ParticipantItem(
                OmrAssessmentParticipant participant,
                OmrAssessmentAttempt latestAttempt
        ) {
            studentId = participant.getStudentId();
            registration = participant.getRegistration();
            studentName = participant.getStudentName();
            rosterPosition = participant.getRosterPosition();
            latestRevision = latestAttempt == null
                    ? 0 : latestAttempt.getRevision();
            latestHistoryRecordId = latestAttempt == null
                    ? null : latestAttempt.getHistoryRecordId();
            latestRecordedAtEpochMillis = latestAttempt == null
                    ? 0L : latestAttempt.getRecordedAtEpochMillis();
        }

        public String getStudentId() {
            return studentId;
        }

        public String getRegistration() {
            return registration;
        }

        public String getStudentName() {
            return studentName;
        }

        public int getRosterPosition() {
            return rosterPosition;
        }

        public boolean isCorrected() {
            return latestRevision > 0;
        }

        public int getLatestRevision() {
            return latestRevision;
        }

        public String getLatestHistoryRecordId() {
            return latestHistoryRecordId;
        }

        public long getLatestRecordedAtEpochMillis() {
            return latestRecordedAtEpochMillis;
        }

        public boolean hasStudentId(String candidate) {
            return candidate != null && studentId.equals(candidate.trim());
        }
    }

    private final String applicationId;
    private final String assessmentId;
    private final int assessmentVersion;
    private final String assessmentTitle;
    private final String subject;
    private final String frozenClassName;
    private final long createdAtEpochMillis;
    private final String query;
    private final int totalCount;
    private final int correctedCount;
    private final String firstPendingStudentId;
    private final List<ParticipantItem> visibleParticipants;

    private OmrAssessmentApplicationRosterViewState(
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            String frozenClassName,
            String query,
            int totalCount,
            int correctedCount,
            String firstPendingStudentId,
            List<ParticipantItem> visibleParticipants
    ) {
        applicationId = application.getApplicationId();
        assessmentId = application.getAssessmentId();
        assessmentVersion = application.getAssessmentVersion();
        assessmentTitle = assessment.getTitle();
        subject = assessment.getSubject();
        this.frozenClassName = frozenClassName;
        createdAtEpochMillis = application.getCreatedAtEpochMillis();
        this.query = query;
        this.totalCount = totalCount;
        this.correctedCount = correctedCount;
        this.firstPendingStudentId = firstPendingStudentId;
        this.visibleParticipants = Collections.unmodifiableList(
                new ArrayList<>(visibleParticipants)
        );
    }

    public static OmrAssessmentApplicationRosterViewState from(
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            List<OmrAssessmentParticipant> participants,
            List<OmrAssessmentAttempt> attempts,
            String query
    ) {
        if (application == null || assessment == null
                || participants == null || attempts == null) {
            throw new IllegalArgumentException(
                    "A aplicação, a prova e seus históricos são obrigatórios."
            );
        }
        if (!application.usesAssessment(
                assessment.getAssessmentId(), assessment.getVersion()
        )) {
            throw new IllegalArgumentException(
                    "A prova não corresponde à aplicação."
            );
        }
        if (participants.isEmpty()) {
            throw new IllegalArgumentException(
                    "A aplicação deve possuir uma chamada congelada."
            );
        }

        List<OmrAssessmentParticipant> ordered = new ArrayList<>();
        Set<String> studentIds = new HashSet<>();
        Set<String> registrations = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        String frozenClassName = null;

        for (OmrAssessmentParticipant participant : participants) {
            if (participant == null || !participant.belongsToApplication(
                    application.getApplicationId()
            )) {
                throw new IllegalArgumentException(
                        "A chamada possui participante nulo ou de outra aplicação."
                );
            }
            if (!studentIds.add(participant.getStudentId())
                    || !registrations.add(participant.getRegistration()
                    .toUpperCase(Locale.ROOT))
                    || !positions.add(participant.getRosterPosition())) {
                throw new IllegalArgumentException(
                        "A chamada possui identidade, matrícula ou posição repetida."
                );
            }
            if (frozenClassName == null) {
                frozenClassName = participant.getClassName();
            } else if (!frozenClassName.equals(participant.getClassName())) {
                throw new IllegalArgumentException(
                        "A chamada congelada possui mais de uma turma."
                );
            }
            ordered.add(participant);
        }
        ordered.sort(Comparator
                .comparingInt(OmrAssessmentParticipant::getRosterPosition)
                .thenComparing(OmrAssessmentParticipant::getStudentId));

        Map<String, OmrAssessmentAttempt> latestByStudent =
                latestAttempts(application, studentIds, attempts);
        String normalizedQuery = normalizeSearchText(query).trim();
        List<ParticipantItem> visible = new ArrayList<>();
        String firstPendingStudentId = null;

        for (OmrAssessmentParticipant participant : ordered) {
            ParticipantItem item = new ParticipantItem(
                    participant,
                    latestByStudent.get(participant.getStudentId())
            );
            if (!item.isCorrected() && firstPendingStudentId == null) {
                firstPendingStudentId = item.getStudentId();
            }
            if (matches(item, normalizedQuery)) {
                visible.add(item);
            }
        }

        return new OmrAssessmentApplicationRosterViewState(
                application,
                assessment,
                frozenClassName,
                normalizedQuery,
                ordered.size(),
                latestByStudent.size(),
                firstPendingStudentId,
                visible
        );
    }

    private static Map<String, OmrAssessmentAttempt> latestAttempts(
            OmrAssessmentApplication application,
            Set<String> studentIds,
            List<OmrAssessmentAttempt> attempts
    ) {
        Set<String> attemptIds = new HashSet<>();
        Map<String, Set<Integer>> revisionsByStudent = new HashMap<>();
        Map<String, OmrAssessmentAttempt> latest = new HashMap<>();

        for (OmrAssessmentAttempt attempt : attempts) {
            if (attempt == null || !attemptIds.add(attempt.getAttemptId())
                    || !attempt.belongsToApplication(
                    application.getApplicationId()
            ) || !studentIds.contains(attempt.getStudentId())) {
                throw new IllegalArgumentException(
                        "O histórico possui tentativa nula, repetida ou órfã."
                );
            }
            Set<Integer> revisions = revisionsByStudent.computeIfAbsent(
                    attempt.getStudentId(), ignored -> new HashSet<>()
            );
            if (!revisions.add(attempt.getRevision())) {
                throw new IllegalArgumentException(
                        "O histórico possui revisão repetida para um aluno."
                );
            }

            OmrAssessmentAttempt current = latest.get(attempt.getStudentId());
            if (current == null
                    || attempt.getRevision() > current.getRevision()) {
                latest.put(attempt.getStudentId(), attempt);
            }
        }
        return latest;
    }

    private static boolean matches(ParticipantItem item, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String status = item.isCorrected() ? "corrigido" : "pendente";
        String revision = item.isCorrected()
                ? "revisao " + item.latestRevision : "";
        return normalizeSearchText(item.studentName).contains(query)
                || normalizeSearchText(item.registration).contains(query)
                || normalizeSearchText(item.studentId).contains(query)
                || normalizeSearchText(status).contains(query)
                || Integer.toString(item.rosterPosition).equals(query)
                || revision.contains(query);
    }

    private static String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
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

    public String getFrozenClassName() {
        return frozenClassName;
    }

    public long getCreatedAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public String getQuery() {
        return query;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getCorrectedCount() {
        return correctedCount;
    }

    public int getPendingCount() {
        return totalCount - correctedCount;
    }

    public String getFirstPendingStudentIdOrNull() {
        return firstPendingStudentId;
    }

    public int getVisibleCount() {
        return visibleParticipants.size();
    }

    public List<ParticipantItem> getVisibleParticipants() {
        return visibleParticipants;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean hasNoMatches() {
        return hasQuery() && visibleParticipants.isEmpty();
    }

    public ParticipantItem findVisibleParticipantOrNull(String studentId) {
        for (ParticipantItem item : visibleParticipants) {
            if (item.hasStudentId(studentId)) {
                return item;
            }
        }
        return null;
    }
}
