package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;

import java.util.List;

/**
 * Histórico somente de acréscimo das revisões de correção de uma aplicação.
 */
public interface OmrAssessmentAttemptRepository {

    /**
     * Acrescenta uma tentativa sem substituir correções anteriores.
     *
     * @return {@code true} quando foi salva; {@code false} quando o mesmo
     * attemptId já existia, preservando o registro original.
     */
    boolean save(OmrAssessmentAttempt attempt);

    List<OmrAssessmentAttempt> loadForApplication(
            String applicationId
    );

    List<OmrAssessmentAttempt> loadForParticipant(
            String applicationId,
            String studentId
    );

    OmrAssessmentAttempt findLatestForParticipantOrNull(
            String applicationId,
            String studentId
    );

    OmrAssessmentAttempt findByIdOrNull(String attemptId);

    OmrAssessmentAttempt findByHistoryRecordIdOrNull(
            String historyRecordId
    );
}
