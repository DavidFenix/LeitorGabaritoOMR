package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class OmrSheetPngRendererInstrumentedTest {

    private static final double DELTA = 0.001;

    private final OmrSheetSvgGenerator svgGenerator =
            new OmrSheetSvgGenerator();

    @Test
    public void writesDecodablePngWithStableMetadata()
            throws Exception {
        OmrSheetSvgDocument source = svgGenerator.generate(
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        5,
                        9
                )
        );
        ByteArrayOutputStream destination =
                new ByteArrayOutputStream();

        OmrSheetPngRenderResult result =
                new OmrSheetPngRenderer().write(
                        source,
                        destination
                );

        byte[] png = destination.toByteArray();
        assertTrue(png.length > 1_000);
        assertTrue(Arrays.equals(
                new byte[]{
                        (byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A
                },
                Arrays.copyOf(png, 8)
        ));

        Bitmap decoded = BitmapFactory.decodeByteArray(
                png,
                0,
                png.length
        );
        assertNotNull(decoded);
        try {
            assertEquals(result.getWidthPixels(), decoded.getWidth());
            assertEquals(result.getHeightPixels(), decoded.getHeight());
        } finally {
            decoded.recycle();
        }

        assertEquals(
                "cartao-resposta-045-itens-5-alternativas-"
                        + "009-por-coluna-v4.png",
                result.getSuggestedFileName()
        );
        assertEquals("image/png", result.getMimeType());
        assertEquals(source.getTemplateId(), result.getTemplateId());
        assertEquals(4, result.getTemplateVersion());
        assertEquals(45, result.getQuestionCount());
        assertEquals(300, result.getDotsPerInch());
        assertEquals(
                source.getOutputWidthMillimeters(),
                result.getWidthMillimeters(),
                DELTA
        );
        assertEquals(
                source.getOutputHeightMillimeters(),
                result.getHeightMillimeters(),
                DELTA
        );
        assertEquals(
                pixels(source.getOutputWidthMillimeters(), 300),
                result.getWidthPixels()
        );
        assertEquals(
                pixels(source.getOutputHeightMillimeters(), 300),
                result.getHeightPixels()
        );
    }

    @Test
    public void embedsPhysicalResolutionAtThreeHundredDpi()
            throws Exception {
        byte[] png = render(
                OmrSheetTemplateCatalog.flexibleOptionsV4(10, 4)
        );
        int[] physical = findPhysicalResolution(png);

        assertNotNull(physical);
        assertEquals(11_811, physical[0]);
        assertEquals(11_811, physical[1]);
        assertEquals(1, physical[2]);
        assertEquals(1, countChunks(png, "pHYs"));
    }

    @Test
    public void preservesWhiteBackgroundAndFourDarkMarkers()
            throws Exception {
        byte[] png = render(
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        5,
                        9
                )
        );
        Bitmap bitmap = BitmapFactory.decodeByteArray(
                png,
                0,
                png.length
        );
        assertNotNull(bitmap);
        try {
            assertWhite(bitmap.getPixel(0, 0));
            assertWhite(bitmap.getPixel(
                    bitmap.getWidth() - 1,
                    bitmap.getHeight() - 1
            ));

            int horizontalBand = Math.max(1, bitmap.getWidth() / 6);
            int verticalBand = Math.max(1, bitmap.getHeight() / 6);

            assertTrue(countDark(
                    bitmap,
                    0,
                    0,
                    horizontalBand,
                    verticalBand
            ) > 500);
            assertTrue(countDark(
                    bitmap,
                    bitmap.getWidth() - horizontalBand,
                    0,
                    bitmap.getWidth(),
                    verticalBand
            ) > 500);
            assertTrue(countDark(
                    bitmap,
                    bitmap.getWidth() - horizontalBand,
                    bitmap.getHeight() - verticalBand,
                    bitmap.getWidth(),
                    bitmap.getHeight()
            ) > 500);
            assertTrue(countDark(
                    bitmap,
                    0,
                    bitmap.getHeight() - verticalBand,
                    horizontalBand,
                    bitmap.getHeight()
            ) > 500);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void rendersRepresentativePublishedLayouts()
            throws Exception {
        int[][] configurations = new int[][]{
                {1, 4, 1},
                {10, 5, 2},
                {45, 4, 15},
                {90, 5, 30}
        };

        for (int[] configuration : configurations) {
            OmrSheetTemplateSpec spec =
                    OmrSheetTemplateCatalog.flexibleOptionsV4(
                            configuration[0],
                            configuration[1],
                            configuration[2]
                    );
            byte[] png = render(spec);
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    png,
                    0,
                    png.length
            );
            assertNotNull(bitmap);
            try {
                assertTrue(bitmap.getWidth() > 100);
                assertTrue(bitmap.getHeight() > 100);
                assertTrue((long) bitmap.getWidth()
                        * bitmap.getHeight() <= 8_000_000L);
            } finally {
                bitmap.recycle();
            }
        }
    }

    @Test
    public void customAndBlankTextsKeepRasterDimensions()
            throws Exception {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog.flexibleOptionsV4(
                        45,
                        5,
                        9
                );
        OmrSheetSvgDocument defaults = svgGenerator.generate(spec);
        OmrSheetSvgDocument customized = svgGenerator.generate(
                spec,
                OmrSheetTextCustomization.custom(
                        "AVALIAÇÃO & CIÊNCIAS",
                        "Preencha somente uma opção",
                        "Turma 3ª série"
                )
        );
        OmrSheetSvgDocument blank = svgGenerator.generate(
                spec,
                OmrSheetTextCustomization.custom("", "", "")
        );

        OmrSheetPngRenderResult defaultResult = write(defaults);
        OmrSheetPngRenderResult customResult = write(customized);
        OmrSheetPngRenderResult blankResult = write(blank);

        assertEquals(defaultResult.getWidthPixels(),
                customResult.getWidthPixels());
        assertEquals(defaultResult.getHeightPixels(),
                customResult.getHeightPixels());
        assertEquals(defaultResult.getWidthPixels(),
                blankResult.getWidthPixels());
        assertEquals(defaultResult.getHeightPixels(),
                blankResult.getHeightPixels());
    }

    @Test
    public void rendererDoesNotCloseCallerOutputStream()
            throws Exception {
        TrackingOutputStream destination =
                new TrackingOutputStream();
        new OmrSheetPngRenderer().write(
                svgGenerator.generate(
                        OmrSheetTemplateCatalog.flexibleOptionsV4(
                                4,
                                4
                        )
                ),
                destination
        );

        assertFalse(destination.closed);
        destination.write(0x7F);
        assertTrue(destination.size() > 1_000);
    }

    @Test
    public void rejectsMissingInputsAndUnsafeDensity() {
        expectIllegalArgument(() ->
                new OmrSheetPngRenderer(149)
        );
        expectIllegalArgument(() ->
                new OmrSheetPngRenderer(301)
        );
        expectIllegalArgument(() ->
                new OmrSheetPngRenderer().write(
                        null,
                        new ByteArrayOutputStream()
                )
        );
        expectIllegalArgument(() ->
                new OmrSheetPngRenderer().write(
                        svgGenerator.generate(
                                OmrSheetTemplateCatalog
                                        .flexibleOptionsV4(1, 4)
                        ),
                        null
                )
        );
    }

    @Test
    public void rejectsUnsupportedSvgInsteadOfSilentlyChangingIt()
            throws Exception {
        OmrSheetSvgDocument valid = svgGenerator.generate(
                OmrSheetTemplateCatalog.flexibleOptionsV4(1, 4)
        );
        String unsupported = valid.getContent().replaceFirst(
                "transform=\"translate\\([^\"]+\\)\"",
                "transform=\"scale(2)\""
        );
        OmrSheetSvgDocument source = new OmrSheetSvgDocument(
                valid.getSuggestedFileName(),
                unsupported,
                valid.getTemplateId(),
                valid.getTemplateVersion(),
                valid.getQuestionCount(),
                valid.getViewWidth(),
                valid.getViewHeight(),
                valid.getOutputWidthMillimeters(),
                valid.getOutputHeightMillimeters()
        );

        try {
            new OmrSheetPngRenderer().write(
                    source,
                    new ByteArrayOutputStream()
            );
            fail("Era esperada IOException.");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    private OmrSheetPngRenderResult write(
            OmrSheetSvgDocument source
    ) throws Exception {
        return new OmrSheetPngRenderer().write(
                source,
                new ByteArrayOutputStream()
        );
    }

    private byte[] render(OmrSheetTemplateSpec spec)
            throws Exception {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();
        new OmrSheetPngRenderer().write(
                svgGenerator.generate(spec),
                output
        );
        return output.toByteArray();
    }

    private static int pixels(double millimeters, int dpi) {
        return Math.max(
                1,
                (int) Math.round(millimeters * dpi / 25.4)
        );
    }

    private static int[] findPhysicalResolution(byte[] png) {
        int offset = 8;
        while (offset + 12 <= png.length) {
            int length = readInt(png, offset);
            if (length < 0 || offset + 12L + length > png.length) {
                throw new AssertionError("Bloco PNG invalido.");
            }
            String type = new String(
                    png,
                    offset + 4,
                    4,
                    StandardCharsets.US_ASCII
            );
            if ("pHYs".equals(type)) {
                assertEquals(9, length);
                return new int[]{
                        readInt(png, offset + 8),
                        readInt(png, offset + 12),
                        png[offset + 16] & 0xFF
                };
            }
            offset += 12 + length;
        }
        return null;
    }

    private static int countChunks(byte[] png, String expectedType) {
        int count = 0;
        int offset = 8;
        while (offset + 12 <= png.length) {
            int length = readInt(png, offset);
            if (length < 0 || offset + 12L + length > png.length) {
                throw new AssertionError("Bloco PNG invalido.");
            }
            String type = new String(
                    png,
                    offset + 4,
                    4,
                    StandardCharsets.US_ASCII
            );
            if (expectedType.equals(type)) {
                count++;
            }
            offset += 12 + length;
        }
        return count;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void assertWhite(int color) {
        assertEquals(255, (color >>> 24) & 0xFF);
        assertTrue(((color >>> 16) & 0xFF) > 245);
        assertTrue(((color >>> 8) & 0xFF) > 245);
        assertTrue((color & 0xFF) > 245);
    }

    private static int countDark(
            Bitmap bitmap,
            int left,
            int top,
            int right,
            int bottom
    ) {
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int color = bitmap.getPixel(x, y);
                int red = (color >>> 16) & 0xFF;
                int green = (color >>> 8) & 0xFF;
                int blue = color & 0xFF;
                if (red < 40 && green < 40 && blue < 40) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void expectIllegalArgument(
            ThrowingRunnable action
    ) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TrackingOutputStream
            extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
