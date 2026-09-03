package com.example.leitorgabaritoomr.infrastructure.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteGradingHistoryRepositoryInstrumentedTest {

    private static final String TEST_DATABASE_NAME =
            "omr_grading_history_instrumented_test.db";

    private Context applicationContext;
    private OmrSQLiteGradingHistoryRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        applicationContext.deleteDatabase(TEST_DATABASE_NAME);
        openRepository();
    }

    @After
    public void tearDown() {
        closeRepository();
        applicationContext.deleteDatabase(TEST_DATABASE_NAME);
    }

    @Test
    public void emptyRepositoryReturnsEmptyResults() {
        assertTrue(repository.loadAll().isEmpty());
        assertTrue(
                repository.loadByStudentId("student-001")
                        .isEmpty()
        );
        assertNull(repository.findByIdOrNull("history-001"));
        assertNull(
                repository.findByReadingIdOrNull("reading-001")
        );
    }

    @Test
    public void saveAndLoadPreserveCompleteRecord() {
        OmrGradingHistoryRecord original = record(
                "history-001",
                "reading-001",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        assertTrue(repository.save(original));

        List<OmrGradingHistoryRecord> stored =
                repository.loadAll();

        assertEquals(1, stored.size());
        assertCompleteRecord(original, stored.get(0));
    }

    @Test
    public void loadAllOrdersNewestStorageFirst() {
        OmrGradingHistoryRecord oldest = record(
                "history-oldest",
                "reading-oldest",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                1_000L
        );

        OmrGradingHistoryRecord newest = record(
                "history-newest",
                "reading-newest",
                "student-002",
                "000456",
                "Bruno Lima",
                "9 B",
                3_000L
        );

        OmrGradingHistoryRecord middle = record(
                "history-middle",
                "reading-middle",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        repository.save(oldest);
        repository.save(newest);
        repository.save(middle);

        List<OmrGradingHistoryRecord> stored =
                repository.loadAll();

        assertEquals("history-newest", idAt(stored, 0));
        assertEquals("history-middle", idAt(stored, 1));
        assertEquals("history-oldest", idAt(stored, 2));
    }

    @Test
    public void equalStorageInstantUsesLatestInsertionFirst() {
        OmrGradingHistoryRecord first = record(
                "history-first",
                "reading-first",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        OmrGradingHistoryRecord second = record(
                "history-second",
                "reading-second",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        repository.save(first);
        repository.save(second);

        List<OmrGradingHistoryRecord> stored =
                repository.loadAll();

        assertEquals("history-second", idAt(stored, 0));
        assertEquals("history-first", idAt(stored, 1));
    }

    @Test
    public void newInstanceReadsPersistedHistory() {
        OmrGradingHistoryRecord original = record(
                "history-persisted",
                "reading-persisted",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        repository.save(original);
        closeRepository();
        openRepository();

        assertCompleteRecord(
                original,
                repository.findByIdOrNull(
                        "history-persisted"
                )
        );
    }

    @Test
    public void duplicateHistoryRecordIdPreservesFirstRecord() {
        OmrGradingHistoryRecord original = record(
                "history-001",
                "reading-original",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        OmrGradingHistoryRecord conflicting = record(
                "history-001",
                "reading-conflicting",
                "student-002",
                "000456",
                "Bruno Lima",
                "9 B",
                3_000L
        );

        assertTrue(repository.save(original));
        assertFalse(repository.save(conflicting));

        assertEquals(1, repository.loadAll().size());
        assertCompleteRecord(
                original,
                repository.findByIdOrNull("history-001")
        );
        assertNull(
                repository.findByReadingIdOrNull(
                        "reading-conflicting"
                )
        );
    }

    @Test
    public void duplicateReadingIdPreservesFirstStudentLink() {
        OmrGradingHistoryRecord original = record(
                "history-original",
                "reading-001",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        OmrGradingHistoryRecord conflicting = record(
                "history-conflicting",
                "reading-001",
                "student-002",
                "000456",
                "Bruno Lima",
                "9 B",
                3_000L
        );

        assertTrue(repository.save(original));
        assertFalse(repository.save(conflicting));

        OmrGradingHistoryRecord stored =
                repository.findByReadingIdOrNull(
                        " reading-001 "
                );

        assertCompleteRecord(original, stored);
        assertEquals(
                "student-001",
                stored.getStudent().getStudentId()
        );
        assertNull(
                repository.findByIdOrNull(
                        "history-conflicting"
                )
        );
    }

    @Test
    public void findsNormalizedIdsAndReturnsNullForMissingOnes() {
        OmrGradingHistoryRecord original = record(
                "history-001",
                "reading-001",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        );

        repository.save(original);

        assertCompleteRecord(
                original,
                repository.findByIdOrNull(" history-001 ")
        );
        assertCompleteRecord(
                original,
                repository.findByReadingIdOrNull(
                        " reading-001 "
                )
        );

        assertNull(repository.findByIdOrNull("missing"));
        assertNull(repository.findByIdOrNull(" "));
        assertNull(repository.findByIdOrNull(null));
        assertNull(
                repository.findByReadingIdOrNull(null)
        );
    }

    @Test
    public void filtersByStableStudentIdInNewestFirstOrder() {
        repository.save(record(
                "history-ana-old",
                "reading-ana-old",
                "student-ana",
                "000123",
                "Ana Beatriz",
                "9 A",
                1_000L
        ));

        repository.save(record(
                "history-bruno",
                "reading-bruno",
                "student-bruno",
                "000456",
                "Bruno Lima",
                "9 B",
                3_000L
        ));

        repository.save(record(
                "history-ana-new",
                "reading-ana-new",
                "student-ana",
                "000123",
                "Ana B. Costa",
                "9 B",
                2_000L
        ));

        List<OmrGradingHistoryRecord> anaHistory =
                repository.loadByStudentId(
                        " student-ana "
                );

        assertEquals(2, anaHistory.size());
        assertEquals("history-ana-new", idAt(anaHistory, 0));
        assertEquals("history-ana-old", idAt(anaHistory, 1));
        assertEquals(
                "Ana B. Costa",
                anaHistory.get(0).getStudent().getName()
        );
        assertEquals(
                "Ana Beatriz",
                anaHistory.get(1).getStudent().getName()
        );
    }

    @Test
    public void returnedListsAreImmutable() {
        repository.save(record(
                "history-001",
                "reading-001",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        ));

        expectUnsupportedOperation(() ->
                repository.loadAll().clear()
        );

        expectUnsupportedOperation(() ->
                repository.loadByStudentId(
                        "student-001"
                ).clear()
        );

        expectUnsupportedOperation(() ->
                repository.loadByStudentId(null).add(
                        record(
                                "another",
                                "another-reading",
                                "student-001",
                                "000123",
                                "Ana Beatriz",
                                "9 A",
                                3_000L
                        )
                )
        );
    }

    @Test
    public void rejectsNullRecordAndInvalidConstructorData() {
        expectIllegalArgument(() -> repository.save(null));

        expectIllegalArgument(() ->
                new OmrSQLiteGradingHistoryRepository(
                        null,
                        TEST_DATABASE_NAME
                )
        );

        expectIllegalArgument(() ->
                new OmrSQLiteGradingHistoryRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void tamperedMetadataIsSkippedWithoutHidingHealthyRows() {
        repository.save(record(
                "history-tampered",
                "reading-tampered",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        ));

        repository.save(record(
                "history-healthy",
                "reading-healthy",
                "student-002",
                "000456",
                "Bruno Lima",
                "9 B",
                3_000L
        ));

        closeRepository();

        ContentValues values = new ContentValues();
        values.put("student_name", "Nome adulterado");

        updateDatabaseRow(
                values,
                "history_record_id = ?",
                new String[]{"history-tampered"}
        );

        openRepository();

        List<OmrGradingHistoryRecord> stored =
                repository.loadAll();

        assertEquals(1, stored.size());
        assertEquals("history-healthy", idAt(stored, 0));
        assertNull(
                repository.findByIdOrNull("history-tampered")
        );
    }

    @Test
    public void corruptedPayloadIsSkippedWithoutHidingHealthyRows() {
        repository.save(record(
                "history-corrupted",
                "reading-corrupted",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9 A",
                2_000L
        ));

        repository.save(record(
                "history-healthy",
                "reading-healthy",
                "student-002",
                "000456",
                "Bruno Lima",
                "9 B",
                3_000L
        ));

        closeRepository();

        ContentValues values = new ContentValues();
        values.put("payload", new byte[]{1, 2, 3});

        updateDatabaseRow(
                values,
                "history_record_id = ?",
                new String[]{"history-corrupted"}
        );

        openRepository();

        List<OmrGradingHistoryRecord> stored =
                repository.loadAll();

        assertEquals(1, stored.size());
        assertEquals("history-healthy", idAt(stored, 0));
        assertNull(
                repository.findByReadingIdOrNull(
                        "reading-corrupted"
                )
        );
    }

    private void openRepository() {
        repository =
                new OmrSQLiteGradingHistoryRepository(
                        applicationContext,
                        TEST_DATABASE_NAME
                );
    }

    private void closeRepository() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    private void updateDatabaseRow(
            ContentValues values,
            String whereClause,
            String[] whereArguments
    ) {
        SQLiteDatabase database =
                SQLiteDatabase.openDatabase(
                        applicationContext
                                .getDatabasePath(
                                        TEST_DATABASE_NAME
                                )
                                .getPath(),
                        null,
                        SQLiteDatabase.OPEN_READWRITE
                );

        try {
            int updated = database.update(
                    "omr_grading_history",
                    values,
                    whereClause,
                    whereArguments
            );

            assertEquals(1, updated);
        } finally {
            database.close();
        }
    }

    private String idAt(
            List<OmrGradingHistoryRecord> records,
            int index
    ) {
        return records.get(index).getHistoryRecordId();
    }

    private void assertCompleteRecord(
            OmrGradingHistoryRecord expected,
            OmrGradingHistoryRecord actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getStoredAtEpochMillis(),
                actual.getStoredAtEpochMillis()
        );
        assertEquals(
                expected.getStudent().getStudentId(),
                actual.getStudent().getStudentId()
        );
        assertEquals(
                expected.getStudent().getRegistration(),
                actual.getStudent().getRegistration()
        );
        assertEquals(
                expected.getStudent().getName(),
                actual.getStudent().getName()
        );
        assertEquals(
                expected.getStudent().getClassName(),
                actual.getStudent().getClassName()
        );
        assertEquals(
                expected.getGradingResult(),
                actual.getGradingResult()
        );
    }

    private OmrGradingHistoryRecord record(
            String historyRecordId,
            String readingId,
            String studentId,
            String registration,
            String studentName,
            String className,
            long storedAt
    ) {
        OmrStudentIdentity student =
                new OmrStudentIdentity(
                        studentId,
                        registration,
                        studentName,
                        className
                );

        OmrReadingResult reading = new OmrReadingResult(
                readingId,
                storedAt - 100L,
                "layout-controlled",
                1,
                "Layout controlado",
                Collections.singletonList(
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
                                0.96
                        )
                )
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-001",
                        1,
                        "Avaliacao de Matematica",
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

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        reading,
                        answerKey
                );

        return new OmrGradingHistoryRecord(
                historyRecordId,
                storedAt,
                student,
                gradingResult
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private void expectUnsupportedOperation(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }
}
