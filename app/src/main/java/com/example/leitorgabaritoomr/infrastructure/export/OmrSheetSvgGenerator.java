package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.util.Locale;

/**
 * Gera um cartao-resposta vetorial a partir da mesma geometria
 * consumida pelo leitor OMR.
 *
 * O centro de cada marcador define um canto da regiao canonica.
 * Uma margem externa mantem os quatro marcadores inteiros no SVG.
 */
public final class OmrSheetSvgGenerator {

    private static final double OUTPUT_WIDTH_MILLIMETERS = 180.0;

    private static final double MARKER_SIDE_SCALE = 0.065;
    private static final double MINIMUM_MARKER_SIDE = 36.0;
    private static final double MAXIMUM_MARKER_SIDE = 64.0;
    private static final double MARKER_MARGIN_SCALE = 0.90;

    private static final int STANDARD_V2_TEMPLATE_VERSION = 2;
    private static final String STANDARD_V2_TEMPLATE_ID_PREFIX =
            "omr-standard-";
    private static final double STANDARD_V2_MARKER_SIDE_MILLIMETERS =
            6.0;
    private static final double STANDARD_PHYSICAL_SCALE_MILLIMETERS_PER_UNIT =
            (OUTPUT_WIDTH_MILLIMETERS
                    - 2.0
                    * MARKER_MARGIN_SCALE
                    * STANDARD_V2_MARKER_SIDE_MILLIMETERS)
                    / 1200.0;
    private static final double STANDARD_V2_TITLE_Y = 45.0;
    private static final double STANDARD_V2_INSTRUCTION_Y = 72.0;
    private static final double STANDARD_V2_HEADER_OFFSET_Y = 42.0;

    private static final int FLEXIBLE_V3_TEMPLATE_VERSION = 3;
    private static final int FLEXIBLE_V4_TEMPLATE_VERSION = 4;
    private static final String FLEXIBLE_V3_TEMPLATE_ID_PREFIX =
            "omr-flex-";
    private static final double FLEXIBLE_V3_TITLE_Y = 20.0;
    private static final double FLEXIBLE_V3_INSTRUCTION_Y = 44.0;

    private static final double BUBBLE_RADIUS_SCALE = 0.78;
    private static final double BUBBLE_STROKE_WIDTH = 3.2;

    public OmrSheetSvgDocument generate(
            OmrSheetTemplateSpec spec
    ) {
        return generate(
                spec,
                OmrSheetTextCustomization.defaults()
        );
    }

    public OmrSheetSvgDocument generate(
            OmrSheetTemplateSpec spec,
            OmrSheetTextCustomization textCustomization
    ) {
        if (spec == null) {
            throw new IllegalArgumentException(
                    "A especificacao do modelo e obrigatoria."
            );
        }
        if (textCustomization == null) {
            throw new IllegalArgumentException(
                    "A personalizacao dos textos e obrigatoria."
            );
        }

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        double markerSide = calculateMarkerSide(spec, layout);
        double outerMargin = markerSide * MARKER_MARGIN_SCALE;

        double viewWidth =
                layout.getCanonicalWidth()
                        + outerMargin * 2.0;

        double viewHeight =
                layout.getCanonicalHeight()
                        + outerMargin * 2.0;

        double outputWidthMillimeters =
                calculateOutputWidthMillimeters(
                        spec,
                        viewWidth
                );

        double outputHeightMillimeters =
                outputWidthMillimeters
                        * viewHeight
                        / viewWidth;

        StringBuilder svg = new StringBuilder(16384);

        appendDocumentStart(
                svg,
                spec,
                viewWidth,
                viewHeight,
                outputWidthMillimeters,
                outputHeightMillimeters
        );

        appendBackground(svg, viewWidth, viewHeight);

        svg.append("  <g id=\"omr-layout\" transform=\"translate(")
                .append(format(outerMargin))
                .append(' ')
                .append(format(outerMargin))
                .append(")\">\n");

        appendTitle(svg, spec, layout, textCustomization);
        appendBlocks(svg, spec, layout);
        appendFooter(svg, spec, layout, textCustomization);
        appendMarkers(svg, layout, markerSide);

        svg.append("  </g>\n");
        svg.append("</svg>\n");

        return new OmrSheetSvgDocument(
                createSuggestedFileName(spec),
                svg.toString(),
                spec.getTemplateId(),
                spec.getTemplateVersion(),
                spec.getQuestionCount(),
                viewWidth,
                viewHeight,
                outputWidthMillimeters,
                outputHeightMillimeters
        );
    }

