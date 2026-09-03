package com.example.leitorgabaritoomr.application.history;

import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

/**
 * Registra correcoes OMR no historico de forma idempotente.
 *
 * A identidade da operacao e a readingId. Se o Android repetir um callback
 * para a mesma leitura, o registro original e devolvido e nenhuma segunda
 * nota e criada. Uma readingId previamente ligada a outro aluno ou a outro
 * resultado e tratada como conflito de integridade.
 */
public final class OmrGradingHistoryRecorder {

    private final OmrGradingHistoryRepository repository;

    public OmrGradingHistoryRecorder(
            OmrGradingHistoryRepository repository
    ) {
        if (repository == null) {
            throw new IllegalArgumentException(
                    "O repositorio de historico e obrigatorio."
            );
        }

        this.repository = repository;
    }

    /**
     * Cria ou recupera o unico registro pertencente a esta leitura.
     */
    public OmrGradingHistoryRecord record(
            OmrStudentIdentity student,
            OmrGradingResult gradingResult
    ) {
        if (student == null) {
            throw new IllegalArgumentException(
                    "O aluno e obrigatorio."
            );
        }

        if (gradingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da correcao e obrigatorio."
            );
        }

        String readingId = gradingResult
                .getReadingResult()
                .getReadingId();

        OmrGradingHistoryRecord existingRecord =
                repository.findByReadingIdOrNull(
                        readingId
                );

        if (existingRecord != null) {
            validateExistingRecord(
                    existingRecord,
                    student,
                    gradingResult
            );

            return existingRecord;
        }

        OmrGradingHistoryRecord newRecord =
                OmrGradingHistoryRecord.create(
                        student,
                        gradingResult
                );

        if (repository.save(newRecord)) {
            return newRecord;
        }

        /*
         * Outra chamada pode ter armazenado a mesma leitura entre a busca e
         * a insercao. Nesse caso, recuperamos a linha vencedora e aplicamos
         * exatamente as mesmas validacoes de integridade.
         */
        existingRecord = repository.findByReadingIdOrNull(
                readingId
        );

        if (existingRecord == null) {
            throw new IllegalStateException(
                    "O repositorio recusou o registro sem preservar"
                            + " uma leitura existente."
            );
        }

        validateExistingRecord(
                existingRecord,
                student,
                gradingResult
        );

        return existingRecord;
    }

    private static void validateExistingRecord(
            OmrGradingHistoryRecord existingRecord,
            OmrStudentIdentity student,
            OmrGradingResult gradingResult
    ) {
        if (!existingRecord.getStudent().equals(student)) {
            throw new IllegalStateException(
                    "A leitura ja esta vinculada a outro aluno: "
                            + existingRecord.getReadingId()
            );
        }

        if (!existingRecord.getGradingResult().equals(
                gradingResult
        )) {
            throw new IllegalStateException(
                    "A leitura ja possui outro resultado no historico: "
                            + existingRecord.getReadingId()
            );
        }
    }
}
