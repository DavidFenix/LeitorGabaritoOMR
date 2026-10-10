package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo SQLite das aplicações imutáveis de prova em turma.
 */
public final class OmrSQLiteAssessmentApplicationRepository
        implements OmrAssessmentApplicationRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_APPLICATION_ID,
            OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
            OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
            OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID,
            OmrSchoolDatabase.COLUMN_APPLICATION_CREATED_AT,
            OmrSchoolDatabase.COLUMN_APPLICATION_PAYLOAD
    };

    private static final String APPLICATION_ORDER =
            OmrSchoolDatabase.COLUMN_APPLICATION_CREATED_AT
                    + " DESC, "
                    + OmrSchoolDatabase.COLUMN_APPLICATION_ID
                    + " ASC";

    private final OmrSchoolDatabase database;
    private final OmrAssessmentApplicationBinaryCodec codec;

    public OmrSQLiteAssessmentApplicationRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    OmrSQLiteAssessmentApplicationRepository(
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

        codec = new OmrAssessmentApplicationBinaryCodec();
    }

    @Override
    public boolean save(
            OmrAssessmentApplication application
    ) {
        if (application == null) {
            throw new IllegalArgumentException(
                    "A aplicação da prova é obrigatória."
            );
        }

        try {
            long rowId = database
                    .getWritableDatabase()
                    .insertWithOnConflict(
                            OmrSchoolDatabase
                                    .TABLE_ASSESSMENT_APPLICATION,
                            null,
                            valuesOf(application),
                            SQLiteDatabase.CONFLICT_IGNORE
                    );

            return rowId != -1L;

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "A aplicação exige uma versão de prova e uma turma "
                            + "já cadastradas.",
                    exception
            );
        }
    }

    @Override
    public List<OmrAssessmentApplication> loadAll() {
        return queryApplications(
                null,
                null,
                APPLICATION_ORDER
        );
    }

    @Override
    public List<OmrAssessmentApplication> loadForClassGroup(
            String classGroupId
    ) {
        String normalizedId = normalizeOrNull(classGroupId);

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return queryApplications(
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
                        + " = ?",
                new String[]{normalizedId},
                APPLICATION_ORDER
        );
    }

    @Override
    public List<OmrAssessmentApplication> loadForAssessment(
            String assessmentId,
            int assessmentVersion
    ) {
        String normalizedId = normalizeOrNull(assessmentId);

        if (normalizedId == null || assessmentVersion <= 0) {
            return Collections.emptyList();
        }

        return queryApplications(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                        + " = ? AND "
                        + OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
                        + " = ?",
                new String[]{
                        normalizedId,
                        Integer.toString(assessmentVersion)
                },
                APPLICATION_ORDER
        );
    }

    @Override
    public OmrAssessmentApplication findByIdOrNull(
            String applicationId
    ) {
        String normalizedId = normalizeOrNull(applicationId);

        if (normalizedId == null) {
            return null;
        }

        List<OmrAssessmentApplication> matches =
                queryApplications(
                        OmrSchoolDatabase.COLUMN_APPLICATION_ID
                                + " = ?",
                        new String[]{normalizedId},
                        null
                );

        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public void close() {
        database.close();
    }

    private ContentValues valuesOf(
            OmrAssessmentApplication application
    ) {
        ContentValues values = new ContentValues();

        values.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_ID,
                application.getApplicationId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                application.getAssessmentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
                application.getAssessmentVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID,
                application.getClassGroupId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_CREATED_AT,
                application.getCreatedAtEpochMillis()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_PAYLOAD,
                codec.encode(application)
        );

        return values;
    }

    private List<OmrAssessmentApplication> queryApplications(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrAssessmentApplication> applications =
                new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase
                                .TABLE_ASSESSMENT_APPLICATION,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrAssessmentApplication application =
                        decodeAndValidateOrNull(cursor);

                if (application != null) {
                    applications.add(application);
                }
            }
        }

        return Collections.unmodifiableList(applications);
    }

    private OmrAssessmentApplication decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase
                                    .COLUMN_APPLICATION_PAYLOAD
                    )
            );

            OmrAssessmentApplication application =
                    codec.decode(payload);

            return metadataMatches(cursor, application)
                    ? application
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrAssessmentApplication application
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_APPLICATION_ID
        ).equals(application.getApplicationId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
        ).equals(application.getAssessmentId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
        ) == application.getAssessmentVersion()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
        ).equals(application.getClassGroupId())
                && longAt(
                cursor,
                OmrSchoolDatabase.COLUMN_APPLICATION_CREATED_AT
        ) == application.getCreatedAtEpochMillis();
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
