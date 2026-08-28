package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

/**
 * Porta de persistência do gabarito oficial atualmente ativo.
 *
 * A camada de aplicação conhece somente este contrato. A forma concreta de
 * armazenamento — arquivo privado, preferências ou banco de dados — pertence
 * à infraestrutura e pode mudar sem afetar o domínio nem a correção OMR.
 */
public interface OmrActiveAnswerKeyStore {

    /**
     * Salva o gabarito como ativo, substituindo de maneira segura qualquer
     * gabarito anteriormente selecionado.
     */
    void saveActive(
            OmrAnswerKeyDefinition answerKeyDefinition
    );

    /**
     * Recupera o gabarito ativo.
     *
     * @return o gabarito persistido ou {@code null} quando ainda não existe
     * um gabarito ativo.
     */
    OmrAnswerKeyDefinition loadActiveOrNull();

    /**
     * Remove o gabarito ativo. A operação deve ser idempotente.
     */
    void clearActive();
}
