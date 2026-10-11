package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.classroom.OmrClassEnrollmentRepository;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo SQLite das matrículas de alunos nas turmas escolares.
 */
public final class OmrSQLiteClassEnrollmentRepository
        implements OmrClassEnrollmentRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_ENROLLMENT_CLASS_GROUP_ID,
            OmrSchoolDatabase.COLUMN_ENROLLMENT_STUDENT_ID,
            OmrSchoolDatabase.COLUMN_ROSTER_POSITION,
            OmrSchoolDatabase.COLUMN_ENROLLMENT_PAYLOAD
    };

    private static final String CLASS_ROSTER_ORDER =
            OmrSchoolDatabase.COLUMN_ROSTER_POSITION
                    + " ASC, "
                    + OmrSchoolDatabase.COLUMN_ENROLLMENT_STUDENT_ID
                    + " ASC";

    private static final String STUDENT_CLASS_ORDER =
            OmrSchoolDatabase.COLUMN_ENROLLMENT_CLASS_GROUP_ID
                    + " ASC";

    private final OmrSchoolDatabase database;
    private final OmrClassEnrollmentBinaryCodec codec;

    public OmrSQLiteClassEnrollmentRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    public OmrSQLiteClassEnrollmentRepository(
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

        codec = new OmrClassEnrollmentBinaryCodec();
    }

    @Override
    public void save(
            OmrClassEnrollment enrollment
    ) {
        if (enrollment == null) {
            throw new IllegalArgumentException(
                    "A matrícula em turma é obrigatória."
            );
        }

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();

        writableDatabase.beginTransaction();

        try {
            ContentValues values = valuesOf(enrollment);

            int updated = writableDatabase.update(
                    OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                    values,
                    OmrSchoolDatabase
                            .COLUMN_ENROLLMENT_CLASS_GROUP_ID
                            + " = ? AND "
                            + OmrSchoolDatabase
                            .COLUMN_ENROLLMENT_STUDENT_ID
                            + " = ?",
                    new String[]{
                            enrollment.getClassGroupId(),
                            enrollment.getStudentId()
                    }
            );

            if (updated == 0) {
                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                        null,
                        values
                );
            }

            writableDatabase.setTransactionSuccessful();

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "A matrícula exige turma e aluno cadastrados e uma "
                            + "posição livre na chamada.",
                    exception
            );

        } finally {
            writableDatabase.endTransaction();
        }
    }

    @Override
    public List<OmrClassEnrollment> loadForClassGroup(
            String classGroupId
    ) {
        String normalizedId = normalizeOrNull(
                classGroupId
        );

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return queryEnrollments(
                OmrSchoolDatabase
                        .COLUMN_ENROLLMENT_CLASS_GROUP_ID
                        + " = ?",
                new String[]{normalizedId},
                CLASS_ROSTER_ORDER
        );
    }

    @Override
    public List<OmrClassEnrollment> loadForStudent(
            String studentId
    ) {
        String normalizedId = normalizeOrNull(studentId);

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return queryEnrollments(
                OmrSchoolDatabase.COLUMN_ENROLLMENT_STUDENT_ID
                        + " = ?",
                new String[]{normalizedId},
                STUDENT_CLASS_ORDER
        );
    }

    @Override
    public OmrClassEnrollment findOrNull(
            String classGroupId,
            String studentId
    ) {
        String normalizedClassGroupId = normalizeOrNull(
                classGroupId
        );
        String normalizedStudentId = normalizeOrNull(
                studentId
        );

        if (normalizedClassGroupId == null
                || normalizedStudentId == null) {
            return null;
        }

        List<OmrClassEnrollment> matches =
                queryEnrollments(
                        OmrSchoolDatabase
                                .COLUMN_ENROLLMENT_CLASS_GROUP_ID
                                + " = ? AND "
                                + OmrSchoolDatabase
                                .COLUMN_ENROLLMENT_STUDENT_ID
                                + " = ?",
                        new String[]{
                                normalizedClassGroupId,
                                normalizedStudentId
                        },
                        null
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
            OmrClassEnrollment enrollment
    ) {
        ContentValues values = new ContentValues();

        values.put(
                OmrSchoolDatabase
                        .COLUMN_ENROLLMENT_CLASS_GROUP_ID,
                enrollment.getClassGroupId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ENROLLMENT_STUDENT_ID,
                enrollment.getStudentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ROSTER_POSITION,
                enrollment.getRosterPosition()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ENROLLMENT_PAYLOAD,
                codec.encode(enrollment)
        );

        return values;
    }

    private List<OmrClassEnrollment> queryEnrollments(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrClassEnrollment> enrollments =
                new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrClassEnrollment enrollment =
                        decodeAndValidateOrNull(cursor);

                if (enrollment != null) {
                    enrollments.add(enrollment);
                }
            }
        }

        return Collections.unmodifiableList(enrollments);
    }

    private OmrClassEnrollment decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase
                                    .COLUMN_ENROLLMENT_PAYLOAD
                    )
            );

            OmrClassEnrollment enrollment =
                    codec.decode(payload);

            return metadataMatches(cursor, enrollment)
                    ? enrollment
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrClassEnrollment enrollment
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase
                        .COLUMN_ENROLLMENT_CLASS_GROUP_ID
        ).equals(enrollment.getClassGroupId())
                && textAt(
                cursor,
                OmrSchoolDatabase
                        .COLUMN_ENROLLMENT_STUDENT_ID
        ).equals(enrollment.getStudentId())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ROSTER_POSITION
        ) == enrollment.getRosterPosition();
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
