package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentDeletionRepository;

/** Exclusao SQLite atomica de uma prova, suas versoes e seus blocos. */
public final class OmrSQLiteAssessmentDeletionRepository
        implements OmrAssessmentDeletionRepository, AutoCloseable {

    private final OmrSchoolDatabase database;

    public OmrSQLiteAssessmentDeletionRepository(Context context) {
        this(context, OmrSchoolDatabase.DEFAULT_DATABASE_NAME);
    }

    public OmrSQLiteAssessmentDeletionRepository(
            Context context,
            String databaseName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto Android e obrigatorio."
            );
        }
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco escolar e obrigatorio."
            );
        }

        Context applicationContext = context.getApplicationContext();
        database = new OmrSchoolDatabase(
                applicationContext == null ? context : applicationContext,
                databaseName.trim()
        );
    }

    @Override
    public DeletionResult deleteAllVersionsIfUnused(
            String assessmentId
    ) {
        String normalizedId = normalizeOrNull(assessmentId);
        if (normalizedId == null) {
            return DeletionResult.notFound();
        }

        SQLiteDatabase writable = database.getWritableDatabase();
        writable.beginTransaction();
        try {
            int versionCount = countRows(
                    writable,
                    OmrSchoolDatabase.TABLE_ASSESSMENT,
                    OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                    normalizedId
            );
            if (versionCount == 0) {
                writable.setTransactionSuccessful();
                return DeletionResult.notFound();
            }

            int applicationCount = countRows(
                    writable,
                    OmrSchoolDatabase.TABLE_ASSESSMENT_APPLICATION,
                    OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                    normalizedId
            );
            if (applicationCount > 0) {
                writable.setTransactionSuccessful();
                return DeletionResult.blocked(applicationCount);
            }

            String where = OmrSchoolDatabase.COLUMN_ASSESSMENT_ID + " = ?";
            String[] arguments = {normalizedId};

            writable.delete(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK,
                    where,
                    arguments
            );
            writable.delete(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                    where,
                    arguments
            );
            int deletedVersions = writable.delete(
                    OmrSchoolDatabase.TABLE_ASSESSMENT,
                    where,
                    arguments
            );

            writable.setTransactionSuccessful();
            return DeletionResult.deleted(deletedVersions);

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
            String column,
            String value
    ) {
        try (Cursor cursor = readable.rawQuery(
                "SELECT COUNT(*) FROM " + table
                        + " WHERE " + column + " = ?",
                new String[]{value}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static String normalizeOrNull(String value) {
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }
}
