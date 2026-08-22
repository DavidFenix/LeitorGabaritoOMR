package com.example.leitorgabaritoomr.vision.interpretation;

/**
 * Estado semantico final de uma questao OMR.
 *
 * O vencedor matematico do ranking nao e automaticamente uma
 * resposta marcada. Esta enumeracao permite que a camada de
 * interpretacao se recuse a escolher quando a evidencia nao for
 * suficiente ou coerente.
 */
public enum QuestionMarkState {

    /**
     * O consenso ainda nao acumulou frames suficientes.
     */
    NOT_READY,

    /**
     * Exatamente uma alternativa foi reconhecida como marcada.
     */
    SINGLE_MARK,

    /**
     * Nenhuma alternativa apresentou evidencia suficiente.
     */
    BLANK,

    /**
     * Duas ou mais alternativas apresentaram evidencia de marca.
     */
    MULTIPLE_MARKS,

    /**
     * Existe evidencia, mas ela nao permite uma decisao segura.
     */
    AMBIGUOUS;

    public boolean isReady() {
        return this != NOT_READY;
    }

    public boolean hasSingleMark() {
        return this == SINGLE_MARK;
    }

    public boolean requiresReview() {
        return this == MULTIPLE_MARKS
                || this == AMBIGUOUS;
    }
}
