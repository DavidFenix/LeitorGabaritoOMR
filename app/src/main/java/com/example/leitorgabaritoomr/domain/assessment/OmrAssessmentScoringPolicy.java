package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Contrato versionado para calcular a nota de um bloco da prova.
 *
 * A identidade da politica e sua versao devem permanecer estaveis. Uma
 * mudanca de formula precisa publicar outra versao, permitindo reproduzir
 * no futuro exatamente a nota calculada no momento da correcao.
 */
public interface OmrAssessmentScoringPolicy
        extends Serializable {

    String getPolicyId();

    int getVersion();

    BigDecimal getMaximumScore();

    BigDecimal calculate(
            OmrAssessmentScoreInput input
    );
}
