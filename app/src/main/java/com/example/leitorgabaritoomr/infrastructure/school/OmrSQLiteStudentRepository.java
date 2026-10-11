package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.student.OmrStudentRepository;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Catálogo SQLite dos alunos permanentes conhecidos pelo dispositivo.
 *
 * Salvar o mesmo identificador atualiza o cadastro sem trocar sua identidade.
 * Matrículas não podem ser compartilhadas por alunos diferentes. Linhas com
 * payload ou metadados inconsistentes são ignoradas sem esconder as demais.
 */
public final class OmrSQLiteStudentRepository
        implements OmrStudentRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_STUDENT_ID,
            OmrSchoolDatabase.COLUMN_REGISTRATION,
            OmrSchoolDatabase.COLUMN_STUDENT_NAME,
            OmrSchoolDatabase.COLUMN_STUDENT_PAYLOAD
    };

    private static final String SCHOOL_ORDER =
            OmrSchoolDatabase.COLUMN_STUDENT_NAME
                    + " COLLATE NOCASE ASC, "
                    + OmrSchoolDatabase.COLUMN_REGISTRATION
                    + " COLLATE NOCASE ASC, "
                    + OmrSchoolDatabase.COLUMN_STUDENT_ID
                    + " ASC";

    private final OmrSchoolDatabase database;
    private final OmrStudentProfileBinaryCodec codec;

    public OmrSQLiteStudentRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    /**
     * Abre um catálogo com nome de banco explícito.
     *
     * Útil para fluxos isolados e testes instrumentados sem compartilhar o
     * banco escolar padrão do aplicativo.
     */
    public OmrSQLiteStudentRepository(
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

        codec = new OmrStudentProfileBinaryCodec();
    }

    @Override
    public void save(
            OmrStudentProfile student
    ) {
        if (student == null) {
            throw new IllegalArgumentException(
                    "O aluno é obrigatório."
            );
        }

        saveAll(Collections.singletonList(student));
    }

    @Override
    public void saveAll(
            List<OmrStudentProfile> students
    ) {
        validateBatch(students);

        if (students.isEmpty()) {
            return;
        }

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();

        writableDatabase.beginTransaction();

        try {
            for (OmrStudentProfile student : students) {
                upsert(writableDatabase, student);
            }

            writableDatabase.setTransactionSuccessful();

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "A matrícula já pertence a outro aluno.",
                    exception
            );

        } finally {
            writableDatabase.endTransaction();
        }
    }

    private void upsert(
            SQLiteDatabase writableDatabase,
            OmrStudentProfile student
    ) {
        ContentValues values = valuesOf(student);

        int updated = writableDatabase.update(
                OmrSchoolDatabase.TABLE_STUDENT,
                values,
                OmrSchoolDatabase.COLUMN_STUDENT_ID + " = ?",
                new String[]{student.getStudentId()}
        );

        if (updated == 0) {
            writableDatabase.insertOrThrow(
                    OmrSchoolDatabase.TABLE_STUDENT,
                    null,
                    values
            );
        }
    }

    @Override
    public List<OmrStudentProfile> loadAll() {
        return queryStudents(
                null,
                null,
                SCHOOL_ORDER
        );
    }

    @Override
    public OmrStudentProfile findByIdOrNull(
            String studentId
    ) {
        String normalizedId = normalizeTextOrNull(
                studentId
        );

        if (normalizedId == null) {
            return null;
        }

        return firstOrNull(queryStudents(
                OmrSchoolDatabase.COLUMN_STUDENT_ID
                        + " = ?",
                new String[]{normalizedId},
                null
        ));
    }

    @Override
    public OmrStudentProfile findByRegistrationOrNull(
            String registration
    ) {
        String normalizedRegistration =
                normalizeRegistrationOrNull(registration);

        if (normalizedRegistration == null) {
            return null;
        }

        return firstOrNull(queryStudents(
                OmrSchoolDatabase.COLUMN_REGISTRATION
                        + " = ?",
                new String[]{normalizedRegistration},
                null
        ));
    }

    @Override
    public void close() {
        database.close();
    }

    private ContentValues valuesOf(
            OmrStudentProfile student
    ) {
        ContentValues values = new ContentValues();

        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_ID,
                student.getStudentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_REGISTRATION,
                student.getRegistration()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_NAME,
                student.getName()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_PAYLOAD,
                codec.encode(student)
        );

        return values;
    }

    private List<OmrStudentProfile> queryStudents(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrStudentProfile> students =
                new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase.TABLE_STUDENT,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrStudentProfile student =
                        decodeAndValidateOrNull(cursor);

                if (student != null) {
                    students.add(student);
                }
            }
        }

        return Collections.unmodifiableList(students);
    }

    private OmrStudentProfile decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase
                                    .COLUMN_STUDENT_PAYLOAD
                    )
            );

            OmrStudentProfile student =
                    codec.decode(payload);

            return metadataMatches(cursor, student)
                    ? student
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrStudentProfile student
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_STUDENT_ID
        ).equals(student.getStudentId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_REGISTRATION
        ).equals(student.getRegistration())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_STUDENT_NAME
        ).equals(student.getName());
    }

    private static String textAt(
            Cursor cursor,
            String columnName
    ) {
        return cursor.getString(
                cursor.getColumnIndexOrThrow(columnName)
        );
    }

    private static OmrStudentProfile firstOrNull(
            List<OmrStudentProfile> matches
    ) {
        return matches.isEmpty()
                ? null
                : matches.get(0);
    }

    private static String normalizeTextOrNull(
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeRegistrationOrNull(
            String value
    ) {
        String normalized = normalizeTextOrNull(value);

        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private static void validateBatch(
            List<OmrStudentProfile> students
    ) {
        if (students == null) {
            throw new IllegalArgumentException(
                    "A coleção de alunos é obrigatória."
            );
        }

        Set<String> studentIds = new HashSet<>();
        Set<String> registrations = new HashSet<>();

        for (OmrStudentProfile student : students) {
            if (student == null) {
                throw new IllegalArgumentException(
                        "A coleção não pode conter alunos nulos."
                );
            }

            if (!studentIds.add(student.getStudentId())) {
                throw new IllegalArgumentException(
                        "Aluno repetido no lote: "
                                + student.getStudentId()
                );
            }

            String normalizedRegistration =
                    student.getRegistration()
                            .toUpperCase(Locale.ROOT);

            if (!registrations.add(normalizedRegistration)) {
                throw new IllegalArgumentException(
                        "Matrícula repetida no lote: "
                                + student.getRegistration()
                );
            }
        }
    }
}
