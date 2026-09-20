package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

public final class OmrSheetSvgFlexibleV3Test {

    private static final double DELTA = 0.000001;
    private static final double PHYSICAL_DELTA = 0.002;

    private final OmrSheetSvgGenerator generator =
            new OmrSheetSvgGenerator();

    @Test
    public void physicalWidthFollowsTheOccupiedColumnCount()
            throws Exception {

        assertPhysicalSize(generate(18, 5, 18), 44.640, 172.104);
        assertPhysicalSize(generate(19, 5, 18), 78.480, 172.104);
        assertPhysicalSize(generate(37, 5, 18), 112.320, 172.104);
        assertPhysicalSize(generate(55, 5, 18), 146.160, 172.104);
        assertPhysicalSize(generate(73, 5, 18), 180.000, 172.104);
    }

    @Test
    public void physicalHeightFollowsTheSelectedRowsPerColumn()
            throws Exception {

        assertPhysicalSize(generate(1, 4, 1), 44.640, 47.460);
        assertPhysicalSize(generate(45, 4, 9), 180.000, 106.116);
        assertPhysicalSize(generate(45, 4, 15), 112.320, 150.108);
        assertPhysicalSize(generate(90, 5, 30), 112.320, 260.088);
    }

    @Test
    public void markersBubblesFontsAndStrokesKeepFixedPhysicalSizes()
            throws Exception {

        for (OmrSheetSvgDocument document
                : new OmrSheetSvgDocument[]{
                generate(2, 5, 2),
                generate(45, 5, 15),
                generate(90, 4, 30)
        }) {
            Document svg = parseSvg(document.getContent());
            double scale = physicalScale(svg);

            Element marker = findByAttribute(
                    svg,
                    "rect",
                    "data-corner",
                    "TL"
            );

            Element bubble = findByAttribute(
                    svg,
                    "circle",
                    "data-option-id",
                    "question-001-option-01"
            );

            assertEquals(
                    6.0,
                    readDouble(marker, "width") * scale,
                    PHYSICAL_DELTA
            );
            assertEquals(
                    3.95928,
                    readDouble(bubble, "r") * 2.0 * scale,
                    PHYSICAL_DELTA
            );
            assertEquals(
                    0.4512,
                    readDouble(bubble, "stroke-width") * scale,
                    PHYSICAL_DELTA
            );
            assertPhysicalFont(svg, scale, "omr-title", 3.384);
            assertPhysicalFont(
                    svg,
                    scale,
                    "omr-question-number",
                    2.679
            );
            assertPhysicalFont(
                    svg,
                    scale,
                    "omr-option-label",
                    2.397
            );
        }
    }

    @Test
    public void metadataAndFileNameIdentifyTheExactGeometry()
            throws Exception {

        OmrSheetSvgDocument document = generate(45, 5, 15);
        Element root = parseSvg(
                document.getContent()
        ).getDocumentElement();

        assertEquals(
                "cartao-resposta-045-itens-5-alternativas-"
                        + "015-por-coluna-v3.svg",
                document.getSuggestedFileName()
        );
        assertEquals(
                "omr-flex-ae-q045-r015",
                document.getTemplateId()
        );
        assertEquals(3, document.getTemplateVersion());
        assertEquals("5", root.getAttribute("data-option-count"));
        assertEquals(
                "15",
                root.getAttribute("data-questions-per-column")
        );
        assertEquals(
                "omr-flex-ae-q045-r015",
                root.getAttribute("data-template-id")
        );
        assertEquals("3", root.getAttribute("data-template-version"));
        assertEquals("45", root.getAttribute("data-question-count"));
    }

    @Test
    public void differentRowsPerColumnCannotOverwriteEachOther() {
        OmrSheetSvgDocument nineRows = generate(45, 4, 9);
        OmrSheetSvgDocument fifteenRows = generate(45, 4, 15);

        assertFalse(
                nineRows.getSuggestedFileName().equals(
                        fifteenRows.getSuggestedFileName()
                )
        );
        assertFalse(
                nineRows.getTemplateId().equals(
                        fifteenRows.getTemplateId()
                )
        );
    }

    @Test
    public void oneColumnUsesTextThatFitsTheNarrowCard()
            throws Exception {

        Document svg = parseSvg(
                generate(2, 5, 2).getContent()
        );

        Element instruction = findByAttribute(
                svg,
                "text",
                "class",
                "omr-instruction"
        );

        assertEquals(
                "Marque apenas uma alternativa",
                instruction.getTextContent()
        );
        assertEquals(
                15.0,
                readDouble(instruction, "font-size"),
                DELTA
        );
    }

    @Test
    public void twoOrMoreColumnsKeepTheCompleteInstruction()
            throws Exception {

        Document svg = parseSvg(
                generate(19, 4, 18).getContent()
        );

        Element instruction = findByAttribute(
                svg,
                "text",
                "class",
                "omr-instruction"
        );

        assertEquals(
                "Preencha completamente apenas uma alternativa por questão",
                instruction.getTextContent()
        );
    }

    @Test
    public void markerCentersSurroundOnlyTheOccupiedColumns()
            throws Exception {

        assertMarkerCenters(generate(18, 4, 18), 240.0, 1144.0);
        assertMarkerCenters(generate(45, 4, 15), 720.0, 988.0);
        assertMarkerCenters(generate(90, 5, 18), 1200.0, 1144.0);
    }

