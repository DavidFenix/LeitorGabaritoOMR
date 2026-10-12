package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import java.util.List;

/**
 * Porta transacional para acrescentar versões de provas em lote.
 *
 * O lote deve ser confirmado por inteiro. Nenhuma versão existente pode ser
 * substituída, pois uma prova publicada é imutável e pode estar vinculada a
 * aplicações ou correções históricas.
 */
public interface OmrAssessmentBatchRepository {

    void saveAllNew(
            List<OmrAssessmentDefinition> assessments
    );
}