    private void appendDocumentStart(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            double viewWidth,
            double viewHeight,
            double outputWidthMillimeters,
            double outputHeightMillimeters
    ) {
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .append(" width=\"")
                .append(format(outputWidthMillimeters))
                .append("mm\"")
                .append(" height=\"")
                .append(format(outputHeightMillimeters))
                .append("mm\"")
                .append(" viewBox=\"0 0 ")
                .append(format(viewWidth))
                .append(' ')
                .append(format(viewHeight))
                .append("\"")
                .append(" preserveAspectRatio=\"xMidYMid meet\"")
                .append(" data-template-id=\"")
                .append(escapeXml(spec.getTemplateId()))
                .append("\"")
                .append(" data-template-version=\"")
                .append(spec.getTemplateVersion())
                .append("\"")
                .append(" data-question-count=\"")
                .append(spec.getQuestionCount())
                .append("\"");

        if (isPhysicalStandard(spec)) {
            svg.append(" data-option-count=\"")
                    .append(spec.getOptionCount())
                    .append("\"");
        }

        if (isFlexible(spec)) {
            svg.append(" data-questions-per-column=\"")
                    .append(spec.getQuestionsPerBlock())
                    .append("\"");
        }

        svg.append(">\n");

        svg.append("  <title>")
                .append(escapeXml(spec.getTemplateName()))
                .append("</title>\n");

        svg.append("  <desc>")
                .append("Cartão-resposta OMR. Mantenha a proporção original ao redimensionar.")
                .append("</desc>\n");
    }

    private void appendBackground(
            StringBuilder svg,
            double viewWidth,
            double viewHeight
    ) {
        svg.append("  <rect class=\"omr-background\"")
                .append(" x=\"0\" y=\"0\"")
                .append(" width=\"")
                .append(format(viewWidth))
                .append("\" height=\"")
                .append(format(viewHeight))
                .append("\" fill=\"#ffffff\"/>\n");
    }

    private void appendTitle(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout,
            OmrSheetTextCustomization textCustomization
    ) {
        double centerX = layout.getCanonicalWidth() / 2.0;

        double titleY;
        double instructionY;

        if (isFlexible(spec)) {
            titleY = FLEXIBLE_V3_TITLE_Y;
            instructionY = FLEXIBLE_V3_INSTRUCTION_Y;
        } else if (isPhysicalStandard(spec)) {
            titleY = STANDARD_V2_TITLE_Y;
            instructionY = STANDARD_V2_INSTRUCTION_Y;
        } else {
            titleY = layout.getCanonicalHeight() * 0.070;
            instructionY = layout.getCanonicalHeight() * 0.105;
        }

        int columnCount = isSingleColumnFlexible(spec) ? 1 : 2;
        String header = textCustomization.resolveHeaderText();
        String instruction = textCustomization.resolveInstructionText(
                columnCount
        );
        double availableWidth = calculateAvailableTextWidth(
                spec,
                layout
        );

        if (!header.isEmpty()) {
            appendCenteredText(
                    svg,
                    "omr-title",
                    header,
                    centerX,
                    titleY,
                    24.0,
                    "700",
                    "#000000",
                    availableWidth,
                    !isFlexible(spec)
                            && !textCustomization.hasHeaderOverride()
            );
        }

        if (!instruction.isEmpty()) {
            appendCenteredText(
                    svg,
                    "omr-instruction",
                    instruction,
                    centerX,
                    instructionY,
                    15.0,
                    null,
                    "#222222",
                    availableWidth,
                    !isFlexible(spec)
                            && !textCustomization
                            .hasInstructionOverride()
            );
        }
    }

