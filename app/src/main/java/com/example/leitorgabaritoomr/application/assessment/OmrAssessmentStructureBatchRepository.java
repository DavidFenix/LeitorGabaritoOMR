package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;

import java.util.List;

/**
 * Porta transacional para publicar estruturas curriculares em lote.
 *
 * Todas as provas referenciadas devem existir e nenhuma identidade pode ter
 * estrutura previamente publicada. Uma falha deve preservar o banco inteiro,
 * sem deixar parte do CSV gravada.
 */
public interface OmrAssessmentStructureBatchRepository {

    void saveAllNew(List<OmrAssessmentStructure> structures);
}
