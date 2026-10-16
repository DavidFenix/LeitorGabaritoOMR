package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Coordena a gravacao transacional de uma pre-visualizacao CSV validada. */
public final class OmrAnswerKeyCsvRegistrationService {

    private final OmrAnswerKeyBatchRepository batchRepository;

    public OmrAnswerKeyCsvRegistrationService(
            OmrAnswerKeyBatchRepository batchRepository
    ) {
        if (batchRepository == null) {
            throw new IllegalArgumentException(
                    "O repositorio transacional de gabaritos e obrigatorio."
            );
        }
        this.batchRepository = batchRepository;
    }

    public ImportSummary importPreview(
            OmrAnswerKeyCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A pre-visualizacao do CSV e obrigatoria."
            );
        }
        if (!preview.isReadyToImport()) {
            throw new IllegalArgumentException(
                    "O CSV de gabaritos deve estar sem erros antes da importacao."
            );
        }

        List<OmrAnswerKeyDefinition> definitions = new ArrayList<>(
                preview.getRows().size()
        );
        for (OmrAnswerKeyCsvParser.Row row : preview.getRows()) {
            definitions.add(row.getAnswerKeyDefinition());
        }

        int imported = batchRepository.saveAllCompatible(definitions);
        return new ImportSummary(
                definitions.size(),
                imported,
                definitions.size() - imported,
                definitions
        );
    }

    public static final class ImportSummary {
        private final int processedCount;
        private final int importedCount;
        private final int unchangedCount;
        private final List<OmrAnswerKeyDefinition> answerKeys;

        private ImportSummary(
                int processedCount,
                int importedCount,
                int unchangedCount,
                List<OmrAnswerKeyDefinition> answerKeys
        ) {
            this.processedCount = processedCount;
            this.importedCount = importedCount;
            this.unchangedCount = unchangedCount;
            this.answerKeys = Collections.unmodifiableList(
                    new ArrayList<>(answerKeys)
            );
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public int getImportedCount() {
            return importedCount;
        }

        public int getUnchangedCount() {
            return unchangedCount;
        }

        public List<OmrAnswerKeyDefinition> getAnswerKeys() {
            return answerKeys;
        }
    }
}
