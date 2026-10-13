package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.application.history.OmrGradingHistoryRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Monta o relatório usando a chamada congelada e a última revisão de cada aluno. */
public final class OmrAssessmentResultReportService {

    private final OmrAssessmentApplicationRepository applicationRepository;
    private final OmrAssessmentRepository assessmentRepository;
    private final OmrAssessmentParticipantRepository participantRepository;
    private final OmrAssessmentAttemptRepository attemptRepository;
    private final OmrGradingHistoryRepository historyRepository;

    public OmrAssessmentResultReportService(
            OmrAssessmentApplicationRepository applicationRepository,
            OmrAssessmentRepository assessmentRepository,
            OmrAssessmentParticipantRepository participantRepository,
            OmrAssessmentAttemptRepository attemptRepository,
            OmrGradingHistoryRepository historyRepository
    ) {
        if (applicationRepository == null
                || assessmentRepository == null
                || participantRepository == null
                || attemptRepository == null
                || historyRepository == null) {
            throw new IllegalArgumentException(
                    "Todos os repositórios do relatório são obrigatórios."
            );
        }
        this.applicationRepository = applicationRepository;
        this.assessmentRepository = assessmentRepository;
        this.participantRepository = participantRepository;
        this.attemptRepository = attemptRepository;
        this.historyRepository = historyRepository;
    }

    public OmrAssessmentResultReport build(String applicationId) {
        return build(applicationId, System.currentTimeMillis());
    }

    public OmrAssessmentResultReport build(
            String applicationId,
            long generatedAtEpochMillis
    ) {
        String normalizedId = requireText(applicationId);
        if (generatedAtEpochMillis <= 0L) {
            throw new IllegalArgumentException(
                    "O instante de geração deve ser positivo."
            );
        }

        OmrAssessmentApplication application =
                applicationRepository.findByIdOrNull(normalizedId);
        if (application == null) {
            throw new IllegalArgumentException(
                    "A aplicação solicitada não foi encontrada."
            );
        }

        OmrAssessmentDefinition assessment = assessmentRepository.findOrNull(
                application.getAssessmentId(),
                application.getAssessmentVersion()
        );
        if (assessment == null) {
            throw new IllegalStateException(
                    "A versão exata da prova não está disponível."
            );
        }

        List<OmrAssessmentParticipant> participants = new ArrayList<>(
                participantRepository.loadForApplication(normalizedId)
        );
        if (participants.isEmpty()) {
            throw new IllegalStateException(
                    "A aplicação não possui chamada congelada."
            );
        }
        participants.sort(Comparator
                .comparingInt(OmrAssessmentParticipant::getRosterPosition)
                .thenComparing(OmrAssessmentParticipant::getStudentName)
                .thenComparing(OmrAssessmentParticipant::getStudentId));

        Map<String, OmrAssessmentAttempt> latestByStudent = new HashMap<>();
        for (OmrAssessmentAttempt attempt
                : attemptRepository.loadForApplication(normalizedId)) {
            if (!attempt.belongsToApplication(normalizedId)) {
                continue;
            }
            OmrAssessmentAttempt previous = latestByStudent.get(
                    attempt.getStudentId()
            );
            if (previous == null
                    || attempt.getRevision() > previous.getRevision()
                    || (attempt.getRevision() == previous.getRevision()
                    && attempt.getRecordedAtEpochMillis()
                    > previous.getRecordedAtEpochMillis())) {
                latestByStudent.put(attempt.getStudentId(), attempt);
            }
        }

        List<OmrAssessmentResultReport.Row> rows = new ArrayList<>();
        for (OmrAssessmentParticipant participant : participants) {
            if (!participant.belongsToApplication(normalizedId)) {
                throw new IllegalStateException(
                        "A chamada contém participante de outra aplicação."
                );
            }
            OmrAssessmentAttempt attempt = latestByStudent.get(
                    participant.getStudentId()
            );
            OmrGradingHistoryRecord history = attempt == null
                    ? null
                    : historyRepository.findByIdOrNull(
                    attempt.getHistoryRecordId()
            );
            if (history != null
                    && !isCompatible(history, participant, assessment)) {
                history = null;
            }
            rows.add(new OmrAssessmentResultReport.Row(
                    participant,
                    attempt,
                    history
            ));
        }

        return new OmrAssessmentResultReport(
                application,
                assessment,
                participants.get(0).getClassName(),
                generatedAtEpochMillis,
                rows
        );
    }

    public static boolean isCompatible(
            OmrGradingHistoryRecord history,
            OmrAssessmentParticipant participant,
            OmrAssessmentDefinition assessment
    ) {
        if (history == null || participant == null || assessment == null) {
            return false;
        }
        return history.belongsToStudent(participant.getStudentId())
                && history.getStudent().getRegistration().equals(
                participant.getRegistration()
        )
                && history.getStudent().getName().equals(
                participant.getStudentName()
        )
                && history.getStudent().getClassName().equals(
                participant.getClassName()
        )
                && history.getAnswerKeyId().equals(
                assessment.getAnswerKeyId()
        )
                && history.getAnswerKeyVersion()
                == assessment.getAnswerKeyVersion();
    }

    private static String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O applicationId é obrigatório."
            );
        }
        return value.trim();
    }
}
