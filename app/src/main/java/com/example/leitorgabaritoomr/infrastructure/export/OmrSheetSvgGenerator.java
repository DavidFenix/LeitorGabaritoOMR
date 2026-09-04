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

    private static final double BUBBLE_RADIUS_SCALE = 0.78;
    private static final double BUBBLE_STROKE_WIDTH = 3.2;

    public OmrSheetSvgDocument generate(
            OmrSheetTemplateSpec spec
    ) {
        if (spec == null) {
            throw new IllegalArgumentException(
                    "A especificacao do modelo e obrigatoria."
            );
        }

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        double markerSide = calculateMarkerSide(layout);
        double outerMargin = markerSide * MARKER_MARGIN_SCALE;

        double viewWidth =
                layout.getCanonicalWidth()
                        + outerMargin * 2.0;

        double viewHeight =
                layout.getCanonicalHeight()
                        + outerMargin * 2.0;

        double outputHeightMillimeters =
                OUTPUT_WIDTH_MILLIMETERS
                        * viewHeight
                        / viewWidth;

        StringBuilder svg = new StringBuilder(16384);

        appendDocumentStart(
                svg,
                spec,
                viewWidth,
                viewHeight,
                outputHeightMillimeters
        );

        appendBackground(svg, viewWidth, viewHeight);

        svg.append("  <g id=\"omr-layout\" transform=\"translate(")
                .append(format(outerMargin))
                .append(' ')
                .append(format(outerMargin))
                .append(")\">\n");

        appendTitle(svg, layout);
        appendBlocks(svg, layout);
        appendFooter(svg, spec, layout);
        appendMarkers(svg, layout, markerSide);

        svg.append("  </g>\n");
        svg.append("</svg>\n");

        return new OmrSheetSvgDocument(
                createSuggestedFileName(spec),
                svg.toString(),
                spec.getTemplateId(),
                spec.getTemplateVersion(),
                spec.getQuestionCount()
        );
    }

    private void appendDocumentStart(
            StringBuilder svg,
            OmrSheetTemplateSpec spec,
            double viewWidth,
            double viewHeight,
            double outputHeightMillimeters
    ) {
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .append(" width=\"")
                .append(format(OUTPUT_WIDTH_MILLIMETERS))
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
                .append("\">\n");

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
            OmrLayoutDefinition layout
    ) {
        double centerX = layout.getCanonicalWidth() / 2.0;
        double titleY = layout.getCanonicalHeight() * 0.070;
        double instructionY = layout.getCanonicalHeight() * 0.105;

        svg.append("    <text class=\"omr-title\"")
                .append(" x=\"")
                .append(format(centerX))
                .append("\" y=\"")
                .append(format(titleY))
                .append("\" text-anchor=\"middle\"")
                .append(" font-family=\"Arial, sans-serif\"")
                .append(" font-size=\"24\" font-weight=\"700\"")
                .append(" fill=\"#000000\">CARTÃO-RESPOSTA</text>\n");

        svg.append("    <text class=\"omr-instruction\"")
                .append(" x=\"")
                .append(format(centerX))
                .append("\" y=\"")
                .append(format(instructionY))
                .append("\" text-anchor=\"middle\"")
                .append(" font-family=\"Arial, sans-serif\"")
                .append(" font-size=\"15\" fill=\"#222222\">")
                .append("Preencha completamente apenas uma alternativa por questão")
                .append("</text>\n");
    }

    private void appendBlocks(
            StringBuilder svg,
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
                    layout,
                    block
            );

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                appendQuestion(
                        svg,
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
            OmrLayoutDefinition layout,
            OmrBlockDefinition block
    ) {
        OmrQuestionDefinition firstQuestion =
                block.getQuestions().get(0);

        double firstRowY =
                firstQuestion.getOptions().get(0)
                        .getCenter().getY()
                        * layout.getCanonicalHeight();

        double headerY = Math.max(
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
            OmrLayoutDefinition layout,
            int blockIndex,
            OmrQuestionDefinition question
    ) {
        double blockWidth =
                layout.getCanonicalWidth()
                        / (double) layout.getBlockCount();

        double blockLeft = blockIndex * blockWidth;

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
            OmrLayoutDefinition layout
    ) {
        double centerX = layout.getCanonicalWidth() / 2.0;
        double warningY = layout.getCanonicalHeight() - 34.0;
        double codeY = layout.getCanonicalHeight() - 12.0;

        svg.append("    <text class=\"omr-resize-warning\"")
                .append(" x=\"")
                .append(format(centerX))
                .append("\" y=\"")
                .append(format(warningY))
                .append("\" text-anchor=\"middle\"")
                .append(" font-family=\"Arial, sans-serif\"")
                .append(" font-size=\"13\" fill=\"#333333\">")
                .append("Mantenha a proporção original ao redimensionar")
                .append("</text>\n");

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
            OmrLayoutDefinition layout
    ) {
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

    private String createSuggestedFileName(
            OmrSheetTemplateSpec spec
    ) {
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
