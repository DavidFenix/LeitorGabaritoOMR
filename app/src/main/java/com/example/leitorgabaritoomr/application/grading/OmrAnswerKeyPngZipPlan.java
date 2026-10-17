package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plano imutável e serializável de um lote de cartões PNG.
 *
 * A Activity pode preservá-lo durante recriações enquanto o seletor de
 * destino está aberto. Nenhum bitmap nem conteúdo PNG é mantido no plano.
 */
public final class OmrAnswerKeyPngZipPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<Card> cards;
    private final List<Issue> issues;

    OmrAnswerKeyPngZipPlan(
            List<Card> cards,
            List<Issue> issues
    ) {
        if (cards == null || issues == null) {
            throw new IllegalArgumentException(
                    "Os cartões e problemas do plano são obrigatórios."
            );
        }
        this.cards = Collections.unmodifiableList(
                new ArrayList<>(cards)
        );
        this.issues = Collections.unmodifiableList(
                new ArrayList<>(issues)
        );
    }

    public List<Card> getCards() {
        return cards;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public int getCardCount() {
        return cards.size();
    }

    public boolean isReadyToExport() {
        return !cards.isEmpty() && issues.isEmpty();
    }

    public static final class Card implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int lineNumber;
        private final String code;
        private final OmrAnswerKeyDefinition answerKeyDefinition;
        private final String outputFileName;
        private final String headerOverride;
        private final String instructionOverride;
        private final String footerOverride;

        Card(
                int lineNumber,
                String code,
                OmrAnswerKeyDefinition answerKeyDefinition,
                String outputFileName,
                String headerOverride,
                String instructionOverride,
                String footerOverride
        ) {
            this.lineNumber = lineNumber;
            this.code = code;
            this.answerKeyDefinition = answerKeyDefinition;
            this.outputFileName = outputFileName;
            this.headerOverride = headerOverride;
            this.instructionOverride = instructionOverride;
            this.footerOverride = footerOverride;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getCode() {
            return code;
        }

        public OmrAnswerKeyDefinition getAnswerKeyDefinition() {
            return answerKeyDefinition;
        }

        public String getOutputFileName() {
            return outputFileName;
        }

        public String getHeaderOverride() {
            return headerOverride;
        }

        public String getInstructionOverride() {
            return instructionOverride;
        }

        public String getFooterOverride() {
            return footerOverride;
        }
    }

    public static final class Issue implements Serializable {

        private static final long serialVersionUID = 1L;

        private final int lineNumber;
        private final String message;

        Issue(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getMessage() {
            return message;
        }
    }
}
