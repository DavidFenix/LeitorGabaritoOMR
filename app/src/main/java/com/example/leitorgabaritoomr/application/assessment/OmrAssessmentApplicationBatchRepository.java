package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.util.List;

/**
 * Porta transacional para aplicações de prova e suas chamadas congeladas.
 *
 * Todas as aplicações e todos os participantes do lote devem ser confirmados
 * juntos. Uma falha não pode deixar uma aplicação sem a respectiva chamada.
 */
public interface OmrAssessmentApplicationBatchRepository {

    void saveAllNew(
            List<OmrAssessmentApplication> applications,
            List<OmrAssessmentParticipant> participants
    );
}
