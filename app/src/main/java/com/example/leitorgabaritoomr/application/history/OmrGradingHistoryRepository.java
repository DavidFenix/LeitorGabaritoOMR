package com.example.leitorgabaritoomr.application.history;

import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;

import java.util.List;

/**
 * Repositorio do historico de leituras corrigidas.
 *
 * O historico e somente de acrescimo: um registro existente nunca deve
 * ser substituido por outro. Tanto historyRecordId quanto readingId sao
 * identidades unicas, impedindo que a mesma leitura seja vinculada ou
 * armazenada duas vezes.
 *
 * Implementacoes devem devolver fotografias imutaveis e independentes
 * da colecao interna.
 */
public interface OmrGradingHistoryRepository {

    /**
     * Acrescenta um registro ao historico.
     *
     * @return {@code true} quando o registro foi salvo; {@code false}
     * quando ja existe um registro com o mesmo historyRecordId ou com
     * o mesmo readingId. Em caso de duplicidade, o registro anteriormente
     * armazenado deve permanecer intacto.
     *
     * @throws IllegalArgumentException quando o registro for nulo.
     */
    boolean save(
            OmrGradingHistoryRecord record
    );

    /**
     * Retorna todo o historico do armazenamento mais recente para o mais
     * antigo. A lista retornada deve ser imutavel.
     */
    List<OmrGradingHistoryRecord> loadAll();

    /**
     * Localiza um registro por sua identidade propria.
     *
     * @return o registro encontrado ou {@code null} quando a identidade
     * nao existe ou e nula.
     */
    OmrGradingHistoryRecord findByIdOrNull(
            String historyRecordId
    );

    /**
     * Localiza o registro criado para uma leitura OMR especifica.
     *
     * @return o registro encontrado ou {@code null} quando a leitura nao
     * esta armazenada ou sua identidade e nula.
     */
    OmrGradingHistoryRecord findByReadingIdOrNull(
            String readingId
    );

    /**
     * Retorna o historico de um aluno, tambem do armazenamento mais
     * recente para o mais antigo. A correspondencia deve usar o
     * studentId estavel, nunca apenas nome ou matricula.
     *
     * Uma identidade nula ou vazia deve produzir uma lista vazia.
     */
    List<OmrGradingHistoryRecord> loadByStudentId(
            String studentId
    );
}
