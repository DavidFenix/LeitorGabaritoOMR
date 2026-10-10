package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;

import java.util.List;

/**
 * Catálogo das aplicações imutáveis de versões de prova em turmas.
 *
 * Uma aplicação é identificada pelo {@code applicationId}. Depois de salva,
 * seu vínculo com prova e turma não pode ser reescrito.
 */
public interface OmrAssessmentApplicationRepository {

    /**
     * Acrescenta uma aplicação ao catálogo.
     *
     * @return {@code true} quando foi salva; {@code false} quando o mesmo
     * applicationId já existia. O conteúdo original deve ser preservado.
     */
    boolean save(OmrAssessmentApplication application);

    /**
     * Retorna todas as aplicações, da criação mais recente para a mais antiga.
     */
    List<OmrAssessmentApplication> loadAll();

    List<OmrAssessmentApplication> loadForClassGroup(
            String classGroupId
    );

    List<OmrAssessmentApplication> loadForAssessment(
            String assessmentId,
            int assessmentVersion
    );

    OmrAssessmentApplication findByIdOrNull(
            String applicationId
    );
}
