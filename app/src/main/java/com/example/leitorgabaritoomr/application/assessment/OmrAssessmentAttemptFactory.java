package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Vincula uma correção histórica ao participante correto de uma aplicação.
 *
 * A revisão é calculada a partir das tentativas já existentes. Dessa forma,
 * uma nova leitura nunca substitui silenciosamente a correção anterior.
 */
public final class OmrAssessmentAttemptFactory {

    public OmrAssessmentAttempt create(
            String attemptId,
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            OmrAssessmentParticipant participant,
            OmrGradingHistoryRecord historyRecord,
            List<OmrAssessmentAttempt> existingAttempts
    ) {
        if (application == null || historyRecord == null) {
            return create(
                    attemptId,
                    application,
                    assessment,
                    participant,
                    historyRecord,
                    existingAttempts,
                    null
            );
        }

        OmrAssessmentStructure effectiveStructure = application
                .getFrozenStructureOrNull();
        if (effectiveStructure == null) {
            effectiveStructure = OmrAssessmentStructure.legacySingleBlock(
                    application.getAssessmentId(),
                    application.getAssessmentVersion(),
                    historyRecord.getGradingResult().getQuestionCount()
            );
        }

        return create(
                attemptId,
                application,
                assessment,
                participant,
                historyRecord,
                existingAttempts,
                effectiveStructure
        );
    }

    public OmrAssessmentAttempt create(
            String attemptId,
            OmrAssessmentApplication application,
            OmrAssessmentDefinition assessment,
            OmrAssessmentParticipant participant,
            OmrGradingHistoryRecord historyRecord,
            List<OmrAssessmentAttempt> existingAttempts,
            OmrAssessmentStructure frozenStructure
    ) {
        String normalizedAttemptId = requireText(
                "attemptId",
                attemptId
        );

        if (application == null) {
            throw new IllegalArgumentException(
                    "A aplicação da prova é obrigatória."
            );
        }

        if (assessment == null) {
            throw new IllegalArgumentException(
                    "A prova é obrigatória."
            );
        }

        if (participant == null) {
            throw new IllegalArgumentException(
                    "O participante é obrigatório."
            );
        }

        if (historyRecord == null) {
            throw new IllegalArgumentException(
                    "O registro da correção é obrigatório."
            );
        }

        if (existingAttempts == null) {
            throw new IllegalArgumentException(
                    "A coleção de tentativas existentes é obrigatória."
            );
        }

        if (frozenStructure == null) {
            throw new IllegalArgumentException(
                    "A estrutura usada na correção é obrigatória."
            );
        }

        validateAssessmentApplication(
                assessment,
                application
        );
        validateParticipantApplication(
                application,
                participant
        );
        validateHistoryStudent(
                participant,
                historyRecord
        );
        validateAnswerKey(
                assessment,
                historyRecord
        );
        validateStructure(
                frozenStructure,
                assessment,
                historyRecord
        );

        int nextRevision = resolveNextRevision(
                normalizedAttemptId,
                participant,
                historyRecord,
                existingAttempts
        );

        return new OmrAssessmentAttempt(
                normalizedAttemptId,
                application.getApplicationId(),
                participant.getStudentId(),
                nextRevision,
                historyRecord.getHistoryRecordId(),
                historyRecord.getStoredAtEpochMillis(),
                frozenStructure
        );
    }

