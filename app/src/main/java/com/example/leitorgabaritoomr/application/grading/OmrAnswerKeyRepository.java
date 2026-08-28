package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.util.List;

/**
 * Repositório dos gabaritos oficiais disponíveis no dispositivo.
 *
 * Cada gabarito é identificado pelo par {@code id + version}. A implementação
 * deve preservar todas as versões armazenadas e manter, separadamente, a
 * referência ao gabarito atualmente ativo.
 */
public interface OmrAnswerKeyRepository
        extends OmrActiveAnswerKeyStore {

    /**
     * Salva um gabarito na coleção sem alterar a seleção ativa.
     *
     * Quando o mesmo par {@code id + version} já existir, seu conteúdo é
     * substituído. A ordem retornada por {@link #loadAll()} deve colocar o
     * item salvo mais recentemente primeiro.
     */
    void save(
            OmrAnswerKeyDefinition answerKeyDefinition
    );

    /**
     * Retorna uma fotografia imutável dos gabaritos armazenados, do mais
     * recentemente salvo para o mais antigo.
     */
    List<OmrAnswerKeyDefinition> loadAll();

    /**
     * Localiza uma versão exata de um gabarito.
     *
     * @return o gabarito encontrado ou {@code null} quando ele não existe ou
     * seus dados persistidos são inválidos.
     */
    OmrAnswerKeyDefinition findOrNull(
            String answerKeyId,
            int answerKeyVersion
    );

    /**
     * Torna ativo um gabarito que já pertence à coleção.
     *
     * @throws IllegalArgumentException quando a identidade é inválida ou o
     * gabarito solicitado não existe.
     */
    void selectActive(
            String answerKeyId,
            int answerKeyVersion
    );

    /**
     * Remove uma versão exata. Se ela estiver ativa, a seleção também deve ser
     * removida na mesma operação.
     *
     * @return {@code true} quando um gabarito existente foi removido;
     * {@code false} quando a identidade não estava armazenada.
     */
    boolean delete(
            String answerKeyId,
            int answerKeyVersion
    );
}
