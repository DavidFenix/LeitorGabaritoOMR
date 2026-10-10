package com.example.leitorgabaritoomr.infrastructure.export;

import java.nio.charset.StandardCharsets;

/**
 * Resultado imutavel da geracao de um cartao-resposta SVG.
 */
public final class OmrSheetSvgDocument {

    public static final String MIME_TYPE = "image/svg+xml";

    private final String suggestedFileName;
    private final String content;
    private final String templateId;
    private final int templateVersion;
    private final int questionCount;

    OmrSheetSvgDocument(
            String suggestedFileName,
            String content,
            String templateId,
            int templateVersion,
            int questionCount
    ) {
        this.suggestedFileName =
                requireText(
                        "suggestedFileName",
                        suggestedFileName
                );

        this.content =
                requireText("content", content);

        this.templateId =
                requireText("templateId", templateId);

        if (!this.suggestedFileName.endsWith(".svg")) {
            throw new IllegalArgumentException(
                    "O nome sugerido deve terminar com .svg."
            );
        }

        if (templateVersion <= 0) {
            throw new IllegalArgumentException(
                    "templateVersion deve ser positivo."
            );
        }

        if (questionCount <= 0) {
            throw new IllegalArgumentException(
                    "questionCount deve ser positivo."
            );
        }

        this.templateVersion = templateVersion;
        this.questionCount = questionCount;
    }

    private String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value;
    }

    public String getSuggestedFileName() {
        return suggestedFileName;
    }

    public String getMimeType() {
        return MIME_TYPE;
    }

    public String getContent() {
        return content;
    }

    public byte[] getUtf8Bytes() {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public String getTemplateId() {
        return templateId;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public int getQuestionCount() {
        return questionCount;
    }
}
