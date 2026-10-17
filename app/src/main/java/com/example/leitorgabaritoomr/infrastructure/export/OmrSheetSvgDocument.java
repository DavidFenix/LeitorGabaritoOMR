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
    private final double viewWidth;
    private final double viewHeight;
    private final double outputWidthMillimeters;
    private final double outputHeightMillimeters;

    OmrSheetSvgDocument(
            String suggestedFileName,
            String content,
            String templateId,
            int templateVersion,
            int questionCount,
            double viewWidth,
            double viewHeight,
            double outputWidthMillimeters,
            double outputHeightMillimeters
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

        validatePositiveFinite("viewWidth", viewWidth);
        validatePositiveFinite("viewHeight", viewHeight);
        validatePositiveFinite(
                "outputWidthMillimeters",
                outputWidthMillimeters
        );
        validatePositiveFinite(
                "outputHeightMillimeters",
                outputHeightMillimeters
        );

        this.templateVersion = templateVersion;
        this.questionCount = questionCount;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.outputWidthMillimeters = outputWidthMillimeters;
        this.outputHeightMillimeters = outputHeightMillimeters;
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

    private void validatePositiveFinite(
            String fieldName,
            double value
    ) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " deve ser positivo e finito."
            );
        }
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

    public double getViewWidth() {
        return viewWidth;
    }

    public double getViewHeight() {
        return viewHeight;
    }

    public double getOutputWidthMillimeters() {
        return outputWidthMillimeters;
    }

    public double getOutputHeightMillimeters() {
        return outputHeightMillimeters;
    }
}