    private static void validateStructure(
            OmrAssessmentStructure structure,
            OmrAssessmentDefinition assessment,
            OmrGradingHistoryRecord historyRecord
    ) {
        if (!structure.hasAssessmentIdentity(
                assessment.getAssessmentId(),
                assessment.getVersion()
        )) {
            throw new IllegalArgumentException(
                    "A estrutura não pertence à versão da prova."
            );
        }

        if (structure.getQuestionCount()
                != historyRecord.getGradingResult().getQuestionCount()) {
            throw new IllegalArgumentException(
                    "A estrutura não possui a quantidade de questões corrigida."
            );
        }
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " não pode ser vazio."
            );
        }

        return value.trim();
    }

    private static void validateAssessmentApplication(
            OmrAssessmentDefinition assessment,
            OmrAssessmentApplication application
    ) {
        if (!application.usesAssessment(
                assessment.getAssessmentId(),
                assessment.getVersion()
        )) {
            throw new IllegalArgumentException(
                    "A prova informada não pertence à aplicação."
            );
        }
    }

    private static void validateParticipantApplication(
            OmrAssessmentApplication application,
            OmrAssessmentParticipant participant
    ) {
        if (!participant.belongsToApplication(
                application.getApplicationId()
        )) {
            throw new IllegalArgumentException(
                    "O participante não pertence à aplicação."
            );
        }
    }

    private static void validateHistoryStudent(
            OmrAssessmentParticipant participant,
            OmrGradingHistoryRecord historyRecord
    ) {
        OmrStudentIdentity historicalStudent =
                historyRecord.getStudent();

        if (!participant.belongsToStudent(
                historicalStudent.getStudentId()
        )) {
            throw new IllegalArgumentException(
                    "A correção pertence a outro aluno."
            );
        }

        boolean sameSnapshot =
                participant.getRegistration().equals(
                        historicalStudent.getRegistration()
                )
                        && participant.getStudentName().equals(
                        historicalStudent.getName()
                )
                        && participant.getClassName().equals(
                        historicalStudent.getClassName()
                );

        if (!sameSnapshot) {
            throw new IllegalArgumentException(
                    "A identificação histórica do aluno não corresponde"
                            + " ao participante da aplicação."
            );
        }
    }

    private static void validateAnswerKey(
            OmrAssessmentDefinition assessment,
            OmrGradingHistoryRecord historyRecord
    ) {
        if (!assessment.usesAnswerKey(
                historyRecord.getAnswerKeyId(),
                historyRecord.getAnswerKeyVersion()
        )) {
            throw new IllegalArgumentException(
                    "A correção não utiliza o gabarito exato da prova."
            );
        }
    }

    private static int resolveNextRevision(
            String attemptId,
            OmrAssessmentParticipant participant,
            OmrGradingHistoryRecord historyRecord,
            List<OmrAssessmentAttempt> existingAttempts
    ) {
        Set<String> knownAttemptIds = new HashSet<>();
        Set<String> knownHistoryRecordIds = new HashSet<>();
        Set<Integer> participantRevisions = new HashSet<>();

        int highestRevision = 0;

        for (OmrAssessmentAttempt existing
                : existingAttempts) {

            if (existing == null) {
                throw new IllegalArgumentException(
                        "A coleção de tentativas não pode"
                                + " conter valores nulos."
                );
            }

            if (!knownAttemptIds.add(
                    existing.getAttemptId()
            )) {
                throw new IllegalArgumentException(
                        "Tentativa existente repetida: "
                                + existing.getAttemptId()
                );
            }

            if (!knownHistoryRecordIds.add(
                    existing.getHistoryRecordId()
            )) {
                throw new IllegalArgumentException(
                        "Correção histórica já repetida"
                                + " entre tentativas: "
                                + existing.getHistoryRecordId()
                );
            }

            if (existing.hasAttemptId(attemptId)) {
                throw new IllegalArgumentException(
                        "A identidade da tentativa já está em uso: "
                                + attemptId
                );
            }

            if (existing.referencesHistoryRecord(
                    historyRecord.getHistoryRecordId()
            )) {
                throw new IllegalArgumentException(
                        "A correção histórica já está vinculada"
                                + " a uma tentativa: "
                                + historyRecord.getHistoryRecordId()
                );
            }

            if (!existing.belongsToParticipant(
                    participant.getApplicationId(),
                    participant.getStudentId()
            )) {
                continue;
            }

            if (!participantRevisions.add(
                    existing.getRevision()
            )) {
                throw new IllegalArgumentException(
                        "Revisão repetida para o participante: "
                                + existing.getRevision()
                );
            }

            highestRevision = Math.max(
                    highestRevision,
                    existing.getRevision()
            );
        }

        if (highestRevision != participantRevisions.size()) {
            throw new IllegalArgumentException(
                    "As revisões existentes do participante"
                            + " devem formar uma sequência contínua."
            );
        }

        if (highestRevision == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "O limite de revisões do participante foi atingido."
            );
        }

        return highestRevision + 1;
    }
}
