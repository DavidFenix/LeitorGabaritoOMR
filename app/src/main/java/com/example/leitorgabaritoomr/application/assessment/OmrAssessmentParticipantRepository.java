package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.util.List;

/**
 * Armazena a fotografia imutável da chamada de cada aplicação de prova.
 */
public interface OmrAssessmentParticipantRepository {

    /**
     * Grava toda a chamada em uma única transação.
     *
     * @return {@code true} quando a chamada foi salva; {@code false} quando a
     * aplicação já possuía participantes, preservando a fotografia original.
     */
    boolean saveRoster(
            String applicationId,
            List<OmrAssessmentParticipant> participants
    );

    List<OmrAssessmentParticipant> loadForApplication(
            String applicationId
    );

    OmrAssessmentParticipant findOrNull(
            String applicationId,
            String studentId
    );
}
