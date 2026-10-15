package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;

import java.util.List;

/**
 * Persistencia imutavel da estrutura curricular de cada versao de prova.
 *
 * Uma estrutura pertence ao par exato {@code assessmentId + version}. Depois
 * de salva, qualquer alteracao deve acompanhar uma nova versao da prova.
 */
public interface OmrAssessmentStructureRepository {

    /**
     * Acrescenta uma estrutura ao catalogo.
     *
     * @return {@code true} quando foi salva; {@code false} quando a mesma
     * identidade de prova ja possuia uma estrutura. O registro anterior nao
     * deve ser substituido.
     */
    boolean save(OmrAssessmentStructure structure);

    /** Retorna a estrutura explicitamente gravada ou {@code null}. */
    OmrAssessmentStructure findStoredOrNull(
            String assessmentId,
            int assessmentVersion
    );

    /**
     * Retorna as estruturas gravadas para uma prova, da versao mais alta para
     * a mais baixa, em uma fotografia imutavel.
     */
    List<OmrAssessmentStructure> loadStoredVersions(
            String assessmentId
    );

    /**
     * Resolve uma estrutura gravada ou cria o bloco unico de compatibilidade.
     *
     * O bloco legado so e produzido quando a versao da prova existe e nao ha
     * nenhuma estrutura gravada. Dados persistidos corrompidos, identidade
     * inexistente ou divergencia na quantidade de questoes retornam
     * {@code null}; assim uma configuracao multidisciplinar defeituosa nunca
     * e silenciosamente tratada como prova antiga.
     */
    OmrAssessmentStructure resolveOrLegacy(
            String assessmentId,
            int assessmentVersion,
            int questionCount
    );
}
