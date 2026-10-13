package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;

import java.util.List;

/**
 * Acrescenta ao banco escolar a revisão que referencia uma correção histórica.
 *
 * O historyRecordId também participa da identidade determinística da tentativa.
 * Isso permite repetir com segurança a confirmação após uma interrupção entre
 * a gravação do histórico e a gravação do vínculo com a aplicação.
 */
public final class OmrAssessmentCorrectionRecorder {

    private static final String ATTEMPT_ID_PREFIX = "attempt:";

    private final OmrAssessmentAttemptRepository attemptRepository;
    private final OmrAssessmentAttemptFactory attemptFactory;

    public OmrAssessmentCorrectionRecorder(
            OmrAssessmentAttemptRepository attemptRepository
    ) {
        this(attemptRepository, new OmrAssessmentAttemptFactory());
    }

    OmrAssessmentCorrectionRecorder(
            OmrAssessmentAttemptRepository attemptRepository,
            OmrAssessmentAttemptFactory attemptFactory
    ) {
        if (attemptRepository == null || attemptFactory == null) {
            throw new IllegalArgumentException(
                    "O repositório e a fábrica de tentativas são obrigatórios."
            );
        }

        this.attemptRepository = attemptRepository;
        this.attemptFactory = attemptFactory;
    }

    public OmrAssessmentAttempt record(
            OmrAssessmentCorrectionContext context,
            OmrGradingHistoryRecord historyRecord
    ) {
        if (context == null || historyRecord == null) {
            throw new IllegalArgumentException(
                    "O contexto e o histórico da correção são obrigatórios."
            );
        }

        OmrAssessmentAttempt existing =
                attemptRepository.findByHistoryRecordIdOrNull(
                        historyRecord.getHistoryRecordId()
                );

        if (existing != null) {
            validateExisting(existing, context, historyRecord);
            return existing;
        }

        List<OmrAssessmentAttempt> applicationAttempts =
                attemptRepository.loadForApplication(
                        context.getApplication().getApplicationId()
                );

        String attemptId = ATTEMPT_ID_PREFIX
                + historyRecord.getHistoryRecordId();

        OmrAssessmentAttempt attempt = attemptFactory.create(
                attemptId,
                context.getApplication(),
                context.getAssessment(),
                context.getParticipant(),
                historyRecord,
                applicationAttempts
        );

        try {
            if (attemptRepository.save(attempt)) {
                return attempt;
            }
        } catch (IllegalArgumentException constraintFailure) {
            OmrAssessmentAttempt concurrentWinner = findWinner(
                    attemptId,
                    historyRecord.getHistoryRecordId()
            );
            if (concurrentWinner == null) {
                throw constraintFailure;
            }
            validateExisting(
                    concurrentWinner,
                    context,
                    historyRecord
            );
            return concurrentWinner;
        }

        OmrAssessmentAttempt winner = findWinner(
                attemptId,
                historyRecord.getHistoryRecordId()
        );

        if (winner == null) {
            throw new IllegalStateException(
                    "O repositório recusou a tentativa sem preservar o vínculo."
            );
        }

        validateExisting(winner, context, historyRecord);
        return winner;
    }

    private OmrAssessmentAttempt findWinner(
            String attemptId,
            String historyRecordId
    ) {
        OmrAssessmentAttempt winner =
                attemptRepository.findByIdOrNull(attemptId);

        return winner != null
                ? winner
                : attemptRepository.findByHistoryRecordIdOrNull(
                        historyRecordId
                );
    }

    private static void validateExisting(
            OmrAssessmentAttempt existing,
            OmrAssessmentCorrectionContext context,
            OmrGradingHistoryRecord historyRecord
    ) {
        if (!existing.belongsToParticipant(
                context.getApplication().getApplicationId(),
                context.getParticipant().getStudentId()
        )) {
            throw new IllegalStateException(
                    "A correção histórica já pertence a outro participante."
            );
        }

        if (!existing.referencesHistoryRecord(
                historyRecord.getHistoryRecordId()
        )) {
            throw new IllegalStateException(
                    "A tentativa existente referencia outra correção histórica."
            );
        }

        if (existing.getRecordedAtEpochMillis()
                != historyRecord.getStoredAtEpochMillis()) {
            throw new IllegalStateException(
                    "A tentativa existente possui outro instante de registro."
            );
        }
    }
}
