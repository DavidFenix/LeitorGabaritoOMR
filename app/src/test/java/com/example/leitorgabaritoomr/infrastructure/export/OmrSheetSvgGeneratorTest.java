package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

public final class OmrSheetSvgGeneratorTest {

    private static final double DELTA = 0.000001;

    private final OmrSheetSvgGenerator generator =
            new OmrSheetSvgGenerator();

    @Test
    public void tenQuestionDocumentExposesStableMetadata() {
        OmrSheetSvgDocument document = generateCompact(10);

        assertEquals(
                "cartao-resposta-010-itens-v1.svg",
                document.getSuggestedFileName()
        );
        assertEquals("image/svg+xml", document.getMimeType());
        assertEquals("omr-compact-ad-q010", document.getTemplateId());
        assertEquals(1, document.getTemplateVersion());
        assertEquals(10, document.getQuestionCount());

        assertTrue(
                document.getContent().startsWith(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                )
        );
        assertTrue(
                document.getContent().contains(
                        "data-template-id=\"omr-compact-ad-q010\""
                )
        );
        assertTrue(
                document.getContent().contains(
                        "data-question-count=\"10\""
                )
        );
        assertTrue(
                document.getContent().contains(
                        "preserveAspectRatio=\"xMidYMid meet\""
                )
        );
    }

    @Test
    public void svgContainsExactlyFourOrderedCornerMarkers() {
        String svg = generateCompact(10).getContent();

        assertEquals(
                4,
                countOccurrences(svg, "class=\"omr-marker\"")
        );

        int topLeft = svg.indexOf("data-corner=\"TL\"");
        int topRight = svg.indexOf("data-corner=\"TR\"");
        int bottomRight = svg.indexOf("data-corner=\"BR\"");
        int bottomLeft = svg.indexOf("data-corner=\"BL\"");

        assertTrue(topLeft >= 0);
        assertTrue(topRight > topLeft);
        assertTrue(bottomRight > topRight);
        assertTrue(bottomLeft > bottomRight);
    }

    @Test
    public void tenQuestionSvgContainsTenRowsAndFortyBubbles() {
        String svg = generateCompact(10).getContent();

        assertEquals(
                10,
                countOccurrences(
                        svg,
                        "class=\"omr-question-number\""
                )
        );
        assertEquals(
                40,
                countOccurrences(svg, "class=\"omr-bubble\"")
        );
        assertEquals(
                8,
                countOccurrences(
                        svg,
                        "class=\"omr-option-label\""
                )
        );
        assertTrue(svg.contains("data-question-id=\"question-010\""));
        assertTrue(svg.contains("data-option-label=\"D\""));
    }

    @Test
    public void oneQuestionSvgContainsOnlyItsRealElements() {
        String svg = generateCompact(1).getContent();

        assertEquals(
                1,
                countOccurrences(
                        svg,
                        "class=\"omr-question-number\""
                )
        );
        assertEquals(
                4,
                countOccurrences(svg, "class=\"omr-bubble\"")
        );
        assertEquals(
                4,
                countOccurrences(
                        svg,
                        "class=\"omr-option-label\""
                )
        );
        assertTrue(svg.contains("data-question-id=\"question-001\""));
        assertFalse(svg.contains("data-question-id=\"question-002\""));
    }

    @Test
    public void everyCompactCountGeneratesExactRowsAndBubbles() {
        for (int questionCount = 1;
             questionCount <= 10;
             questionCount++) {

            String svg =
                    generateCompact(questionCount)
                            .getContent();

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
        }
    }

    @Test
    public void firstBubbleUsesTheSameCanonicalCoordinatesAsLayout()
            throws Exception {

        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog
                        .compactFourOptions(10);

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        Document svg = parseSvg(
                generator.generate(spec).getContent()
        );

        assertBubbleMatchesLayout(
                svg,
                layout,
                "question-001-option-01"
        );

        assertBubbleMatchesLayout(
                svg,
                layout,
                "question-006-option-01"
        );
    }

    @Test
    public void markerCentersDefineTheCanonicalRectangle()
            throws Exception {

        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog
                        .compactFourOptions(10);

        OmrLayoutDefinition layout =
                OmrDynamicLayoutFactory.create(spec);

        Document svg = parseSvg(
                generator.generate(spec).getContent()
        );

        Element layoutGroup = findElementByAttribute(
                svg,
                "g",
                "id",
                "omr-layout"
        );

        double[] translation = parseTranslation(
                layoutGroup.getAttribute("transform")
        );

        Element topLeft = findMarker(svg, "TL");
        Element topRight = findMarker(svg, "TR");
        Element bottomRight = findMarker(svg, "BR");
        Element bottomLeft = findMarker(svg, "BL");

        assertMarkerCenter(topLeft, 0.0, 0.0);
        assertMarkerCenter(
                topRight,
                layout.getCanonicalWidth(),
                0.0
        );
        assertMarkerCenter(
                bottomRight,
                layout.getCanonicalWidth(),
                layout.getCanonicalHeight()
        );
        assertMarkerCenter(
                bottomLeft,
                0.0,
                layout.getCanonicalHeight()
        );

        double markerHalfSide =
                readDouble(topLeft, "width") / 2.0;

        assertTrue(translation[0] > markerHalfSide);
        assertTrue(translation[1] > markerHalfSide);
    }

