package com.example.leitorgabaritoomr.infrastructure.export;

/**
 * Metadados imutaveis de um cartao-resposta gravado como PNG.
 *
 * O arquivo guarda a resolucao fisica em seu bloco pHYs. Dessa forma um
 * editor compativel consegue recuperar o tamanho de impressao sem inferir
 * uma densidade arbitraria a partir da quantidade de pixels.
 */
public final class OmrSheetPngRenderResult {

    public static final String MIME_TYPE = "image/png";

    private final String suggestedFileName;
    private final String templateId;
    private final int templateVersion;
    private final int questionCount;
    private final int widthPixels;
    private final int heightPixels;
    private final int dotsPerInch;
    private final double widthMillimeters;
    private final double heightMillimeters;

    OmrSheetPngRenderResult(
            String suggestedFileName,
            String templateId,
            int templateVersion,
            int questionCount,
            int widthPixels,
            int heightPixels,
            int dotsPerInch,
            double widthMillimeters,
            double heightMillimeters
    ) {
        this.suggestedFileName = requireText(
                "suggestedFileName",
                suggestedFileName
        );
        this.templateId = requireText("templateId", templateId);

        if (!this.suggestedFileName.endsWith(".png")) {
            throw new IllegalArgumentException(
                    "O nome sugerido deve terminar com .png."
            );
        }
        if (templateVersion <= 0
                || questionCount <= 0
                || widthPixels <= 0
                || heightPixels <= 0
                || dotsPerInch <= 0) {
            throw new IllegalArgumentException(
                    "Os metadados numericos do PNG devem ser positivos."
            );
        }
        if (!Double.isFinite(widthMillimeters)
                || widthMillimeters <= 0.0
                || !Double.isFinite(heightMillimeters)
                || heightMillimeters <= 0.0) {
            throw new IllegalArgumentException(
                    "As dimensoes fisicas do PNG devem ser positivas."
            );
        }

        this.templateVersion = templateVersion;
        this.questionCount = questionCount;
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.dotsPerInch = dotsPerInch;
        this.widthMillimeters = widthMillimeters;
        this.heightMillimeters = heightMillimeters;
    }

    private String requireText(String fieldName, String value) {
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

    public String getTemplateId() {
        return templateId;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public int getWidthPixels() {
        return widthPixels;
    }

    public int getHeightPixels() {
        return heightPixels;
    }

    public int getDotsPerInch() {
        return dotsPerInch;
    }

    public double getWidthMillimeters() {
        return widthMillimeters;
    }

    public double getHeightMillimeters() {
        return heightMillimeters;
    }
}
