package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class OmrSheetPngRenderResultTest {

    @Test
    public void exposesImmutablePngMetadata() {
        OmrSheetPngRenderResult result = validResult();

        assertEquals("cartao.png", result.getSuggestedFileName());
        assertEquals("image/png", result.getMimeType());
        assertEquals("omr-flex-ae-q045-r009", result.getTemplateId());
        assertEquals(4, result.getTemplateVersion());
        assertEquals(45, result.getQuestionCount());
        assertEquals(1_300, result.getWidthPixels());
        assertEquals(900, result.getHeightPixels());
        assertEquals(300, result.getDotsPerInch());
        assertEquals(110.0, result.getWidthMillimeters(), 0.000001);
        assertEquals(76.2, result.getHeightMillimeters(), 0.000001);
    }

    @Test
    public void requiresPngExtension() {
        expectIllegalArgument(() -> new OmrSheetPngRenderResult(
                "cartao.svg",
                "layout",
                1,
                1,
                100,
                100,
                300,
                10.0,
                10.0
        ));
    }

    @Test
    public void rejectsInvalidNumericMetadata() {
        expectIllegalArgument(() -> new OmrSheetPngRenderResult(
                "cartao.png",
                "layout",
                1,
                1,
                0,
                100,
                300,
                10.0,
                10.0
        ));
        expectIllegalArgument(() -> new OmrSheetPngRenderResult(
                "cartao.png",
                "layout",
                1,
                1,
                100,
                100,
                300,
                Double.NaN,
                10.0
        ));
    }

    private static OmrSheetPngRenderResult validResult() {
        return new OmrSheetPngRenderResult(
                "cartao.png",
                "omr-flex-ae-q045-r009",
                4,
                45,
                1_300,
                900,
                300,
                110.0,
                76.2
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}
