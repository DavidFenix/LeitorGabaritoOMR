package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyCsvParser;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyPngZipPlan;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyPngZipPlanner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(AndroidJUnit4.class)
public final class OmrAnswerKeyPngZipExporterInstrumentedTest {

    @Test
    public void templateBecomesZipWithTwoDecodablePngFiles()
            throws Exception {
        OmrAnswerKeyCsvParser.Preview preview =
                new OmrAnswerKeyCsvParser().parse(
                        new OmrAnswerKeyCsvExporter().buildTemplate()
                );
        OmrAnswerKeyPngZipPlan plan =
                new OmrAnswerKeyPngZipPlanner().plan(preview);
        assertTrue(plan.isReadyToExport());

        TrackingOutputStream destination =
                new TrackingOutputStream();

        OmrAnswerKeyPngZipExporter.ExportSummary summary =
                new OmrAnswerKeyPngZipExporter().write(
                        plan,
                        destination
                );

        assertFalse(destination.closed);
        assertEquals(2, summary.getExportedCount());
        assertEquals(
                Arrays.asList("mat-a-v1.png", "nat-b-v1.png"),
                summary.getEntryNames()
        );

        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(destination.toByteArray())
        )) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                assertFalse(entry.isDirectory());
                assertFalse(entry.getName().contains("/"));

                byte[] png = readCurrentEntry(zip);
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
                    assertTrue(decoded.getWidth() > 100);
                    assertTrue(decoded.getHeight() > 100);
                } finally {
                    decoded.recycle();
                }
                zip.closeEntry();
            }
        }

        assertEquals(summary.getEntryNames(), entryNames);

        destination.write(0x7F);
        assertTrue(destination.size() > 1_000);
    }

    @Test
    public void exporterRejectsInvalidPlanAndMissingDestination()
            throws Exception {
        OmrAnswerKeyPngZipPlan invalid =
                new OmrAnswerKeyPngZipPlanner().plan(
                        new OmrAnswerKeyCsvParser().parse("")
                );

        expectIllegalArgument(() ->
                new OmrAnswerKeyPngZipExporter().write(
                        invalid,
                        new ByteArrayOutputStream()
                )
        );

        OmrAnswerKeyPngZipPlan valid =
                new OmrAnswerKeyPngZipPlanner().plan(
                        new OmrAnswerKeyCsvParser().parse(
                                new OmrAnswerKeyCsvExporter()
                                        .buildTemplate()
                        )
                );

        expectIllegalArgument(() ->
                new OmrAnswerKeyPngZipExporter().write(valid, null)
        );
    }

    private static byte[] readCurrentEntry(
            ZipInputStream zip
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
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