    @Test
    public void dynamicTextIsEscapedForValidXml() {
        OmrSheetTemplateSpec spec =
                new OmrSheetTemplateSpec(
                        "modelo-\"<&",
                        1,
                        "Nome <&> do modelo",
                        1,
                        1200,
                        700,
                        5,
                        new String[]{"A&", "B<"},
                        new double[]{0.35, 0.65},
                        0.20,
                        0.15,
                        0.015,
                        0.026,
                        1
                );

        String svg = generator.generate(spec).getContent();

        assertTrue(
                svg.contains(
                        "data-template-id=\"modelo-&quot;&lt;&amp;\""
                )
        );
        assertTrue(svg.contains("Nome &lt;&amp;&gt; do modelo"));
        assertTrue(svg.contains(">A&amp;</text>"));
        assertTrue(svg.contains(">B&lt;</text>"));
    }

    @Test
    public void utf8BytesRepresentContentAndAreIndependent() {
        OmrSheetSvgDocument document = generateCompact(10);

        byte[] firstCopy = document.getUtf8Bytes();
        byte[] secondCopy = document.getUtf8Bytes();

        firstCopy[0] = 0;

        assertEquals(
                document.getContent(),
                new String(secondCopy, StandardCharsets.UTF_8)
        );
        assertTrue(firstCopy[0] != secondCopy[0]);
    }

    @Test
    public void generatorRejectsNullSpec() {
        try {
            generator.generate(null);
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private OmrSheetSvgDocument generateCompact(
            int questionCount
    ) {
        return generator.generate(
                OmrSheetTemplateCatalog
                        .compactFourOptions(questionCount)
        );
    }

    private Document parseSvg(String svg)
            throws Exception {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(
                             svg.getBytes(StandardCharsets.UTF_8)
                     )) {

            return factory.newDocumentBuilder()
                    .parse(input);
        }
    }

    private void assertBubbleMatchesLayout(
            Document svg,
            OmrLayoutDefinition layout,
            String optionId
    ) {
        OmrOptionDefinition option =
                layout.findOptionById(optionId);

        assertTrue(option != null);

        Element circle = findElementByAttribute(
                svg,
                "circle",
                "data-option-id",
                optionId
        );

        assertEquals(
                option.getCenter().getX()
                        * layout.getCanonicalWidth(),
                readDouble(circle, "cx"),
                DELTA
        );

        assertEquals(
                option.getCenter().getY()
                        * layout.getCanonicalHeight(),
                readDouble(circle, "cy"),
                DELTA
        );

        double radius = readDouble(circle, "r");

        double samplingRadius = Math.min(
                option.getSamplingRadiusX()
                        * layout.getCanonicalWidth(),
                option.getSamplingRadiusY()
                        * layout.getCanonicalHeight()
        );

        assertTrue(radius > 0.0);
        assertTrue(radius < samplingRadius);
    }

    private Element findMarker(
            Document svg,
            String corner
    ) {
        return findElementByAttribute(
                svg,
                "rect",
                "data-corner",
                corner
        );
    }

    private void assertMarkerCenter(
            Element marker,
            double expectedCenterX,
            double expectedCenterY
    ) {
        double width = readDouble(marker, "width");
        double height = readDouble(marker, "height");

        assertEquals(width, height, DELTA);
        assertTrue(width > 0.0);

        assertEquals(
                expectedCenterX,
                readDouble(marker, "x") + width / 2.0,
                DELTA
        );

        assertEquals(
                expectedCenterY,
                readDouble(marker, "y") + height / 2.0,
                DELTA
        );
    }

    private Element findElementByAttribute(
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

            Element element =
                    (Element) elements.item(index);

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

    private double[] parseTranslation(
            String transform
    ) {
        String prefix = "translate(";

        if (transform == null
                || !transform.startsWith(prefix)
                || !transform.endsWith(")")) {

            fail("Transformacao translate invalida: " + transform);
        }

        String valuesText = transform.substring(
                prefix.length(),
                transform.length() - 1
        );

        String[] values =
                valuesText.trim().split("\\s+");

        if (values.length != 2) {
            fail("translate deve possuir dois valores.");
        }

        return new double[]{
                Double.parseDouble(values[0]),
                Double.parseDouble(values[1])
        };
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
            int found = text.indexOf(
                    fragment,
                    searchStart
            );

            if (found < 0) {
                return count;
            }

            count++;
            searchStart = found + fragment.length();
        }
    }
}
