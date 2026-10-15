package com.example.leitorgabaritoomr.infrastructure.export;

import java.util.Objects;

/**
 * Textos opcionais impressos no cartao-resposta.
 *
 * {@code null} significa usar o texto dinamico padrao. Uma String vazia
 * significa que o usuario decidiu omitir aquele texto.
 */
public final class OmrSheetTextCustomization {

    public static final int MAX_TEXT_LENGTH = 80;

    public static final String DEFAULT_HEADER_TEXT =
            "CARTÃO-RESPOSTA";
    public static final String DEFAULT_SINGLE_COLUMN_INSTRUCTION_TEXT =
            "Marque apenas uma alternativa";
    public static final String DEFAULT_MULTIPLE_COLUMN_INSTRUCTION_TEXT =
            "Preencha completamente apenas uma alternativa por questão";
    public static final String DEFAULT_SINGLE_COLUMN_FOOTER_TEXT =
            "Mantenha a proporção";
    public static final String DEFAULT_MULTIPLE_COLUMN_FOOTER_TEXT =
            "Mantenha a proporção original ao redimensionar";

    private final String headerOverride;
    private final String instructionOverride;
    private final String footerOverride;

    private OmrSheetTextCustomization(
            String headerOverride,
            String instructionOverride,
            String footerOverride
    ) {
        this.headerOverride = normalizeOverride(
                headerOverride,
                "cabecalho"
        );
        this.instructionOverride = normalizeOverride(
                instructionOverride,
                "instrucao"
        );
        this.footerOverride = normalizeOverride(
                footerOverride,
                "rodape"
        );
    }

    public static OmrSheetTextCustomization defaults() {
        return new OmrSheetTextCustomization(null, null, null);
    }

    public static OmrSheetTextCustomization custom(
            String header,
            String instruction,
            String footer
    ) {
        if (header == null || instruction == null || footer == null) {
            throw new IllegalArgumentException(
                    "Os textos personalizados nao podem ser nulos."
            );
        }
        return new OmrSheetTextCustomization(
                header,
                instruction,
                footer
        );
    }

    public static OmrSheetTextCustomization fromOverrides(
            String headerOverride,
            String instructionOverride,
            String footerOverride
    ) {
        return new OmrSheetTextCustomization(
                headerOverride,
                instructionOverride,
                footerOverride
        );
    }

    public OmrSheetTextCustomization withHeaderOverride(
            String value
    ) {
        return new OmrSheetTextCustomization(
                value,
                instructionOverride,
                footerOverride
        );
    }

    public OmrSheetTextCustomization withInstructionOverride(
            String value
    ) {
        return new OmrSheetTextCustomization(
                headerOverride,
                value,
                footerOverride
        );
    }

    public OmrSheetTextCustomization withFooterOverride(
            String value
    ) {
        return new OmrSheetTextCustomization(
                headerOverride,
                instructionOverride,
                value
        );
    }

    public boolean hasHeaderOverride() {
        return headerOverride != null;
    }

    public boolean hasInstructionOverride() {
        return instructionOverride != null;
    }

    public boolean hasFooterOverride() {
        return footerOverride != null;
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

    public String resolveHeaderText() {
        return hasHeaderOverride()
                ? headerOverride
                : DEFAULT_HEADER_TEXT;
    }

    public String resolveInstructionText(
            int columnCount
    ) {
        if (hasInstructionOverride()) {
            return instructionOverride;
        }
        return columnCount <= 1
                ? DEFAULT_SINGLE_COLUMN_INSTRUCTION_TEXT
                : DEFAULT_MULTIPLE_COLUMN_INSTRUCTION_TEXT;
    }

    public String resolveFooterText(
            int columnCount
    ) {
        if (hasFooterOverride()) {
            return footerOverride;
        }
        return columnCount <= 1
                ? DEFAULT_SINGLE_COLUMN_FOOTER_TEXT
                : DEFAULT_MULTIPLE_COLUMN_FOOTER_TEXT;
    }

    private static String normalizeOverride(
            String value,
            String label
    ) {
        if (value == null) {
            return null;
        }

        StringBuilder normalized = new StringBuilder(value.length());
        boolean pendingSpace = false;

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            if (Character.isSurrogate(character)) {
                throw new IllegalArgumentException(
                        "Caractere invalido no " + label + "."
                );
            }

            if (Character.isWhitespace(character)) {
                pendingSpace = normalized.length() > 0;
                continue;
            }

            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException(
                        "Caractere invalido no " + label + "."
                );
            }

            if (pendingSpace) {
                normalized.append(' ');
                pendingSpace = false;
            }
            normalized.append(character);
        }

        if (normalized.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "O " + label + " aceita ate "
                            + MAX_TEXT_LENGTH
                            + " caracteres."
            );
        }

        return normalized.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OmrSheetTextCustomization)) {
            return false;
        }
        OmrSheetTextCustomization that =
                (OmrSheetTextCustomization) other;
        return Objects.equals(headerOverride, that.headerOverride)
                && Objects.equals(
                instructionOverride,
                that.instructionOverride
        )
                && Objects.equals(footerOverride, that.footerOverride);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                headerOverride,
                instructionOverride,
                footerOverride
        );
    }
}
