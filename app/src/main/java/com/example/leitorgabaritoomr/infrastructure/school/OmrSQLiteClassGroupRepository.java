package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.classroom.OmrClassGroupRepository;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catálogo SQLite das turmas escolares disponíveis no dispositivo.
 */
public final class OmrSQLiteClassGroupRepository
        implements OmrClassGroupRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID,
            OmrSchoolDatabase.COLUMN_CLASS_NAME,
            OmrSchoolDatabase.COLUMN_CLASS_GRADE_LEVEL,
            OmrSchoolDatabase.COLUMN_CLASS_SCHOOL_YEAR,
            OmrSchoolDatabase.COLUMN_CLASS_PAYLOAD
    };

    private static final String SCHOOL_ORDER =
            OmrSchoolDatabase.COLUMN_CLASS_SCHOOL_YEAR
                    + " DESC, "
                    + OmrSchoolDatabase.COLUMN_CLASS_NAME
                    + " COLLATE NOCASE ASC, "
                    + OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
                    + " ASC";

    private static final String YEAR_ORDER =
            OmrSchoolDatabase.COLUMN_CLASS_NAME
                    + " COLLATE NOCASE ASC, "
                    + OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
                    + " ASC";

    private final OmrSchoolDatabase database;
    private final OmrClassGroupBinaryCodec codec;

    public OmrSQLiteClassGroupRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    public OmrSQLiteClassGroupRepository(
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

        codec = new OmrClassGroupBinaryCodec();
    }

    @Override
    public void save(
            OmrClassGroup classGroup
    ) {
        if (classGroup == null) {
            throw new IllegalArgumentException(
                    "A turma é obrigatória."
            );
        }

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();

        writableDatabase.beginTransaction();

        try {
            ContentValues values = valuesOf(classGroup);

            int updated = writableDatabase.update(
                    OmrSchoolDatabase.TABLE_CLASS_GROUP,
                    values,
                    OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
                            + " = ?",
                    new String[]{classGroup.getClassGroupId()}
            );

            if (updated == 0) {
                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase.TABLE_CLASS_GROUP,
                        null,
                        values
                );
            }

            writableDatabase.setTransactionSuccessful();

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "Não foi possível salvar a turma.",
                    exception
            );

        } finally {
            writableDatabase.endTransaction();
        }
    }

    @Override
    public List<OmrClassGroup> loadAll() {
        return queryClassGroups(
                null,
                null,
                SCHOOL_ORDER
        );
    }

    @Override
    public List<OmrClassGroup> loadForSchoolYear(
            int schoolYear
    ) {
        if (schoolYear <= 0) {
            return Collections.emptyList();
        }

        return queryClassGroups(
                OmrSchoolDatabase.COLUMN_CLASS_SCHOOL_YEAR
                        + " = ?",
                new String[]{Integer.toString(schoolYear)},
                YEAR_ORDER
        );
    }

    @Override
    public OmrClassGroup findByIdOrNull(
            String classGroupId
    ) {
        String normalizedId = normalizeOrNull(
                classGroupId
        );

        if (normalizedId == null) {
            return null;
        }

        List<OmrClassGroup> matches = queryClassGroups(
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
                        + " = ?",
                new String[]{normalizedId},
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
            OmrClassGroup classGroup
    ) {
        ContentValues values = new ContentValues();

        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID,
                classGroup.getClassGroupId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_NAME,
                classGroup.getName()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_GRADE_LEVEL,
                classGroup.getGradeLevel()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_SCHOOL_YEAR,
                classGroup.getSchoolYear()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_PAYLOAD,
                codec.encode(classGroup)
        );

        return values;
    }

    private List<OmrClassGroup> queryClassGroups(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrClassGroup> classGroups =
                new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase.TABLE_CLASS_GROUP,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrClassGroup classGroup =
                        decodeAndValidateOrNull(cursor);

                if (classGroup != null) {
                    classGroups.add(classGroup);
                }
            }
        }

        return Collections.unmodifiableList(classGroups);
    }

    private OmrClassGroup decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase.COLUMN_CLASS_PAYLOAD
                    )
            );

            OmrClassGroup classGroup = codec.decode(payload);

            return metadataMatches(cursor, classGroup)
                    ? classGroup
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrClassGroup classGroup
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID
        ).equals(classGroup.getClassGroupId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_CLASS_NAME
        ).equals(classGroup.getName())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_CLASS_GRADE_LEVEL
        ).equals(classGroup.getGradeLevel())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_CLASS_SCHOOL_YEAR
        ) == classGroup.getSchoolYear();
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
