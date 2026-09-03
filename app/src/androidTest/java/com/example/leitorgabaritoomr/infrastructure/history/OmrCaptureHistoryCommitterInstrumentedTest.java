package com.example.leitorgabaritoomr.infrastructure.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.application.history.OmrGradingHistoryRecorder;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;
import com.example.leitorgabaritoomr.presentation.capture.OmrCaptureHistoryCommitter;
import com.example.leitorgabaritoomr.presentation.grading.OmrGradingResultActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Integra a decisao final da captura ao armazenamento SQLite real.
 *
 * Nao abre Activity, camera ou OpenCV. O banco usado e exclusivo destes
 * testes e e removido antes e depois de cada caso.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrCaptureHistoryCommitterInstrumentedTest {

    private static final String TEST_DATABASE_NAME =
            "omr_capture_history_committer_test.db";

    private Context applicationContext;
    private OmrSQLiteGradingHistoryRepository repository;
    private OmrGradingHistoryRecorder recorder;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        applicationContext.deleteDatabase(TEST_DATABASE_NAME);

        repository =
                new OmrSQLiteGradingHistoryRepository(
                        applicationContext,
                        TEST_DATABASE_NAME
                );

        recorder =
                new OmrGradingHistoryRecorder(repository);
    }

    @After
    public void tearDown() {
        recorder = null;

        if (repository != null) {
            repository.close();
            repository = null;
        }

        if (applicationContext != null) {
            applicationContext.deleteDatabase(
                    TEST_DATABASE_NAME
            );
        }
    }

    @Test
    public void confirmedResultIsPersistedWithStudent() {
        OmrStudentIdentity student = student();
        OmrGradingResult gradingResult =
                gradingResult("capture-reading-001");

        OmrGradingHistoryRecord returned =
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_OK,
                                student,
                                gradingResult,
                                recorder
                        );

        assertNotNull(returned);
        assertEquals(1, repository.loadAll().size());

        OmrGradingHistoryRecord stored =
                repository.findByReadingIdOrNull(
                        "capture-reading-001"
                );

        assertNotNull(stored);
        assertEquals(
                student.getStudentId(),
                stored.getStudent().getStudentId()
        );
        assertEquals(
                student.getRegistration(),
                stored.getStudent().getRegistration()
        );
        assertEquals(gradingResult, stored.getGradingResult());
    }

    @Test
    public void repeatedConfirmationDoesNotDuplicateHistory() {
        OmrStudentIdentity student = student();
        OmrGradingResult gradingResult =
                gradingResult("capture-reading-001");

        OmrGradingHistoryRecord first =
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_OK,
                                student,
                                gradingResult,
                                recorder
                        );

        OmrGradingHistoryRecord repeated =
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_OK,
                                student,
                                gradingResult,
                                recorder
        );

        assertNotNull(first);
        assertEquals(first, repeated);
        assertEquals(1, repository.loadAll().size());
        assertEquals(
                first.getHistoryRecordId(),
                repository.loadAll()
                        .get(0)
                        .getHistoryRecordId()
        );
    }

    @Test
    public void readAgainDoesNotPersistAttempt() {
        OmrGradingHistoryRecord returned =
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                OmrGradingResultActivity
                                        .RESULT_READ_AGAIN,
                                student(),
                                gradingResult(
                                        "capture-reading-read-again"
                                ),
                                recorder
                        );

        assertNull(returned);
        assertEquals(0, repository.loadAll().size());
    }

    @Test
    public void canceledResultDoesNotPersistAttempt() {
        OmrGradingHistoryRecord returned =
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_CANCELED,
                                student(),
                                gradingResult(
                                        "capture-reading-canceled"
                                ),
                                recorder
                        );

        assertNull(returned);
        assertEquals(0, repository.loadAll().size());
    }

    @Test
    public void confirmedLegacyFlowWithoutStudentDoesNotPersist() {
        OmrGradingHistoryRecord returned =
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_OK,
                                null,
                                gradingResult(
                                        "capture-reading-legacy"
                                ),
                                null
                        );

        assertNull(returned);
        assertEquals(0, repository.loadAll().size());
    }

    @Test
    public void confirmedStudentRequiresResultAndRecorder() {
        expectIllegalState(() ->
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_OK,
                                student(),
                                null,
                                recorder
                        )
        );

        expectIllegalState(() ->
                OmrCaptureHistoryCommitter
                        .recordIfConfirmed(
                                Activity.RESULT_OK,
                                student(),
                                gradingResult(
                                        "capture-reading-no-recorder"
                                ),
                                null
                        )
        );

        assertEquals(0, repository.loadAll().size());
    }

    private static OmrStudentIdentity student() {
        return new OmrStudentIdentity(
                "manual:000123",
                "000123",
                "Ana Beatriz",
                "9 A"
        );
    }

    private static OmrGradingResult gradingResult(
            String readingId
    ) {
        OmrQuestionResult question =
                new OmrQuestionResult(
                        1,
                        "Q01",
                        OmrQuestionResult.Status.SINGLE_MARK,
                        Collections.singletonList(
                                new OmrQuestionResult.Option(
                                        "A",
                                        "A"
                                )
                        ),
                        0.97
                );

        OmrReadingResult reading =
                new OmrReadingResult(
                        readingId,
                        1_800_000_000_000L,
                        "layout-controlled",
                        1,
                        "Layout controlado",
                        Collections.singletonList(question)
                );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "capture-answer-key",
                        1,
                        "Avaliacao controlada",
                        "layout-controlled",
                        1,
                        Collections.singletonList(
                                OmrAnswerKeyEntry.singleAnswer(
                                        "Q01",
                                        "A",
                                        1.0
                                )
                        )
                );

        return new OmrReadingGrader().grade(
                reading,
                answerKey
        );
    }

    private static void expectIllegalState(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
    }
}
