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

public final class OmrSheetSvgPhysicalStandardV2Test {

    private static final double DELTA = 0.000001;
    private static final double PHYSICAL_DELTA = 0.002;

    private final OmrSheetSvgGenerator generator =
            new OmrSheetSvgGenerator();

    @Test
    public void metadataAndFileNamesDistinguishFourAndFiveOptions()
            throws Exception {

        OmrSheetSvgDocument fourOptions = generateFour(90);
        OmrSheetSvgDocument fiveOptions = generateFive(90);

        assertEquals(
                "cartao-resposta-090-itens-4-alternativas-v2.svg",
                fourOptions.getSuggestedFileName()
        );
        assertEquals(
                "cartao-resposta-090-itens-5-alternativas-v2.svg",
                fiveOptions.getSuggestedFileName()
        );
        assertEquals("omr-standard-ad-q090", fourOptions.getTemplateId());
        assertEquals("omr-standard-ae-q090", fiveOptions.getTemplateId());
        assertEquals(2, fourOptions.getTemplateVersion());
        assertEquals(2, fiveOptions.getTemplateVersion());

        Element fourRoot = parseSvg(
                fourOptions.getContent()
        ).getDocumentElement();

        Element fiveRoot = parseSvg(
                fiveOptions.getContent()
        ).getDocumentElement();

        assertEquals("4", fourRoot.getAttribute("data-option-count"));
        assertEquals("5", fiveRoot.getAttribute("data-option-count"));
        assertEquals("90", fourRoot.getAttribute("data-question-count"));
        assertEquals("90", fiveRoot.getAttribute("data-question-count"));
    }

    @Test
    public void everyFourOptionCountGeneratesExactRealElements() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            OmrSheetTemplateSpec spec =
                    OmrSheetTemplateCatalog
                            .standardFourOptionsV2(
                                    questionCount
                            );

            String svg = generator.generate(spec).getContent();

