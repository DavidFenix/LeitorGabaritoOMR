package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;

public final class OmrSheetSvgTextCustomizationTest {

    private final OmrSheetSvgGenerator generator =
            new OmrSheetSvgGenerator();

    @Test
    public void originalOverloadUsesDefaultTexts() {
        OmrSheetTemplateSpec spec = flexible(10, 4, 10);
        assertEquals(
                generator.generate(spec).getContent(),
                generator.generate(
                        spec,
                        OmrSheetTextCustomization.defaults()
                ).getContent()
        );
    }

    @Test
    public void customTextsAreEscapedInXml() {
        String svg = generator.generate(
                flexible(45, 5, 15),
                OmrSheetTextCustomization.custom(
                        "Escola & Turma <A>",
                        "Marque A > B",
                        "Professor \"David\""
                )
        ).getContent();

        assertTrue(svg.contains("Escola &amp; Turma &lt;A&gt;"));
        assertTrue(svg.contains("Marque A &gt; B"));
        assertTrue(svg.contains("Professor &quot;David&quot;"));
    }

    @Test
    public void blankOptionalTextsKeepTechnicalModelId() {
        OmrSheetTemplateSpec spec = flexible(45, 5, 15);
        String svg = generator.generate(
                spec,
                OmrSheetTextCustomization.custom("", "", "")
        ).getContent();

        assertFalse(svg.contains("class=\"omr-title\""));
        assertFalse(svg.contains("class=\"omr-instruction\""));
        assertFalse(svg.contains("class=\"omr-resize-warning\""));
        assertTrue(svg.contains("class=\"omr-template-code\""));
        assertTrue(svg.contains(spec.getTemplateId() + "@v3"));
    }

    @Test
    public void flexibleHeaderAndInstructionUseTopMarkerBand() {
        String svg = generator.generate(
                flexible(10, 4, 10)
        ).getContent();
        assertTrue(textElement(svg, "omr-title").contains("y=\"20.000\""));
        assertTrue(textElement(svg, "omr-title").contains("textLength="));
        assertTrue(
                textElement(svg, "omr-instruction")
                        .contains("y=\"44.000\"")
        );
    }

    @Test
    public void legacyV2TextAnchorsRemainUnchanged() {
        String svg = generator.generate(
                OmrSheetTemplateCatalog.standardFourOptionsV2(10)
        ).getContent();
        assertTrue(textElement(svg, "omr-title").contains("y=\"45.000\""));
        assertTrue(
                textElement(svg, "omr-instruction")
                        .contains("y=\"72.000\"")
        );
    }

    @Test
    public void longCustomTextIsFittedInsideMarkerBand() {
        String svg = generator.generate(
                flexible(10, 4, 10),
                OmrSheetTextCustomization.custom(
                        "ESCOLA ESTADUAL DE ENSINO MEDIO EM TEMPO INTEGRAL DEPUTADO UBIRATAN DINIZ",
                        "Leia atentamente antes de preencher cada alternativa do cartão resposta",
                        ""
                )
        ).getContent();
        assertTrue(textElement(svg, "omr-title").contains("textLength="));
        assertTrue(
                textElement(svg, "omr-instruction")
                        .contains("lengthAdjust=\"spacingAndGlyphs\"")
        );
    }

    @Test
    public void customizationDoesNotChangeMarkerOrBubbleGeometry() {
        OmrSheetTemplateSpec spec = flexible(45, 5, 15);
        String baseline = generator.generate(spec).getContent();
        String customized = generator.generate(
                spec,
                OmrSheetTextCustomization.custom(
                        "Escola",
                        "Instrução",
                        "Rodapé"
                )
        ).getContent();

        assertEquals(
                elementWithAttribute(
                        baseline,
                        "rect",
                        "data-corner=\"TL\""
                ),
                elementWithAttribute(
                        customized,
                        "rect",
                        "data-corner=\"TL\""
                )
        );
        assertEquals(
                elementWithAttribute(
                        baseline,
                        "circle",
                        "data-option-id=\"question-001-option-01\""
                ),
                elementWithAttribute(
                        customized,
                        "circle",
                        "data-option-id=\"question-001-option-01\""
                )
        );
    }

    @Test
    public void nullCustomizationIsRejected() {
        try {
            generator.generate(flexible(10, 4, 10), null);
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("personalizacao"));
        }
    }

    private OmrSheetTemplateSpec flexible(
            int questions,
            int options,
            int rows
    ) {
        return OmrSheetTemplateCatalog.flexibleOptionsV3(
                questions,
                options,
                rows
        );
    }

    private String textElement(String svg, String cssClass) {
        return elementWithAttribute(
                svg,
                "text",
                "class=\"" + cssClass + "\""
        );
    }

    private String elementWithAttribute(
            String svg,
            String elementName,
            String attribute
    ) {
        int attributeIndex = svg.indexOf(attribute);
        if (attributeIndex < 0) {
            fail("Elemento nao encontrado: " + attribute);
        }
        int start = svg.lastIndexOf("<" + elementName, attributeIndex);
        int selfClosingEnd = svg.indexOf("/>", attributeIndex);
        int closingEnd = svg.indexOf(
                "</" + elementName + ">",
                attributeIndex
        );
        int end;
        if (selfClosingEnd >= 0
                && (closingEnd < 0 || selfClosingEnd < closingEnd)) {
            end = selfClosingEnd + 2;
        } else {
            end = closingEnd + elementName.length() + 3;
        }
        return svg.substring(start, end);
    }
}
