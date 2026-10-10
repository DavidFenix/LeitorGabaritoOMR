package com.example.leitorgabaritoomr.application.assessment;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import java.util.List;

/**
 * Catálogo persistente das provas criadas no dispositivo.
 *
 * Cada prova é identificada pelo par {@code assessmentId + version}. Uma
 * versão salva é imutável: alterações devem produzir outra versão, mantendo
 * a anterior disponível para aplicações e resultados históricos.
 */
public interface OmrAssessmentRepository {

    /**
     * Acrescenta uma versão de prova ao catálogo.
     *
     * @return {@code true} quando a versão foi salva; {@code false} quando o
     * mesmo par {@code assessmentId + version} já estava armazenado. Em caso
     * de duplicidade, o conteúdo original deve permanecer intacto.
     *
     * @throws IllegalArgumentException quando a prova for nula.
     */
    boolean save(
            OmrAssessmentDefinition assessment
    );

    /**
     * Retorna todas as versões armazenadas em uma fotografia imutável.
     *
     * A ordem deve ser: ano letivo decrescente, título crescente,
     * assessmentId crescente e versão decrescente.
     */
    List<OmrAssessmentDefinition> loadAll();

    /**
     * Retorna apenas a versão mais alta de cada assessmentId.
     *
     * A lista deve preservar a mesma ordenação escolar de {@link #loadAll()}.
     */
    List<OmrAssessmentDefinition> loadLatestVersions();

    /**
     * Localiza uma versão exata de prova.
     *
     * @return a prova encontrada ou {@code null} para identidade inválida,
     * inexistente ou dados persistidos corrompidos.
     */
    OmrAssessmentDefinition findOrNull(
            String assessmentId,
            int assessmentVersion
    );

    /**
     * Localiza a versão mais alta de uma prova.
     *
     * @return a prova encontrada ou {@code null} quando o assessmentId for
     * inválido, inexistente ou não possuir versão íntegra armazenada.
     */
    OmrAssessmentDefinition findLatestVersionOrNull(
            String assessmentId
    );
}