            assertEquals(
                    questionCount,
                    countOccurrences(
                            svg,
                            "class=\"omr-question-number\""
                    )
            );
            assertEquals(
                    questionCount * 4,
                    countOccurrences(
                            svg,
                            "class=\"omr-bubble\""
                    )
            );
            assertEquals(
                    spec.getBlockCount() * 4,
                    countOccurrences(
                            svg,
                            "class=\"omr-option-label\""
                    )
            );
            assertTrue(
                    svg.contains(
                            String.format(
                                    "data-question-id=\"question-%03d\"",
                                    questionCount
                            )
                    )
            );
            assertFalse(
                    svg.contains(
                            String.format(
                                    "data-question-id=\"question-%03d\"",
                                    questionCount + 1
                            )
                    )
            );
        }
    }

    @Test
    public void everyFiveOptionCountGeneratesExactRealElements() {
        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            OmrSheetTemplateSpec spec =
                    OmrSheetTemplateCatalog
                            .standardFiveOptionsV2(
                                    questionCount
                            );

            String svg = generator.generate(spec).getContent();

            assertEquals(
                    questionCount,
                    countOccurrences(
                            svg,
                            "class=\"omr-question-number\""
                    )
            );
            assertEquals(
                    questionCount * 5,
                    countOccurrences(
                            svg,
                            "class=\"omr-bubble\""
                    )
            );
            assertEquals(
                    spec.getBlockCount() * 5,
                    countOccurrences(
                            svg,
                            "class=\"omr-option-label\""
                    )
            );
            assertTrue(svg.contains("data-option-label=\"E\""));
        }
    }

    @Test
    public void standardV2KeepsEveryPhysicalElementSizeFixed()
            throws Exception {

        for (int questionCount : new int[]{1, 10, 11, 18, 19, 31, 90}) {
            assertFixedPhysicalMetrics(
                    generateFour(questionCount)
            );
            assertFixedPhysicalMetrics(
                    generateFive(questionCount)
            );
        }
    }

    @Test
    public void standardV2UsesExpectedPhysicalHeights()
            throws Exception {

        assertPhysicalSize(generateFour(1), 180.0, 47.460);
        assertPhysicalSize(generateFour(11), 180.0, 120.780);
        assertPhysicalSize(generateFour(18), 180.0, 172.104);
        assertPhysicalSize(generateFour(19), 180.0, 172.104);
        assertPhysicalSize(generateFour(90), 180.0, 172.104);
        assertPhysicalSize(generateFive(90), 180.0, 172.104);
    }

    @Test
    public void rowAndOptionSpacingRemainPhysicallyFixed()
            throws Exception {

        Document four = parseSvg(generateFour(90).getContent());
        Document five = parseSvg(generateFive(90).getContent());

        double fourScale = physicalScale(four);
        double fiveScale = physicalScale(five);

        Element fourA = findByAttribute(
                four,
                "circle",
                "data-option-id",
                "question-001-option-01"
        );
        Element fourB = findByAttribute(
                four,
                "circle",
                "data-option-id",
                "question-001-option-02"
        );
        Element fourNextRowA = findByAttribute(
                four,
                "circle",
                "data-option-id",
                "question-002-option-01"
        );

        Element fiveA = findByAttribute(
                five,
                "circle",
                "data-option-id",
                "question-001-option-01"
        );
        Element fiveB = findByAttribute(
                five,
                "circle",
                "data-option-id",
                "question-001-option-02"
        );

        assertEquals(
                6.0912,
                (readDouble(fourB, "cx")
                        - readDouble(fourA, "cx"))
                        * fourScale,
                PHYSICAL_DELTA
        );
        assertEquals(
                5.2452,
                (readDouble(fiveB, "cx")
                        - readDouble(fiveA, "cx"))
                        * fiveScale,
                PHYSICAL_DELTA
        );
        assertEquals(
                7.332,
                (readDouble(fourNextRowA, "cy")
                        - readDouble(fourA, "cy"))
                        * fourScale,
                PHYSICAL_DELTA
        );
    }

    @Test
    public void titleInstructionAndHeadersUseFixedV2Anchors()
            throws Exception {

        for (int questionCount : new int[]{1, 11, 18, 90}) {
            Document svg = parseSvg(
                    generateFour(questionCount).getContent()
            );

            assertEquals(
                    45.0,
                    readDouble(
                            findByAttribute(
                                    svg,
                                    "text",
                                    "class",
                                    "omr-title"
                            ),
                            "y"
                    ),
                    DELTA
            );
            assertEquals(
                    72.0,
                    readDouble(
                            findByAttribute(
                                    svg,
                                    "text",
                                    "class",
                                    "omr-instruction"
                            ),
                            "y"
                    ),
                    DELTA
            );
            assertEquals(
                    118.0,
                    readDouble(
                            findByAttribute(
                                    svg,
                                    "text",
                                    "class",
                                    "omr-option-label"
                            ),
                            "y"
                    ),
                    DELTA
            );
        }
    }

    @Test
    public void fourMarkersKeepCanonicalCornerCenters()
            throws Exception {

        for (OmrSheetSvgDocument document
                : new OmrSheetSvgDocument[]{
                generateFour(1),
                generateFour(90),
                generateFive(90)
        }) {

            Document svg = parseSvg(document.getContent());
            Element root = svg.getDocumentElement();
            double[] viewBox = parseViewBox(root);
            double canonicalWidth = 1200.0;

            double canonicalHeight =
                    viewBox[3]
                            - 2.0 * readTranslation(svg)[1];

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
    }

    @Test
    public void legacyV1RenderingRemainsUnchanged()
            throws Exception {

        OmrSheetSvgDocument document = generator.generate(
                OmrSheetTemplateCatalog
                        .compactFourOptions(10)
        );

        Document svg = parseSvg(document.getContent());
        Element root = svg.getDocumentElement();
        Element marker = findByAttribute(
                svg,
                "rect",
                "data-corner",
                "TL"
        );
        Element title = findByAttribute(
                svg,
                "text",
                "class",
                "omr-title"
        );

        assertEquals(
                "cartao-resposta-010-itens-v1.svg",
                document.getSuggestedFileName()
        );
        assertEquals("0 0 1264.800 564.800", root.getAttribute("viewBox"));
        assertEquals("180.000mm", root.getAttribute("width"));
        assertEquals("80.380mm", root.getAttribute("height"));
        assertEquals("", root.getAttribute("data-option-count"));
        assertEquals(36.0, readDouble(marker, "width"), DELTA);
        assertEquals(35.0, readDouble(title, "y"), DELTA);
    }

    @Test
    public void representativeV2DocumentsAreValidXml()
            throws Exception {

        for (int questionCount : new int[]{1, 18, 19, 36, 37, 90}) {
            assertEquals(
                    "svg",
                    parseSvg(
                            generateFour(questionCount)
                                    .getContent()
                    ).getDocumentElement().getLocalName()
            );
            assertEquals(
                    "svg",
                    parseSvg(
                            generateFive(questionCount)
                                    .getContent()
                    ).getDocumentElement().getLocalName()
            );
        }
    }

    private void assertFixedPhysicalMetrics(
            OmrSheetSvgDocument document
    ) throws Exception {
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
        Element title = findByAttribute(
                svg,
                "text",
                "class",
                "omr-title"
        );
        Element questionNumber = findByAttribute(
                svg,
                "text",
                "class",
                "omr-question-number"
        );
        Element optionLabel = findByAttribute(
                svg,
                "text",
                "class",
                "omr-option-label"
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
        assertEquals(
                3.384,
                readDouble(title, "font-size") * scale,
                PHYSICAL_DELTA
        );
        assertEquals(
                2.679,
                readDouble(questionNumber, "font-size") * scale,
                PHYSICAL_DELTA
        );
        assertEquals(
                2.397,
                readDouble(optionLabel, "font-size") * scale,
                PHYSICAL_DELTA
        );
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

    private double physicalScale(
            Document svg
    ) {
        Element root = svg.getDocumentElement();
        double widthMillimeters =
                parseMillimeters(root.getAttribute("width"));

        return widthMillimeters
                / parseViewBox(root)[2];
    }

    private double[] readTranslation(
            Document svg
    ) {
        Element layoutGroup = findByAttribute(
                svg,
                "g",
                "id",
                "omr-layout"
        );

        String transform = layoutGroup.getAttribute("transform");
        String prefix = "translate(";

        if (!transform.startsWith(prefix)
                || !transform.endsWith(")")) {

            fail("Transformacao translate invalida: " + transform);
        }

        String[] values = transform.substring(
                prefix.length(),
                transform.length() - 1
        ).trim().split("\\s+");

        if (values.length != 2) {
            fail("translate deve possuir dois valores.");
        }

        return new double[]{
                Double.parseDouble(values[0]),
                Double.parseDouble(values[1])
        };
    }

    private double[] parseViewBox(
            Element root
    ) {
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

    private double parseMillimeters(
            String value
    ) {
        if (value == null || !value.endsWith("mm")) {
            fail("Comprimento fisico invalido: " + value);
        }

        return Double.parseDouble(
                value.substring(0, value.length() - 2)
        );
    }

    private Document parseSvg(
            String svg
    ) throws Exception {
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
        NodeList elements =
                document.getElementsByTagNameNS(
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

    private OmrSheetSvgDocument generateFour(
            int questionCount
    ) {
        return generator.generate(
                OmrSheetTemplateCatalog
                        .standardFourOptionsV2(
                                questionCount
                        )
        );
    }

    private OmrSheetSvgDocument generateFive(
            int questionCount
    ) {
        return generator.generate(
                OmrSheetTemplateCatalog
                        .standardFiveOptionsV2(
                                questionCount
                        )
        );
    }
}