    @Test
    public void exactRealQuestionAndBubbleCountsAreRendered() {
        String svg = generate(45, 5, 15).getContent();

        assertEquals(
                45,
                countOccurrences(
                        svg,
                        "class=\"omr-question-number\""
                )
        );
        assertEquals(
                225,
                countOccurrences(svg, "class=\"omr-bubble\"")
        );
        assertEquals(
                15,
                countOccurrences(
                        svg,
                        "class=\"omr-option-label\""
                )
        );
        assertTrue(
                svg.contains("data-question-id=\"question-045\"")
        );
        assertFalse(
                svg.contains("data-question-id=\"question-046\"")
        );
    }

    @Test
    public void representativeDocumentsAreValidXml()
            throws Exception {

        for (int[] configuration : new int[][]{
                {1, 4, 1},
                {2, 5, 2},
                {45, 4, 9},
                {45, 5, 30},
                {90, 4, 18},
                {90, 5, 30}
        }) {
            Element root = parseSvg(
                    generate(
                            configuration[0],
                            configuration[1],
                            configuration[2]
                    ).getContent()
            ).getDocumentElement();

            assertEquals("svg", root.getLocalName());
        }
    }

    private OmrSheetSvgDocument generate(
            int questionCount,
            int optionCount,
            int questionsPerColumn
    ) {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        questionCount,
                        optionCount,
                        questionsPerColumn
                );

        return generator.generate(spec);
    }

    private void assertPhysicalSize(
            OmrSheetSvgDocument document,
            double expectedWidth,
            double expectedHeight
    ) throws Exception {
        Element root = parseSvg(
                document.getContent()
        ).getDocumentElement();

        assertEquals(
                expectedWidth,
                parseMillimeters(root.getAttribute("width")),
                PHYSICAL_DELTA
        );
        assertEquals(
                expectedHeight,
                parseMillimeters(root.getAttribute("height")),
                PHYSICAL_DELTA
        );
    }

    private void assertPhysicalFont(
            Document svg,
            double scale,
            String cssClass,
            double expectedMillimeters
    ) {
        Element text = findByAttribute(
                svg,
                "text",
                "class",
                cssClass
        );

        assertEquals(
                expectedMillimeters,
                readDouble(text, "font-size") * scale,
                PHYSICAL_DELTA
        );
    }

    private void assertMarkerCenters(
            OmrSheetSvgDocument document,
            double canonicalWidth,
            double canonicalHeight
    ) throws Exception {
        Document svg = parseSvg(document.getContent());

        assertMarkerCenter(svg, "TL", 0.0, 0.0);
        assertMarkerCenter(svg, "TR", canonicalWidth, 0.0);
        assertMarkerCenter(
                svg,
                "BR",
                canonicalWidth,
                canonicalHeight
        );
        assertMarkerCenter(svg, "BL", 0.0, canonicalHeight);
    }

    private void assertMarkerCenter(
            Document svg,
            String corner,
            double expectedCenterX,
            double expectedCenterY
    ) {
        Element marker = findByAttribute(
                svg,
                "rect",
                "data-corner",
                corner
        );

        double width = readDouble(marker, "width");
        double height = readDouble(marker, "height");

        assertEquals(width, height, PHYSICAL_DELTA);
        assertEquals(
                expectedCenterX,
                readDouble(marker, "x") + width / 2.0,
                PHYSICAL_DELTA
        );
        assertEquals(
                expectedCenterY,
                readDouble(marker, "y") + height / 2.0,
                PHYSICAL_DELTA
        );
    }

    private double physicalScale(Document svg) {
        Element root = svg.getDocumentElement();

        return parseMillimeters(root.getAttribute("width"))
                / parseViewBox(root)[2];
    }

    private double[] parseViewBox(Element root) {
        String[] values = root.getAttribute("viewBox")
                .trim()
                .split("\\s+");

        if (values.length != 4) {
            fail("viewBox deve possuir quatro valores.");
        }

        return new double[]{
                Double.parseDouble(values[0]),
                Double.parseDouble(values[1]),
                Double.parseDouble(values[2]),
                Double.parseDouble(values[3])
        };
    }

    private double parseMillimeters(String value) {
        if (value == null || !value.endsWith("mm")) {
            fail("Comprimento fisico invalido: " + value);
        }

        return Double.parseDouble(
                value.substring(0, value.length() - 2)
        );
    }

    private Document parseSvg(String svg) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(
                             svg.getBytes(StandardCharsets.UTF_8)
                     )) {

            return factory.newDocumentBuilder().parse(input);
        }
    }

    private Element findByAttribute(
            Document document,
            String elementName,
            String attributeName,
            String expectedValue
    ) {
        NodeList elements = document.getElementsByTagNameNS(
                "*",
                elementName
        );

        for (int index = 0;
             index < elements.getLength();
             index++) {

            Element element = (Element) elements.item(index);

            if (expectedValue.equals(
                    element.getAttribute(attributeName)
            )) {
                return element;
            }
        }

        fail(
                "Elemento "
                        + elementName
                        + " com "
                        + attributeName
                        + "="
                        + expectedValue
                        + " nao encontrado."
        );

        return null;
    }

    private double readDouble(
            Element element,
            String attributeName
    ) {
        return Double.parseDouble(
                element.getAttribute(attributeName)
        );
    }

    private int countOccurrences(
            String text,
            String fragment
    ) {
        int count = 0;
        int searchStart = 0;

        while (true) {
            int found = text.indexOf(fragment, searchStart);

            if (found < 0) {
                return count;
            }

            count++;
            searchStart = found + fragment.length();
        }
    }
}