    private void appendBlocks(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout
    ) {
        int blockIndex = 0;

        for (OmrBlockDefinition block
                : layout.getBlocks()) {

            svg.append("    <g class=\"omr-block\"")
                    .append(" data-block-id=\"")
                    .append(escapeXml(block.getId()))
                    .append("\">\n");

            appendOptionHeaders(
                    svg,
                    spec,
                    layout,
                    block
            );

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                appendQuestion(
                        svg,
                        spec,
                        layout,
                        blockIndex,
                        question
                );
            }

            svg.append("    </g>\n");
            blockIndex++;
        }
    }

    private void appendOptionHeaders(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout,
            OmrBlockDefinition block
    ) {
        OmrQuestionDefinition firstQuestion =
                block.getQuestions().get(0);

        double firstRowY =
                firstQuestion.getOptions().get(0)
                        .getCenter().getY()
                        * layout.getCanonicalHeight();

        double headerY = isPhysicalStandard(spec)
                ? firstRowY
                - STANDARD_V2_HEADER_OFFSET_Y
                : Math.max(
                        layout.getCanonicalHeight() * 0.125,
                        firstRowY - 42.0
                );

        for (OmrOptionDefinition option
                : firstQuestion.getOptions()) {

            double centerX =
                    option.getCenter().getX()
                            * layout.getCanonicalWidth();

            svg.append("      <text class=\"omr-option-label\"")
                    .append(" x=\"")
                    .append(format(centerX))
                    .append("\" y=\"")
                    .append(format(headerY))
                    .append("\" text-anchor=\"middle\"")
                    .append(" font-family=\"Arial, sans-serif\"")
                    .append(" font-size=\"17\" font-weight=\"700\"")
                    .append(" fill=\"#000000\">")
                    .append(escapeXml(option.getLabel()))
                    .append("</text>\n");
        }
    }

    private void appendQuestion(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout,
            int blockIndex,
            OmrQuestionDefinition question
    ) {
        double blockWidth =
                spec.getBlockWidth()
                        * layout.getCanonicalWidth();

        double blockLeft =
                spec.getBlockLeft(blockIndex)
                        * layout.getCanonicalWidth();

        OmrOptionDefinition firstOption =
                question.getOptions().get(0);

        double firstOptionX =
                firstOption.getCenter().getX()
                        * layout.getCanonicalWidth();

        double questionLabelX = Math.max(
                blockLeft + 18.0,
                firstOptionX
                        - Math.max(42.0, blockWidth * 0.10)
        );

        double centerY =
                firstOption.getCenter().getY()
                        * layout.getCanonicalHeight();

        svg.append("      <g class=\"omr-question\"")
                .append(" data-question-id=\"")
                .append(escapeXml(question.getId()))
                .append("\">\n");

        svg.append("        <text class=\"omr-question-number\"")
                .append(" x=\"")
                .append(format(questionLabelX))
                .append("\" y=\"")
                .append(format(centerY + 6.0))
                .append("\" text-anchor=\"end\"")
                .append(" font-family=\"Arial, sans-serif\"")
                .append(" font-size=\"19\" font-weight=\"700\"")
                .append(" fill=\"#000000\">")
                .append(escapeXml(question.getLabel()))
                .append("</text>\n");

        for (OmrOptionDefinition option
                : question.getOptions()) {

            appendBubble(svg, layout, question, option);
        }

        svg.append("      </g>\n");
    }

    private void appendBubble(
            StringBuilder svg,
            OmrLayoutDefinition layout,
            OmrQuestionDefinition question,
            OmrOptionDefinition option
    ) {
        double centerX =
                option.getCenter().getX()
                        * layout.getCanonicalWidth();

        double centerY =
                option.getCenter().getY()
                        * layout.getCanonicalHeight();

        double radiusX =
                option.getSamplingRadiusX()
                        * layout.getCanonicalWidth();

        double radiusY =
                option.getSamplingRadiusY()
                        * layout.getCanonicalHeight();

        double bubbleRadius =
                Math.min(radiusX, radiusY)
                        * BUBBLE_RADIUS_SCALE;

        svg.append("        <circle class=\"omr-bubble\"")
                .append(" data-question-id=\"")
                .append(escapeXml(question.getId()))
                .append("\" data-option-id=\"")
                .append(escapeXml(option.getId()))
                .append("\" data-option-label=\"")
                .append(escapeXml(option.getLabel()))
                .append("\" cx=\"")
                .append(format(centerX))
                .append("\" cy=\"")
                .append(format(centerY))
                .append("\" r=\"")
                .append(format(bubbleRadius))
                .append("\" fill=\"#ffffff\"")
                .append(" stroke=\"#000000\"")
                .append(" stroke-width=\"")
                .append(format(BUBBLE_STROKE_WIDTH))
                .append("\"/>\n");
    }

    private void appendFooter(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout,
            OmrSheetTextCustomization textCustomization
    ) {
        double centerX = layout.getCanonicalWidth() / 2.0;
        double warningY = layout.getCanonicalHeight() - 34.0;
        double codeY = layout.getCanonicalHeight() - 12.0;

        int columnCount = isSingleColumnFlexible(spec) ? 1 : 2;
        String warning = textCustomization.resolveFooterText(
                columnCount
        );

        if (!warning.isEmpty()) {
            appendCenteredText(
                    svg,
                    "omr-resize-warning",
                    warning,
                    centerX,
                    warningY,
                    13.0,
                    null,
                    "#333333",
                    calculateAvailableTextWidth(spec, layout),
                    !isFlexible(spec)
                            && !textCustomization.hasFooterOverride()
            );
        }

        svg.append("    <text class=\"omr-template-code\"")
                .append(" x=\"")
                .append(format(centerX))
                .append("\" y=\"")
                .append(format(codeY))
                .append("\" text-anchor=\"middle\"")
                .append(" font-family=\"Arial, sans-serif\"")
                .append(" font-size=\"10\" fill=\"#555555\">")
                .append(escapeXml(spec.getTemplateId()))
                .append("@v")
                .append(spec.getTemplateVersion())
                .append("</text>\n");
    }

    private void appendCenteredText(
            StringBuilder svg,
            String cssClass,
            String text,
            double centerX,
            double y,
            double fontSize,
            String fontWeight,
            String fill,
            double availableWidth,
            boolean preserveDefaultMarkup
    ) {
        svg.append("    <text class=\"")
                .append(cssClass)
                .append("\" x=\"")
                .append(format(centerX))
                .append("\" y=\"")
                .append(format(y))
                .append("\" text-anchor=\"middle\"")
                .append(" font-family=\"Arial, sans-serif\"")
                .append(" font-size=\"")
                .append(formatFontSize(fontSize))
                .append("\"");

        if (fontWeight != null) {
            svg.append(" font-weight=\"")
                    .append(fontWeight)
                    .append("\"");
        }

        double estimatedWidth = text.length() * fontSize * 0.58;
        if (!preserveDefaultMarkup && estimatedWidth > availableWidth) {
            svg.append(" textLength=\"")
                    .append(format(availableWidth))
                    .append("\" lengthAdjust=\"spacingAndGlyphs\"");
        }

        svg.append(" fill=\"")
                .append(fill)
                .append("\">")
                .append(escapeXml(text))
                .append("</text>\n");
    }

    private double calculateAvailableTextWidth(
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout
    ) {
        double markerSide = calculateMarkerSide(spec, layout);
        return Math.max(
                80.0,
                layout.getCanonicalWidth()
                        - markerSide
                        - 16.0
        );
    }

    private String formatFontSize(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return format(value);
    }

    private void appendMarkers(
            StringBuilder svg,
            OmrLayoutDefinition layout,
            double markerSide
    ) {
        double half = markerSide / 2.0;
        double rightX = layout.getCanonicalWidth() - half;
        double bottomY = layout.getCanonicalHeight() - half;

        svg.append("    <g id=\"omr-markers\" fill=\"#000000\">\n");
        appendMarker(svg, "TL", -half, -half, markerSide);
        appendMarker(svg, "TR", rightX, -half, markerSide);
        appendMarker(svg, "BR", rightX, bottomY, markerSide);
        appendMarker(svg, "BL", -half, bottomY, markerSide);
        svg.append("    </g>\n");
    }

    private void appendMarker(
            StringBuilder svg,
            String corner,
            double x,
            double y,
            double side
    ) {
        svg.append("      <rect class=\"omr-marker\"")
                .append(" data-corner=\"")
                .append(corner)
                .append("\" x=\"")
                .append(format(x))
                .append("\" y=\"")
                .append(format(y))
                .append("\" width=\"")
                .append(format(side))
                .append("\" height=\"")
                .append(format(side))
                .append("\"/>\n");
    }

    private double calculateMarkerSide(
            OmrSheetTemplateSpec spec,
            OmrLayoutDefinition layout
    ) {
        if (isPhysicalStandard(spec)) {
            return calculatePhysicalStandardMarkerSide();
        }

        double shortestSide = Math.min(
                layout.getCanonicalWidth(),
                layout.getCanonicalHeight()
        );

        return clamp(
                shortestSide * MARKER_SIDE_SCALE,
                MINIMUM_MARKER_SIDE,
                MAXIMUM_MARKER_SIDE
        );
    }

    private double calculatePhysicalStandardMarkerSide() {
        return STANDARD_V2_MARKER_SIDE_MILLIMETERS
                / STANDARD_PHYSICAL_SCALE_MILLIMETERS_PER_UNIT;
    }

    private double calculateOutputWidthMillimeters(
            OmrSheetTemplateSpec spec,
            double viewWidth
    ) {
        if (isFlexible(spec)) {
            return viewWidth
                    * STANDARD_PHYSICAL_SCALE_MILLIMETERS_PER_UNIT;
        }

        return OUTPUT_WIDTH_MILLIMETERS;
    }

    private boolean isStandardV2(
            OmrSheetTemplateSpec spec
    ) {
        return spec.getTemplateVersion()
                == STANDARD_V2_TEMPLATE_VERSION
                && spec.getTemplateId().startsWith(
                        STANDARD_V2_TEMPLATE_ID_PREFIX
                );
    }

    private boolean isFlexible(
            OmrSheetTemplateSpec spec
    ) {
        int version = spec.getTemplateVersion();
        return (version == FLEXIBLE_V3_TEMPLATE_VERSION
                || version == FLEXIBLE_V4_TEMPLATE_VERSION)
                && spec.getTemplateId().startsWith(
                        FLEXIBLE_V3_TEMPLATE_ID_PREFIX
                );
    }

    private boolean isPhysicalStandard(
            OmrSheetTemplateSpec spec
    ) {
        return isStandardV2(spec) || isFlexible(spec);
    }

    private boolean isSingleColumnFlexible(
            OmrSheetTemplateSpec spec
    ) {
        return isFlexible(spec)
                && spec.getBlockCount() == 1;
    }

    private String createSuggestedFileName(
            OmrSheetTemplateSpec spec
    ) {
        if (isStandardV2(spec)) {
            return String.format(
                    Locale.US,
                    "cartao-resposta-%03d-itens-%d-alternativas-v%d.svg",
                    spec.getQuestionCount(),
                    spec.getOptionCount(),
                    spec.getTemplateVersion()
            );
        }

        if (isFlexible(spec)) {
            return String.format(
                    Locale.US,
                    "cartao-resposta-%03d-itens-%d-alternativas-"
                            + "%03d-por-coluna-v%d.svg",
                    spec.getQuestionCount(),
                    spec.getOptionCount(),
                    spec.getQuestionsPerBlock(),
                    spec.getTemplateVersion()
            );
        }

        return String.format(
                Locale.US,
                "cartao-resposta-%03d-itens-v%d.svg",
                spec.getQuestionCount(),
                spec.getTemplateVersion()
        );
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String format(double value) {
        return String.format(
                Locale.US,
                "%.3f",
                value
        );
    }

    private double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}
