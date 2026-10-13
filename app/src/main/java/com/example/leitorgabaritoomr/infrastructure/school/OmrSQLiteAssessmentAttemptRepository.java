package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Histórico SQLite somente de acréscimo das revisões de correção.
 */
public final class OmrSQLiteAssessmentAttemptRepository
        implements OmrAssessmentAttemptRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_ATTEMPT_ID,
            OmrSchoolDatabase.COLUMN_APPLICATION_ID,
            OmrSchoolDatabase.COLUMN_STUDENT_ID,
            OmrSchoolDatabase.COLUMN_ATTEMPT_REVISION,
            OmrSchoolDatabase.COLUMN_HISTORY_RECORD_ID,
            OmrSchoolDatabase.COLUMN_ATTEMPT_RECORDED_AT,
            OmrSchoolDatabase.COLUMN_ATTEMPT_PAYLOAD
    };

    private static final String APPLICATION_ORDER =
            OmrSchoolDatabase.COLUMN_STUDENT_ID
                    + " ASC, "
                    + OmrSchoolDatabase.COLUMN_ATTEMPT_REVISION
                    + " ASC, "
                    + OmrSchoolDatabase.COLUMN_ATTEMPT_ID
                    + " ASC";

    private static final String PARTICIPANT_ORDER =
            OmrSchoolDatabase.COLUMN_ATTEMPT_REVISION
                    + " ASC, "
                    + OmrSchoolDatabase.COLUMN_ATTEMPT_ID
                    + " ASC";

    private final OmrSchoolDatabase database;
    private final OmrAssessmentAttemptBinaryCodec codec;

    public OmrSQLiteAssessmentAttemptRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    public OmrSQLiteAssessmentAttemptRepository(
            Context context,
            String databaseName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto Android é obrigatório."
            );
        }

        if (databaseName == null
                || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco escolar é obrigatório."
            );
        }

        Context applicationContext =
                context.getApplicationContext();

        database = new OmrSchoolDatabase(
                applicationContext == null
                        ? context
                        : applicationContext,
                databaseName.trim()
        );

        codec = new OmrAssessmentAttemptBinaryCodec();
    }

    @Override
    public boolean save(OmrAssessmentAttempt attempt) {
        if (attempt == null) {
            throw new IllegalArgumentException(
                    "A tentativa de correção é obrigatória."
            );
        }

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();

        writableDatabase.beginTransaction();

        try {
            long existingCount = DatabaseUtils.queryNumEntries(
                    writableDatabase,
                    OmrSchoolDatabase.TABLE_ASSESSMENT_ATTEMPT,
                    OmrSchoolDatabase.COLUMN_ATTEMPT_ID + " = ?",
                    new String[]{attempt.getAttemptId()}
            );

            if (existingCount > 0L) {
                writableDatabase.setTransactionSuccessful();
                return false;
            }

            writableDatabase.insertOrThrow(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_ATTEMPT,
                    null,
                    valuesOf(attempt)
            );

            writableDatabase.setTransactionSuccessful();
            return true;

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "A tentativa exige um participante cadastrado e "
                            + "revisão e histórico ainda não utilizados.",
                    exception
            );

        } finally {
            writableDatabase.endTransaction();
        }
    }

    @Override
    public List<OmrAssessmentAttempt> loadForApplication(
            String applicationId
    ) {
        String normalizedId = normalizeOrNull(applicationId);

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return queryAttempts(
                OmrSchoolDatabase.COLUMN_APPLICATION_ID
                        + " = ?",
                new String[]{normalizedId},
                APPLICATION_ORDER
        );
    }

    @Override
    public List<OmrAssessmentAttempt> loadForParticipant(
            String applicationId,
            String studentId
    ) {
        String normalizedApplicationId =
                normalizeOrNull(applicationId);
        String normalizedStudentId = normalizeOrNull(studentId);

        if (normalizedApplicationId == null
                || normalizedStudentId == null) {
            return Collections.emptyList();
        }

        return queryAttempts(
                OmrSchoolDatabase.COLUMN_APPLICATION_ID
                        + " = ? AND "
                        + OmrSchoolDatabase.COLUMN_STUDENT_ID
                        + " = ?",
                new String[]{
                        normalizedApplicationId,
                        normalizedStudentId
                },
                PARTICIPANT_ORDER
        );
    }

    @Override
    public OmrAssessmentAttempt findLatestForParticipantOrNull(
            String applicationId,
            String studentId
    ) {
        List<OmrAssessmentAttempt> attempts =
                loadForParticipant(applicationId, studentId);

        return attempts.isEmpty()
                ? null
                : attempts.get(attempts.size() - 1);
    }

    @Override
    public OmrAssessmentAttempt findByIdOrNull(String attemptId) {
        String normalizedId = normalizeOrNull(attemptId);

        if (normalizedId == null) {
            return null;
        }

        return querySingleAttempt(
                OmrSchoolDatabase.COLUMN_ATTEMPT_ID + " = ?",
                new String[]{normalizedId}
        );
    }

    @Override
    public OmrAssessmentAttempt findByHistoryRecordIdOrNull(
            String historyRecordId
    ) {
        String normalizedId = normalizeOrNull(historyRecordId);

        if (normalizedId == null) {
            return null;
        }

        return querySingleAttempt(
                OmrSchoolDatabase.COLUMN_HISTORY_RECORD_ID + " = ?",
                new String[]{normalizedId}
        );
    }

    @Override
    public void close() {
        database.close();
    }

    private ContentValues valuesOf(OmrAssessmentAttempt attempt) {
        ContentValues values = new ContentValues();

        values.put(
                OmrSchoolDatabase.COLUMN_ATTEMPT_ID,
                attempt.getAttemptId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_ID,
                attempt.getApplicationId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_ID,
                attempt.getStudentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ATTEMPT_REVISION,
                attempt.getRevision()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_HISTORY_RECORD_ID,
                attempt.getHistoryRecordId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ATTEMPT_RECORDED_AT,
                attempt.getRecordedAtEpochMillis()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ATTEMPT_PAYLOAD,
                codec.encode(attempt)
        );

        return values;
    }

    private OmrAssessmentAttempt querySingleAttempt(
            String selection,
            String[] selectionArguments
    ) {
        List<OmrAssessmentAttempt> matches = queryAttempts(
                selection,
                selectionArguments,
                null
        );

        return matches.isEmpty() ? null : matches.get(0);
    }

    private List<OmrAssessmentAttempt> queryAttempts(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrAssessmentAttempt> attempts = new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase.TABLE_ASSESSMENT_ATTEMPT,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrAssessmentAttempt attempt =
                        decodeAndValidateOrNull(cursor);

                if (attempt != null) {
                    attempts.add(attempt);
                }
            }
        }

        return Collections.unmodifiableList(attempts);
    }

    private OmrAssessmentAttempt decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase.COLUMN_ATTEMPT_PAYLOAD
                    )
            );

            OmrAssessmentAttempt attempt = codec.decode(payload);

            return metadataMatches(cursor, attempt)
                    ? attempt
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrAssessmentAttempt attempt
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ATTEMPT_ID
        ).equals(attempt.getAttemptId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_APPLICATION_ID
        ).equals(attempt.getApplicationId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_STUDENT_ID
        ).equals(attempt.getStudentId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ATTEMPT_REVISION
        ) == attempt.getRevision()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_HISTORY_RECORD_ID
        ).equals(attempt.getHistoryRecordId())
                && longAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ATTEMPT_RECORDED_AT
        ) == attempt.getRecordedAtEpochMillis();
    }

    private static String textAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getString(
                cursor.getColumnIndexOrThrow(columnName)
        );
    }

    private static int intAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getInt(
                cursor.getColumnIndexOrThrow(columnName)
        );
    }

    private static long longAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getLong(
                cursor.getColumnIndexOrThrow(columnName)
        );
    }

    private static String normalizeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
