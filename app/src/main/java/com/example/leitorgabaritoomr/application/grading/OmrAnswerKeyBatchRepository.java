package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.util.List;

/**
 * Porta transacional para importar gabaritos oficiais em lote.
 *
 * A operacao deve validar a colecao inteira antes de persistir qualquer item.
 * Uma identidade ja armazenada pode ser repetida apenas quando todo o seu
 * conteudo for identico. Uma identidade igual com conteudo diferente deve
 * rejeitar o lote completo. A selecao ativa nunca e alterada pela importacao.
 */
public interface OmrAnswerKeyBatchRepository {

    /**
     * Persiste, em uma unica transacao, os gabaritos ainda inexistentes.
     *
     * @return quantidade de novas identidades efetivamente armazenadas.
     */
    int saveAllCompatible(
            List<OmrAnswerKeyDefinition> answerKeys
    );
}
