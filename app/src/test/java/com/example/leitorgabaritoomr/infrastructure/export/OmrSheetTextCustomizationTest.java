package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class OmrSheetTextCustomizationTest {

    @Test
    public void defaultsResolveSingleColumnTexts() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.defaults();
        assertEquals("CARTÃO-RESPOSTA", value.resolveHeaderText());
        assertEquals(
                "Marque apenas uma alternativa",
                value.resolveInstructionText(1)
        );
        assertEquals(
                "Mantenha a proporção",
                value.resolveFooterText(1)
        );
    }

    @Test
    public void defaultsResolveMultipleColumnTexts() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.defaults();
        assertEquals(
                "Preencha completamente apenas uma alternativa por questão",
                value.resolveInstructionText(2)
        );
        assertEquals(
                "Mantenha a proporção original ao redimensionar",
                value.resolveFooterText(5)
        );
    }

    @Test
    public void defaultsHaveNoOverrides() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.defaults();
        assertFalse(value.hasHeaderOverride());
        assertFalse(value.hasInstructionOverride());
        assertFalse(value.hasFooterOverride());
        assertNull(value.getHeaderOverride());
        assertNull(value.getInstructionOverride());
        assertNull(value.getFooterOverride());
    }

    @Test
    public void customTextsOverrideAllDefaults() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.custom(
                        "Minha escola",
                        "Use caneta azul",
                        "Boa prova"
                );
        assertEquals("Minha escola", value.resolveHeaderText());
        assertEquals("Use caneta azul", value.resolveInstructionText(5));
        assertEquals("Boa prova", value.resolveFooterText(1));
    }

    @Test
    public void blankOverridesHideOptionalTexts() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.custom("", "", "");
        assertTrue(value.hasHeaderOverride());
        assertTrue(value.hasInstructionOverride());
        assertTrue(value.hasFooterOverride());
        assertEquals("", value.resolveHeaderText());
        assertEquals("", value.resolveInstructionText(1));
        assertEquals("", value.resolveFooterText(2));
    }

    @Test
    public void independentOverridesKeepOtherDefaults() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.defaults()
                        .withInstructionOverride("Leia com atenção");
        assertEquals("CARTÃO-RESPOSTA", value.resolveHeaderText());
        assertEquals("Leia com atenção", value.resolveInstructionText(1));
        assertEquals(
                "Mantenha a proporção",
                value.resolveFooterText(1)
        );
    }

    @Test
    public void whitespaceIsNormalized() {
        OmrSheetTextCustomization value =
                OmrSheetTextCustomization.custom(
                        "  Escola   Ubiratan  ",
                        "Linha 1\nLinha 2",
                        "  fim "
                );
        assertEquals("Escola Ubiratan", value.resolveHeaderText());
        assertEquals("Linha 1 Linha 2", value.resolveInstructionText(1));
        assertEquals("fim", value.resolveFooterText(1));
    }

    @Test
    public void maximumLengthIsAccepted() {
        String text = repeat('x', 80);
        assertEquals(
                text,
                OmrSheetTextCustomization.custom(text, "", "")
                        .resolveHeaderText()
        );
    }

    @Test
    public void excessiveLengthIsRejected() {
        expectIllegalArgument(
                () -> OmrSheetTextCustomization.custom(
                        repeat('x', 81),
                        "",
                        ""
                )
        );
    }

    @Test
    public void nullCustomTextIsRejectedButNullOverrideMeansDefault() {
        expectIllegalArgument(
                () -> OmrSheetTextCustomization.custom(null, "", "")
        );
        assertEquals(
                "CARTÃO-RESPOSTA",
                OmrSheetTextCustomization.fromOverrides(null, null, null)
                        .resolveHeaderText()
        );
    }

    private String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
