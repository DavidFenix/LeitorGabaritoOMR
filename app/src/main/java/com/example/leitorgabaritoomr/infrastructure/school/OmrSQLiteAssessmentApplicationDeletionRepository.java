package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationDeletionRepository;

/** Exclui atomicamente uma aplicação sem tentativas e sua chamada congelada. */
public final class OmrSQLiteAssessmentApplicationDeletionRepository
        implements OmrAssessmentApplicationDeletionRepository,
        AutoCloseable {

    private final OmrSchoolDatabase database;

    public OmrSQLiteAssessmentApplicationDeletionRepository(
            Context context
    ) {
        this(context, OmrSchoolDatabase.DEFAULT_DATABASE_NAME);
    }

    public OmrSQLiteAssessmentApplicationDeletionRepository(
            Context context,
            String databaseName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto Android é obrigatório."
            );
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco escolar é obrigatório."
            );
        }

        Context applicationContext = context.getApplicationContext();
        database = new OmrSchoolDatabase(
                applicationContext == null ? context : applicationContext,
                databaseName.trim()
        );
    }

    @Override
    public DeletionResult deleteIfUncorrected(String applicationId) {
        String normalizedId = normalizeOrNull(applicationId);
        if (normalizedId == null) {
            return DeletionResult.notFound();
        }

        SQLiteDatabase writable = database.getWritableDatabase();
        writable.beginTransaction();
        try {
            int applicationCount = countRows(
                    writable,
                    OmrSchoolDatabase.TABLE_ASSESSMENT_APPLICATION,
                    normalizedId
            );
            if (applicationCount == 0) {
                writable.setTransactionSuccessful();
                return DeletionResult.notFound();
            }

            int attemptCount = countRows(
                    writable,
                    OmrSchoolDatabase.TABLE_ASSESSMENT_ATTEMPT,
                    normalizedId
            );
            if (attemptCount > 0) {
                writable.setTransactionSuccessful();
                return DeletionResult.blocked(attemptCount);
            }

            String where = OmrSchoolDatabase.COLUMN_APPLICATION_ID + " = ?";
            String[] arguments = {normalizedId};
            int participantCount = writable.delete(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_PARTICIPANT,
                    where,
                    arguments
            );
            int deletedApplications = writable.delete(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_APPLICATION,
                    where,
                    arguments
            );

            if (deletedApplications != 1) {
                throw new IllegalStateException(
                        "A aplicação mudou durante a exclusão."
                );
            }

            writable.setTransactionSuccessful();
            return DeletionResult.deleted(participantCount);
        } catch (SQLiteConstraintException exception) {
            return DeletionResult.blocked(1);
        } finally {
            writable.endTransaction();
        }
    }

    @Override
    public void close() {
        database.close();
    }

    private static int countRows(
            SQLiteDatabase readable,
            String table,
            String applicationId
    ) {
        try (Cursor cursor = readable.rawQuery(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE "
                        + OmrSchoolDatabase.COLUMN_APPLICATION_ID
                        + " = ?",
                new String[]{applicationId}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static String normalizeOrNull(String value) {
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }
}
