package com.example.leitorgabaritoomr.infrastructure.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.leitorgabaritoomr.application.history.OmrGradingHistoryRepository;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repositorio SQLite do historico de correcoes OMR.
 *
 * Cada registro ocupa uma linha independente. Metadados indexados permitem
 * localizar aluno, leitura e data sem desserializar todo o banco; o payload
 * binario continua sendo a fonte completa e autoritativa do resultado.
 */
public final class OmrSQLiteGradingHistoryRepository
        implements OmrGradingHistoryRepository,
        AutoCloseable {

    static final String DEFAULT_DATABASE_NAME =
            "omr_grading_history.db";

    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY =
            "omr_grading_history";

    private static final String COLUMN_HISTORY_RECORD_ID =
            "history_record_id";
    private static final String COLUMN_READING_ID =
            "reading_id";
    private static final String COLUMN_STUDENT_ID =
            "student_id";
    private static final String COLUMN_STUDENT_REGISTRATION =
            "student_registration";
    private static final String COLUMN_STUDENT_NAME =
            "student_name";
    private static final String COLUMN_CLASS_NAME =
            "class_name";
    private static final String COLUMN_STORED_AT =
            "stored_at_epoch_millis";
    private static final String COLUMN_CAPTURED_AT =
            "captured_at_epoch_millis";
    private static final String COLUMN_ANSWER_KEY_ID =
            "answer_key_id";
    private static final String COLUMN_ANSWER_KEY_VERSION =
            "answer_key_version";
    private static final String COLUMN_ANSWER_KEY_NAME =
            "answer_key_name";
    private static final String COLUMN_AWARDED_POINTS =
            "awarded_points";
    private static final String COLUMN_POSSIBLE_POINTS =
            "possible_points";
    private static final String COLUMN_AWARDED_PERCENTAGE =
            "awarded_percentage";
    private static final String COLUMN_REQUIRES_REVIEW =
            "requires_review";
    private static final String COLUMN_IS_FINAL =
            "is_final";
    private static final String COLUMN_PAYLOAD =
            "payload";

    private static final String[] RECORD_PROJECTION = {
            COLUMN_HISTORY_RECORD_ID,
            COLUMN_READING_ID,
            COLUMN_STUDENT_ID,
            COLUMN_STUDENT_REGISTRATION,
            COLUMN_STUDENT_NAME,
            COLUMN_CLASS_NAME,
            COLUMN_STORED_AT,
            COLUMN_CAPTURED_AT,
            COLUMN_ANSWER_KEY_ID,
            COLUMN_ANSWER_KEY_VERSION,
            COLUMN_ANSWER_KEY_NAME,
            COLUMN_AWARDED_POINTS,
            COLUMN_POSSIBLE_POINTS,
            COLUMN_AWARDED_PERCENTAGE,
            COLUMN_REQUIRES_REVIEW,
            COLUMN_IS_FINAL,
            COLUMN_PAYLOAD
    };

    private static final String NEWEST_FIRST_ORDER =
            COLUMN_STORED_AT + " DESC, rowid DESC";

    private final DatabaseHelper databaseHelper;
    private final OmrGradingHistoryRecordBinaryCodec codec;

    public OmrSQLiteGradingHistoryRepository(
            Context context
    ) {
        this(context, DEFAULT_DATABASE_NAME);
    }

    OmrSQLiteGradingHistoryRepository(
            Context context,
            String databaseName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto Android e obrigatorio."
            );
        }

        if (databaseName == null
                || databaseName.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "O nome do banco e obrigatorio."
            );
        }

        Context applicationContext =
                context.getApplicationContext();

        databaseHelper = new DatabaseHelper(
                applicationContext == null
                        ? context
                        : applicationContext,
                databaseName.trim()
        );

        codec = new OmrGradingHistoryRecordBinaryCodec();
    }

    @Override
    public boolean save(
            OmrGradingHistoryRecord record
    ) {
        if (record == null) {
            throw new IllegalArgumentException(
                    "O registro historico e obrigatorio."
            );
        }

        ContentValues values = valuesOf(record);

        long insertedRowId = databaseHelper
                .getWritableDatabase()
                .insertWithOnConflict(
                        TABLE_HISTORY,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                );

        return insertedRowId != -1L;
    }

    @Override
    public List<OmrGradingHistoryRecord> loadAll() {
        return queryRecords(
                null,
                null
        );
    }

    @Override
    public OmrGradingHistoryRecord findByIdOrNull(
            String historyRecordId
    ) {
        String normalizedId = normalizeOrNull(
                historyRecordId
        );

        if (normalizedId == null) {
            return null;
        }

        return querySingleRecord(
                COLUMN_HISTORY_RECORD_ID + " = ?",
                new String[]{normalizedId}
        );
    }

    @Override
    public OmrGradingHistoryRecord findByReadingIdOrNull(
            String readingId
    ) {
        String normalizedId = normalizeOrNull(readingId);

        if (normalizedId == null) {
            return null;
        }

        return querySingleRecord(
                COLUMN_READING_ID + " = ?",
                new String[]{normalizedId}
        );
    }

    @Override
    public List<OmrGradingHistoryRecord> loadByStudentId(
            String studentId
    ) {
        String normalizedId = normalizeOrNull(studentId);

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return queryRecords(
                COLUMN_STUDENT_ID + " = ?",
                new String[]{normalizedId}
        );
    }

    @Override
    public void close() {
        databaseHelper.close();
    }

    private ContentValues valuesOf(
            OmrGradingHistoryRecord record
    ) {
        OmrStudentIdentity student = record.getStudent();

        ContentValues values = new ContentValues();

        values.put(
                COLUMN_HISTORY_RECORD_ID,
                record.getHistoryRecordId()
        );
        values.put(COLUMN_READING_ID, record.getReadingId());
        values.put(COLUMN_STUDENT_ID, student.getStudentId());
        values.put(
                COLUMN_STUDENT_REGISTRATION,
                student.getRegistration()
        );
        values.put(COLUMN_STUDENT_NAME, student.getName());
        values.put(COLUMN_CLASS_NAME, student.getClassName());
        values.put(
                COLUMN_STORED_AT,
                record.getStoredAtEpochMillis()
        );
        values.put(
                COLUMN_CAPTURED_AT,
                record.getCapturedAtEpochMillis()
        );
        values.put(
                COLUMN_ANSWER_KEY_ID,
                record.getAnswerKeyId()
        );
        values.put(
                COLUMN_ANSWER_KEY_VERSION,
                record.getAnswerKeyVersion()
        );
        values.put(
                COLUMN_ANSWER_KEY_NAME,
                record.getAnswerKeyName()
        );
        values.put(
                COLUMN_AWARDED_POINTS,
                record.getAwardedPoints()
        );
        values.put(
                COLUMN_POSSIBLE_POINTS,
                record.getPossiblePoints()
        );
        values.put(
                COLUMN_AWARDED_PERCENTAGE,
                record.getAwardedPercentage()
        );
        values.put(
                COLUMN_REQUIRES_REVIEW,
                record.requiresReview() ? 1 : 0
        );
        values.put(
                COLUMN_IS_FINAL,
                record.isFinal() ? 1 : 0
        );
        values.put(COLUMN_PAYLOAD, codec.encode(record));

        return values;
    }

    private List<OmrGradingHistoryRecord> queryRecords(
            String selection,
            String[] selectionArguments
    ) {
        List<OmrGradingHistoryRecord> records =
                new ArrayList<>();

        try (Cursor cursor = databaseHelper
                .getReadableDatabase()
                .query(
                        TABLE_HISTORY,
                        RECORD_PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        NEWEST_FIRST_ORDER
                )) {

            while (cursor.moveToNext()) {
                OmrGradingHistoryRecord record =
                        decodeAndValidateOrNull(cursor);

                if (record != null) {
                    records.add(record);
                }
            }
        }

        return Collections.unmodifiableList(records);
    }

    private OmrGradingHistoryRecord querySingleRecord(
            String selection,
            String[] selectionArguments
    ) {
        try (Cursor cursor = databaseHelper
                .getReadableDatabase()
                .query(
                        TABLE_HISTORY,
                        RECORD_PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        null,
                        "1"
                )) {

            if (!cursor.moveToFirst()) {
                return null;
            }

            return decodeAndValidateOrNull(cursor);
        }
    }

    private OmrGradingHistoryRecord decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(COLUMN_PAYLOAD)
            );

            OmrGradingHistoryRecord record =
                    codec.decode(payload);

            return metadataMatches(cursor, record)
                    ? record
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean metadataMatches(
            Cursor cursor,
            OmrGradingHistoryRecord record
    ) {
        OmrStudentIdentity student = record.getStudent();

        return textAt(cursor, COLUMN_HISTORY_RECORD_ID)
                .equals(record.getHistoryRecordId())
                && textAt(cursor, COLUMN_READING_ID)
                .equals(record.getReadingId())
                && textAt(cursor, COLUMN_STUDENT_ID)
                .equals(student.getStudentId())
                && textAt(cursor, COLUMN_STUDENT_REGISTRATION)
                .equals(student.getRegistration())
                && textAt(cursor, COLUMN_STUDENT_NAME)
                .equals(student.getName())
                && textAt(cursor, COLUMN_CLASS_NAME)
                .equals(student.getClassName())
                && longAt(cursor, COLUMN_STORED_AT)
                == record.getStoredAtEpochMillis()
                && longAt(cursor, COLUMN_CAPTURED_AT)
                == record.getCapturedAtEpochMillis()
                && textAt(cursor, COLUMN_ANSWER_KEY_ID)
                .equals(record.getAnswerKeyId())
                && intAt(cursor, COLUMN_ANSWER_KEY_VERSION)
                == record.getAnswerKeyVersion()
                && textAt(cursor, COLUMN_ANSWER_KEY_NAME)
                .equals(record.getAnswerKeyName())
                && Double.compare(
                doubleAt(cursor, COLUMN_AWARDED_POINTS),
                record.getAwardedPoints()
        ) == 0
                && Double.compare(
                doubleAt(cursor, COLUMN_POSSIBLE_POINTS),
                record.getPossiblePoints()
        ) == 0
                && Double.compare(
                doubleAt(cursor, COLUMN_AWARDED_PERCENTAGE),
                record.getAwardedPercentage()
        ) == 0
                && booleanAt(cursor, COLUMN_REQUIRES_REVIEW)
                == record.requiresReview()
                && booleanAt(cursor, COLUMN_IS_FINAL)
                == record.isFinal();
    }

    private static String textAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getString(
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

    private static int intAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getInt(
                cursor.getColumnIndexOrThrow(columnName)
        );
    }

    private static double doubleAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getDouble(
                cursor.getColumnIndexOrThrow(columnName)
        );
    }

    private static boolean booleanAt(
            Cursor cursor,
            String columnName
    ) {
        return intAt(cursor, columnName) == 1;
    }

    private static String normalizeOrNull(
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private static final class DatabaseHelper
            extends SQLiteOpenHelper {

        private DatabaseHelper(
                Context context,
                String databaseName
        ) {
            super(
                    context,
                    databaseName,
                    null,
                    DATABASE_VERSION
            );
        }

        @Override
        public void onCreate(
                SQLiteDatabase database
        ) {
            database.execSQL(
                    "CREATE TABLE " + TABLE_HISTORY + " ("
                            + COLUMN_HISTORY_RECORD_ID
                            + " TEXT PRIMARY KEY NOT NULL, "
                            + COLUMN_READING_ID
                            + " TEXT UNIQUE NOT NULL, "
                            + COLUMN_STUDENT_ID
                            + " TEXT NOT NULL, "
                            + COLUMN_STUDENT_REGISTRATION
                            + " TEXT NOT NULL, "
                            + COLUMN_STUDENT_NAME
                            + " TEXT NOT NULL, "
                            + COLUMN_CLASS_NAME
                            + " TEXT NOT NULL, "
                            + COLUMN_STORED_AT
                            + " INTEGER NOT NULL CHECK ("
                            + COLUMN_STORED_AT + " > 0), "
                            + COLUMN_CAPTURED_AT
                            + " INTEGER NOT NULL CHECK ("
                            + COLUMN_CAPTURED_AT + " > 0), "
                            + COLUMN_ANSWER_KEY_ID
                            + " TEXT NOT NULL, "
                            + COLUMN_ANSWER_KEY_VERSION
                            + " INTEGER NOT NULL CHECK ("
                            + COLUMN_ANSWER_KEY_VERSION + " > 0), "
                            + COLUMN_ANSWER_KEY_NAME
                            + " TEXT NOT NULL, "
                            + COLUMN_AWARDED_POINTS
                            + " REAL NOT NULL CHECK ("
                            + COLUMN_AWARDED_POINTS + " >= 0), "
                            + COLUMN_POSSIBLE_POINTS
                            + " REAL NOT NULL CHECK ("
                            + COLUMN_POSSIBLE_POINTS + " > 0), "
                            + COLUMN_AWARDED_PERCENTAGE
                            + " REAL NOT NULL CHECK ("
                            + COLUMN_AWARDED_PERCENTAGE
                            + " >= 0 AND "
                            + COLUMN_AWARDED_PERCENTAGE
                            + " <= 100), "
                            + COLUMN_REQUIRES_REVIEW
                            + " INTEGER NOT NULL CHECK ("
                            + COLUMN_REQUIRES_REVIEW
                            + " IN (0, 1)), "
                            + COLUMN_IS_FINAL
                            + " INTEGER NOT NULL CHECK ("
                            + COLUMN_IS_FINAL
                            + " IN (0, 1)), "
                            + COLUMN_PAYLOAD
                            + " BLOB NOT NULL"
                            + ")"
            );

            database.execSQL(
                    "CREATE INDEX index_omr_history_student_time"
                            + " ON " + TABLE_HISTORY
                            + " (" + COLUMN_STUDENT_ID
                            + ", " + COLUMN_STORED_AT + " DESC)"
            );

            database.execSQL(
                    "CREATE INDEX index_omr_history_answer_key"
                            + " ON " + TABLE_HISTORY
                            + " (" + COLUMN_ANSWER_KEY_ID
                            + ", " + COLUMN_ANSWER_KEY_VERSION + ")"
            );
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase database,
                int oldVersion,
                int newVersion
        ) {
            // A primeira versao ainda nao possui migracoes.
        }
    }
}
