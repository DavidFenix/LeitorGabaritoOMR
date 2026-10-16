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
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockBreakdown;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockScorer;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    public void addsAuditablePagesForEveryFrozenComponent()
            throws Exception {
        byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new OmrAssessmentResultPdfExporter().write(
                    structuredReport(),
                    output
            );
            bytes = output.toByteArray();
        }
        assertEquals(3, pageCount(bytes, "structured-result-test.pdf"));
    }

    private static int pageCount(byte[] bytes, String fileName)
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = new File(context.getCacheDir(), fileName);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY
        ); PdfRenderer renderer = new PdfRenderer(descriptor)) {
            return renderer.getPageCount();
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

    private static OmrAssessmentResultReport structuredReport() {
        OmrAssessmentStructure structure = new OmrAssessmentStructure(
                "assessment-1",
                1,
                2,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                "qui", 1, "Ciências da Natureza",
                                "QUI", "Química", 1, 1,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        ),
                        new OmrAssessmentBlock(
                                "fis", 2, "Ciências da Natureza",
                                "FIS", "Física", 2, 1,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        )
                )
        );
        OmrAssessmentApplication application =
                new OmrAssessmentApplication(
                        "application-1", "assessment-1", 1,
                        "class-1", 1_000L, structure
                );
        OmrAssessmentDefinition assessment = new OmrAssessmentDefinition(
                "assessment-1", 1, "Caderno de Natureza", "Natureza",
                "9º ano", "1º bimestre", 2026, "key-1", 1
        );
        OmrAssessmentParticipant participant =
                new OmrAssessmentParticipant(
                        "application-1", "student-1", "MAT-1",
                        "Aluno 1", "9º A", 1
                );

        OmrQuestionResult first = question(1, "question-001", "01", "A");
        OmrQuestionResult second = question(2, "question-002", "02", "B");
        OmrReadingResult reading = new OmrReadingResult(
                "reading-1", 1_500L, "omr-flex-ad-q002-r001",
                3, "Folha flexível", Arrays.asList(first, second)
        );
        OmrAnswerKeyDefinition key = new OmrAnswerKeyDefinition(
                "key-1", 1, "Gabarito", "omr-flex-ad-q002-r001", 3,
                Arrays.asList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        ),
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-002",
                                "question-002-option-01",
                                1.0
                        )
                )
        );
        OmrGradingResult grading = new OmrReadingGrader().grade(reading, key);
        OmrGradingHistoryRecord history = new OmrGradingHistoryRecord(
                "history-1", 2_000L,
                participant.toStudentIdentity(), grading
        );
        OmrAssessmentAttempt attempt = new OmrAssessmentAttempt(
                "attempt-1", "application-1", "student-1", 1,
                "history-1", 2_000L, structure
        );
        OmrAssessmentBlockBreakdown breakdown =
                new OmrAssessmentBlockScorer().calculate(
                        structure,
                        grading
                );
        return new OmrAssessmentResultReport(
                application,
                assessment,
                "9º A",
                2_500L,
                Collections.singletonList(
                        new OmrAssessmentResultReport.Row(
                                participant,
                                attempt,
                                history,
                                breakdown
                        )
                )
        );
    }

    private static OmrQuestionResult question(
            int position,
            String questionId,
            String optionSuffix,
            String label
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(new OmrQuestionResult.Option(
                        questionId + "-option-" + optionSuffix,
                        label
                )),
                0.99
        );
    }
}
