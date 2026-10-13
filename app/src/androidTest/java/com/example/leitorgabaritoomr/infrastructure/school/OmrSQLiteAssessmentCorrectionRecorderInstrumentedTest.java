package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionContext;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionRecorder;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentCorrectionRecorderInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrAssessmentCorrectionRecorder recorder;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        databaseName = "omr_correction_recorder_"
                + UUID.randomUUID() + ".db";
        applicationContext.deleteDatabase(databaseName);
        seedSchoolGraph();
        openRecorder();
    }

    @After
    public void tearDown() {
        closeRecorder();
        applicationContext.deleteDatabase(databaseName);
    }

    @Test
    public void firstAndSecondCorrectionsPersistAsContinuousRevisions() {
        OmrAssessmentAttempt first = recorder.record(
                correctionContext(),
                history("history-1", "reading-1", 2_000L, student(), 3)
        );
        OmrAssessmentAttempt second = recorder.record(
                correctionContext(),
                history("history-2", "reading-2", 3_000L, student(), 3)
        );

        assertEquals(1, first.getRevision());
        assertEquals(2, second.getRevision());
        assertEquals("attempt:history-1", first.getAttemptId());
        assertEquals("attempt:history-2", second.getAttemptId());

        List<OmrAssessmentAttempt> stored =
                attemptRepository.loadForParticipant(
                        "application-1",
                        "student-1"
                );
        assertEquals(2, stored.size());
        assertEquals(first, stored.get(0));
        assertEquals(second, stored.get(1));
    }

    @Test
    public void repeatedConfirmationOfSameHistoryIsIdempotent() {
        OmrGradingHistoryRecord history = history(
                "history-1", "reading-1", 2_000L, student(), 3
        );

        OmrAssessmentAttempt first = recorder.record(
                correctionContext(), history
        );
        OmrAssessmentAttempt repeated = recorder.record(
                correctionContext(), history
        );

        assertEquals(first, repeated);
        assertEquals(
                1,
                attemptRepository.loadForApplication("application-1").size()
        );
    }

    @Test
    public void attemptsRemainAvailableAfterRepositoryReopen() {
        OmrAssessmentAttempt saved = recorder.record(
                correctionContext(),
                history("history-1", "reading-1", 2_000L, student(), 3)
        );

        closeRecorder();
        openRecorder();

        OmrAssessmentAttempt restored =
                attemptRepository.findByIdOrNull(saved.getAttemptId());
        assertNotNull(restored);
        assertEquals(saved, restored);
        assertEquals(
                saved,
                attemptRepository.findByHistoryRecordIdOrNull("history-1")
        );
    }

    @Test
    public void invalidStudentOrAnswerKeyLeavesRepositoryUnchanged() {
        OmrStudentIdentity changedSnapshot = new OmrStudentIdentity(
                "student-1",
                "MAT-999",
                "Outra identidade",
                "9º A congelada"
        );

        expectIllegalArgument(() -> recorder.record(
                correctionContext(),
                history(
                        "history-student",
                        "reading-student",
                        2_000L,
                        changedSnapshot,
                        3
                )
        ));
        expectIllegalArgument(() -> recorder.record(
                correctionContext(),
                history(
                        "history-key",
                        "reading-key",
                        3_000L,
                        student(),
                        4
                )
        ));

        assertTrue(attemptRepository
                .loadForApplication("application-1")
                .isEmpty());
    }

    private void seedSchoolGraph() {
        try (OmrSQLiteAssessmentRepository assessments =
                     new OmrSQLiteAssessmentRepository(
                             applicationContext, databaseName
                     );
             OmrSQLiteClassGroupRepository classes =
                     new OmrSQLiteClassGroupRepository(
                             applicationContext, databaseName
                     );
             OmrSQLiteAssessmentApplicationRepository applications =
                     new OmrSQLiteAssessmentApplicationRepository(
                             applicationContext, databaseName
                     );
             OmrSQLiteAssessmentParticipantRepository participants =
                     new OmrSQLiteAssessmentParticipantRepository(
                             applicationContext, databaseName
                     )) {

            assertTrue(assessments.save(assessment()));
            classes.save(new OmrClassGroup(
                    "class-1", "9º A", "9º ano", 2026
            ));
            assertTrue(applications.save(application()));
            assertTrue(participants.saveRoster(
                    "application-1",
                    Collections.singletonList(participant())
            ));
        }
    }

    private void openRecorder() {
        attemptRepository = new OmrSQLiteAssessmentAttemptRepository(
                applicationContext, databaseName
        );
        recorder = new OmrAssessmentCorrectionRecorder(attemptRepository);
    }

    private void closeRecorder() {
        recorder = null;
        if (attemptRepository != null) {
            attemptRepository.close();
            attemptRepository = null;
        }
    }

    private static OmrAssessmentCorrectionContext correctionContext() {
        return new OmrAssessmentCorrectionContext(
                application(),
                assessment(),
                participant(),
                answerKey(3)
        );
    }

    private static OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-1",
                "assessment-1",
                2,
                "class-1",
                1_000L
        );
    }

    private static OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-1",
                2,
                "Avaliação bimestral",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "key-1",
                3
        );
    }

    private static OmrAssessmentParticipant participant() {
        return new OmrAssessmentParticipant(
                "application-1",
                "student-1",
                "MAT-001",
                "Ana congelada",
                "9º A congelada",
                1
        );
    }

    private static OmrStudentIdentity student() {
        return new OmrStudentIdentity(
                "student-1",
                "MAT-001",
                "Ana congelada",
                "9º A congelada"
        );
    }

    private static OmrGradingHistoryRecord history(
            String historyId,
            String readingId,
            long storedAt,
            OmrStudentIdentity student,
            int keyVersion
    ) {
        return new OmrGradingHistoryRecord(
                historyId,
                storedAt,
                student,
                gradingResult(readingId, keyVersion)
        );
    }

    private static OmrGradingResult gradingResult(
            String readingId,
            int keyVersion
    ) {
        OmrQuestionResult question = new OmrQuestionResult(
                1,
                "question-001",
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        new OmrQuestionResult.Option(
                                "question-001-option-01",
                                "A"
                        )
                ),
                0.99
        );
        OmrReadingResult reading = new OmrReadingResult(
                readingId,
                1_500L,
                "omr-flex-ad-q001-r001",
                3,
                "Cartão flexível",
                Collections.singletonList(question)
        );
        return new OmrReadingGrader().grade(
                reading,
                answerKey(keyVersion)
        );
    }

    private static OmrAnswerKeyDefinition answerKey(int version) {
        return new OmrAnswerKeyDefinition(
                "key-1",
                version,
                "Gabarito exato",
                "omr-flex-ad-q001-r001",
                3,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        )
                )
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
