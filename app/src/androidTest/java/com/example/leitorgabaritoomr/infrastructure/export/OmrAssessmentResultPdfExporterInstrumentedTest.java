package com.example.leitorgabaritoomr.infrastructure.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentResultPdfExporterInstrumentedTest {

    @Test
    public void rejectsMissingInputs() {
        OmrAssessmentResultPdfExporter exporter =
                new OmrAssessmentResultPdfExporter();
        try {
            exporter.write(null, new ByteArrayOutputStream());
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    @Test
    public void writesPdfSignature() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new OmrAssessmentResultPdfExporter().write(report(1), output);
        byte[] bytes = output.toByteArray();
        assertTrue(bytes.length > 500);
        assertEquals("%PDF", new String(bytes, 0, 4, "US-ASCII"));
    }

    @Test
    public void paginatesLongFrozenRoster() throws Exception {
        byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new OmrAssessmentResultPdfExporter().write(report(19), output);
            bytes = output.toByteArray();
        }

        Context context = ApplicationProvider.getApplicationContext();
        File file = new File(context.getCacheDir(), "result-test.pdf");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        try (ParcelFileDescriptor descriptor =
                     ParcelFileDescriptor.open(
                             file,
                             ParcelFileDescriptor.MODE_READ_ONLY
                     );
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            assertEquals(2, renderer.getPageCount());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static OmrAssessmentResultReport report(int participantCount) {
        OmrAssessmentApplication application = new OmrAssessmentApplication(
                "application-1", "assessment-1", 1,
                "class-1", 1_000L
        );
        OmrAssessmentDefinition assessment = new OmrAssessmentDefinition(
                "assessment-1", 1, "Avaliação", "Matemática",
                "9º ano", "1º bimestre", 2026, "key-1", 1
        );
        List<OmrAssessmentResultReport.Row> rows = new ArrayList<>();
        for (int position = 1;
             position <= participantCount;
             position++) {
            OmrAssessmentParticipant participant =
                    new OmrAssessmentParticipant(
                            "application-1",
                            "student-" + position,
                            "MAT-" + position,
                            "Aluno " + position,
                            "9º A",
                            position
                    );
            rows.add(new OmrAssessmentResultReport.Row(
                    participant, null, null
            ));
        }
        return new OmrAssessmentResultReport(
                application, assessment, "9º A", 2_000L, rows
        );
    }
}
