package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationCsvParser;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public final class OmrAssessmentApplicationCsvExporterTest {

    @Test
    public void exportRoundTripsExactApplicationIdentity() {
        OmrAssessmentApplication application = application(
                "application-manual-123"
        );

        String csv = new OmrAssessmentApplicationCsvExporter().build(
                Collections.singletonList(application)
        );
        OmrAssessmentApplicationCsvParser.Preview preview =
                new OmrAssessmentApplicationCsvParser().parse(csv);

        assertTrue(preview.isReadyToImport());
        OmrAssessmentApplicationCsvParser.Row row =
                preview.getRows().get(0);
        assertEquals("application-manual-123", row.getApplicationCode());
        assertEquals("application-manual-123",
                row.getApplicationInternalId());
        assertEquals("assessment-exact", row.getAssessmentReference());
        assertEquals(3, row.getAssessmentVersion());
        assertEquals("class-exact", row.getClassReference());
    }

    @Test
    public void importedApplicationCodeIsDisplayedWithoutTechnicalPrefix() {
        String csv = new OmrAssessmentApplicationCsvExporter().build(
                Collections.singletonList(application(
                        "application-csv:NAT-1-1A"
                ))
        );

        OmrAssessmentApplicationCsvParser.Preview preview =
                new OmrAssessmentApplicationCsvParser().parse(csv);

        assertTrue(preview.isReadyToImport());
        assertEquals("NAT-1-1A", preview.getRows().get(0)
                .getApplicationCode());
        assertEquals("application-csv:NAT-1-1A", preview.getRows().get(0)
                .getApplicationInternalId());
    }

    @Test
    public void templateIsImmediatelyParseableAndUsesGeneratedIdentity() {
        OmrAssessmentApplicationCsvParser.Preview preview =
                new OmrAssessmentApplicationCsvParser().parse(
                        new OmrAssessmentApplicationCsvExporter()
                                .buildTemplate()
                );

        assertTrue(preview.isReadyToImport());
        assertEquals(1, preview.getRows().size());
        assertEquals("NAT-1-1A", preview.getRows().get(0)
                .getApplicationCode());
        assertEquals(null, preview.getRows().get(0)
                .getApplicationInternalId());
    }

    @Test
    public void writeUsesUtf8BomAndCrLf() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new OmrAssessmentApplicationCsvExporter().write(
                Collections.singletonList(application("application-1")),
                output
        );

        byte[] bytes = output.toByteArray();
        assertEquals(0xEF, bytes[0] & 0xFF);
        assertEquals(0xBB, bytes[1] & 0xFF);
        assertEquals(0xBF, bytes[2] & 0xFF);
        String text = new String(
                bytes,
                3,
                bytes.length - 3,
                StandardCharsets.UTF_8
        );
        assertTrue(text.contains("\r\n"));
        assertFalse(text.replace("\r\n", "").contains("\n"));
    }

    @Test
    public void rejectsNullAndRepeatedApplications() {
        OmrAssessmentApplication application = application("application-1");
        expectIllegalArgument(() ->
                new OmrAssessmentApplicationCsvExporter().build(null)
        );
        expectIllegalArgument(() ->
                new OmrAssessmentApplicationCsvExporter().build(
                        Arrays.asList(application, application)
                )
        );
        expectIllegalArgument(() ->
                new OmrAssessmentApplicationCsvExporter().build(
                        Collections.singletonList(null)
                )
        );
    }

    private static OmrAssessmentApplication application(String id) {
        return new OmrAssessmentApplication(
                id,
                "assessment-exact",
                3,
                "class-exact",
                1_000L
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
