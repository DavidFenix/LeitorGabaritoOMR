package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo SQLite das provas escolares disponíveis no dispositivo.
 *
 * Metadados indexados atendem às listagens e o payload binário versionado é
 * a fonte completa da definição. Uma linha cujo payload ou metadado esteja
 * corrompido é ignorada sem esconder as demais provas saudáveis.
 */
public final class OmrSQLiteAssessmentRepository
        implements OmrAssessmentRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
            OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
            OmrSchoolDatabase.COLUMN_TITLE,
            OmrSchoolDatabase.COLUMN_SUBJECT,
            OmrSchoolDatabase.COLUMN_GRADE_LEVEL,
            OmrSchoolDatabase.COLUMN_ACADEMIC_PERIOD,
            OmrSchoolDatabase.COLUMN_SCHOOL_YEAR,
            OmrSchoolDatabase.COLUMN_ANSWER_KEY_ID,
            OmrSchoolDatabase.COLUMN_ANSWER_KEY_VERSION,
            OmrSchoolDatabase.COLUMN_PAYLOAD
    };

    private static final String SCHOOL_ORDER =
            OmrSchoolDatabase.COLUMN_SCHOOL_YEAR + " DESC, "
                    + OmrSchoolDatabase.COLUMN_TITLE
                    + " COLLATE NOCASE ASC, "
                    + OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                    + " ASC, "
                    + OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
                    + " DESC";

    private static final Comparator<OmrAssessmentDefinition>
    LATEST_VERSION_ORDER = (first, second) -> {
        int comparison = Integer.compare(
                second.getSchoolYear(),
                first.getSchoolYear()
        );

        if (comparison != 0) {
            return comparison;
        }

        comparison = first.getTitle().compareToIgnoreCase(
                second.getTitle()
        );

        if (comparison != 0) {
            return comparison;
        }

        comparison = first.getAssessmentId().compareTo(
                second.getAssessmentId()
        );

        if (comparison != 0) {
            return comparison;
        }

        return Integer.compare(
                second.getVersion(),
                first.getVersion()
        );
    };

    private final OmrSchoolDatabase database;
    private final OmrAssessmentDefinitionBinaryCodec codec;

    public OmrSQLiteAssessmentRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    public OmrSQLiteAssessmentRepository(
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

        codec = new OmrAssessmentDefinitionBinaryCodec();
    }

    @Override
    public boolean save(
            OmrAssessmentDefinition assessment
    ) {
        if (assessment == null) {
            throw new IllegalArgumentException(
                    "A prova é obrigatória."
            );
        }

        long insertedRowId = database
                .getWritableDatabase()
                .insertWithOnConflict(
                        OmrSchoolDatabase.TABLE_ASSESSMENT,
                        null,
                        valuesOf(assessment),
                        SQLiteDatabase.CONFLICT_IGNORE
                );

        return insertedRowId != -1L;
    }

    @Override
    public List<OmrAssessmentDefinition> loadAll() {
        return queryAssessments(
                null,
                null,
                SCHOOL_ORDER
        );
    }

    @Override
    public List<OmrAssessmentDefinition>
    loadLatestVersions() {
        Map<String, OmrAssessmentDefinition> latestById =
                new LinkedHashMap<>();

        for (OmrAssessmentDefinition assessment
                : loadAll()) {

            OmrAssessmentDefinition current =
                    latestById.get(
                            assessment.getAssessmentId()
                    );

            if (current == null
                    || assessment.getVersion()
                    > current.getVersion()) {

                latestById.put(
                        assessment.getAssessmentId(),
                        assessment
                );
            }
        }

        List<OmrAssessmentDefinition> latest =
                new ArrayList<>(latestById.values());

        latest.sort(LATEST_VERSION_ORDER);

        return Collections.unmodifiableList(latest);
    }

    @Override
    public OmrAssessmentDefinition findOrNull(
            String assessmentId,
            int assessmentVersion
    ) {
        String normalizedId = normalizeOrNull(
                assessmentId
        );

        if (normalizedId == null
                || assessmentVersion <= 0) {
            return null;
        }

        List<OmrAssessmentDefinition> matches =
                queryAssessments(
                        OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                                + " = ? AND "
                                + OmrSchoolDatabase
                                .COLUMN_ASSESSMENT_VERSION
                                + " = ?",
                        new String[]{
                                normalizedId,
                                Integer.toString(assessmentVersion)
                        },
                        null
                );

        return matches.isEmpty()
                ? null
                : matches.get(0);
    }

    @Override
    public OmrAssessmentDefinition
    findLatestVersionOrNull(
            String assessmentId
    ) {
        String normalizedId = normalizeOrNull(
                assessmentId
        );

        if (normalizedId == null) {
            return null;
        }

        List<OmrAssessmentDefinition> matches =
                queryAssessments(
                        OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                                + " = ?",
                        new String[]{normalizedId},
                        OmrSchoolDatabase
                                .COLUMN_ASSESSMENT_VERSION
                                + " DESC"
                );

        return matches.isEmpty()
                ? null
                : matches.get(0);
    }

    @Override
    public void close() {
        database.close();
    }

    private ContentValues valuesOf(
            OmrAssessmentDefinition assessment
    ) {
        ContentValues values = new ContentValues();

        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                assessment.getAssessmentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
                assessment.getVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_TITLE,
                assessment.getTitle()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_SUBJECT,
                assessment.getSubject()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_GRADE_LEVEL,
                assessment.getGradeLevel()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ACADEMIC_PERIOD,
                assessment.getAcademicPeriod()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_SCHOOL_YEAR,
                assessment.getSchoolYear()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ANSWER_KEY_ID,
                assessment.getAnswerKeyId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ANSWER_KEY_VERSION,
                assessment.getAnswerKeyVersion()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_PAYLOAD,
                codec.encode(assessment)
        );

        return values;
    }

    private List<OmrAssessmentDefinition> queryAssessments(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrAssessmentDefinition> assessments =
                new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase.TABLE_ASSESSMENT,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrAssessmentDefinition assessment =
                        decodeAndValidateOrNull(cursor);

                if (assessment != null) {
                    assessments.add(assessment);
                }
            }
        }

        return Collections.unmodifiableList(assessments);
    }

    private OmrAssessmentDefinition decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase.COLUMN_PAYLOAD
                    )
            );

            OmrAssessmentDefinition assessment =
                    codec.decode(payload);

            return metadataMatches(cursor, assessment)
                    ? assessment
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrAssessmentDefinition assessment
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
        ).equals(assessment.getAssessmentId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
        ) == assessment.getVersion()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_TITLE
        ).equals(assessment.getTitle())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_SUBJECT
        ).equals(assessment.getSubject())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_GRADE_LEVEL
        ).equals(assessment.getGradeLevel())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ACADEMIC_PERIOD
        ).equals(assessment.getAcademicPeriod())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_SCHOOL_YEAR
        ) == assessment.getSchoolYear()
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ANSWER_KEY_ID
        ).equals(assessment.getAnswerKeyId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ANSWER_KEY_VERSION
        ) == assessment.getAnswerKeyVersion();
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

    private static String normalizeOrNull(
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
