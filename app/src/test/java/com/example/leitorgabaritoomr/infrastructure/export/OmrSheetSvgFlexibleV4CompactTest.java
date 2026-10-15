package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

public final class OmrSheetSvgFlexibleV4CompactTest {

    private static final double DELTA = 0.002;

    private final OmrSheetSvgGenerator generator =
            new OmrSheetSvgGenerator();

    @Test
    public void metadataAndFileNamePublishTheNewVersion()
            throws Exception {
        OmrSheetSvgDocument document = generateDefault();
        Element root = parse(document).getDocumentElement();

        assertEquals(
                "cartao-resposta-045-itens-5-alternativas-"
                        + "009-por-coluna-v4.svg",
                document.getSuggestedFileName()
        );
        assertEquals("omr-flex-ae-q045-r009",
                document.getTemplateId());
        assertEquals(4, document.getTemplateVersion());
        assertEquals("4", root.getAttribute("data-template-version"));
        assertEquals("9",
                root.getAttribute("data-questions-per-column"));
    }

    @Test
    public void markedBlankBandsAreReducedWithFixedCoordinates()
            throws Exception {
        Document svg = parse(generateDefault());

        assertEquals(44.0,
                yOf(svg, "text", "class", "omr-instruction"),
                DELTA);
        assertEquals(68.0,
                yOf(svg, "text", "class", "omr-option-label"),
                DELTA);
        assertEquals(110.0,
                yOf(svg, "circle", "data-option-id",
                        "question-001-option-01"),
                DELTA);
        assertEquals(526.0,
                yOf(svg, "circle", "data-option-id",
                        "question-009-option-01"),
                DELTA);
        assertEquals(562.0,
                yOf(svg, "text", "class", "omr-resize-warning"),
                DELTA);
        assertEquals(584.0,
                yOf(svg, "text", "class", "omr-template-code"),
                DELTA);
        assertMarkerCenterY(svg, "BL", 596.0);
        assertMarkerCenterY(svg, "BR", 596.0);
    }

    @Test
    public void physicalScaleAndOmrElementSizesRemainUnchanged()
            throws Exception {
        Document v3 = parse(generator.generate(
                OmrSheetTemplateCatalog.flexibleOptionsV3(
                        45,
                        5,
                        9
                )
        ));
        Document v4 = parse(generateDefault());

        Element v3Root = v3.getDocumentElement();
        Element v4Root = v4.getDocumentElement();
        Element v3Marker = find(v3, "rect", "data-corner", "TL");
        Element v4Marker = find(v4, "rect", "data-corner", "TL");
        Element v3Bubble = find(v3, "circle", "data-option-id",
                "question-001-option-01");
        Element v4Bubble = find(v4, "circle", "data-option-id",
                "question-001-option-01");

        assertEquals(read(v3Root, "width"), read(v4Root, "width"), DELTA);
        assertTrue(read(v4Root, "height") < read(v3Root, "height"));
        assertEquals(read(v3Marker, "width"),
                read(v4Marker, "width"), DELTA);
        assertEquals(read(v3Bubble, "r"),
                read(v4Bubble, "r"), DELTA);
        assertEquals(read(v3Bubble, "stroke-width"),
                read(v4Bubble, "stroke-width"), DELTA);
    }

    @Test
    public void optionalTextsNeverChangeBubbleGeometry()
            throws Exception {
        Document defaults = parse(generateDefault());
        Document custom = parse(generator.generate(
                compactSpec(),
                OmrSheetTextCustomization.custom(
                        "AVALIAÇÃO BIMESTRAL",
                        "Pinte somente uma opção",
                        "Boa prova"
                )
        ));
        Document blank = parse(generator.generate(
                compactSpec(),
                OmrSheetTextCustomization.custom("", "", "")
        ));

        for (String optionId : new String[]{
                "question-001-option-01",
                "question-009-option-05",
                "question-045-option-05"
        }) {
            Element expected = find(defaults, "circle",
                    "data-option-id", optionId);
            Element customized = find(custom, "circle",
                    "data-option-id", optionId);
            Element withoutText = find(blank, "circle",
                    "data-option-id", optionId);

            assertEquals(read(expected, "cx"),
                    read(customized, "cx"), DELTA);
            assertEquals(read(expected, "cy"),
                    read(customized, "cy"), DELTA);
            assertEquals(read(expected, "cx"),
                    read(withoutText, "cx"), DELTA);
            assertEquals(read(expected, "cy"),
                    read(withoutText, "cy"), DELTA);
        }

        assertFalse(blank.getDocumentElement().getTextContent()
                .contains("CARTÃO-RESPOSTA"));
        assertTrue(blank.getDocumentElement().getTextContent()
                .contains("omr-flex-ae-q045-r009@v4"));
    }

    @Test
    public void compactCardRemainsValidXmlForRepresentativeLayouts()
            throws Exception {
        for (int[] configuration : new int[][]{
                {1, 4, 1},
                {10, 5, 2},
                {45, 4, 9},
                {45, 5, 15},
                {90, 4, 18},
                {90, 5, 30}
        }) {
            OmrSheetSvgDocument document = generator.generate(
                    OmrSheetTemplateCatalog.flexibleOptionsV4(
                            configuration[0],
                            configuration[1],
                            configuration[2]
                    )
            );

            assertEquals("svg",
                    parse(document).getDocumentElement().getLocalName());
        }
    }

    private OmrSheetSvgDocument generateDefault() {
        return generator.generate(compactSpec());
    }

    private OmrSheetTemplateSpec compactSpec() {
        return OmrSheetTemplateCatalog.flexibleOptionsV4(
                45,
                5,
                9
        );
    }

    private Document parse(OmrSheetSvgDocument document)
            throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(
                        document.getContent().getBytes(
                                StandardCharsets.UTF_8
                        )
                )
        );
    }

    private double yOf(
            Document document,
            String tag,
            String attribute,
            String value
    ) {
        return read(find(document, tag, attribute, value),
                tag.equals("circle") ? "cy" : "y");
    }

    private void assertMarkerCenterY(
            Document document,
            String corner,
            double expected
    ) {
        Element marker = find(document, "rect", "data-corner", corner);
        assertEquals(expected,
                read(marker, "y") + read(marker, "height") / 2.0,
                DELTA);
    }

    private Element find(
            Document document,
            String tag,
            String attribute,
            String value
    ) {
        NodeList nodes = document.getElementsByTagNameNS("*", tag);
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (value.equals(element.getAttribute(attribute))) {
                return element;
            }
        }
        throw new AssertionError("Elemento SVG não encontrado: " + value);
    }

    private double read(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        if (value.endsWith("mm")) {
            value = value.substring(0, value.length() - 2);
        }
        return Double.parseDouble(value);
    }
}
