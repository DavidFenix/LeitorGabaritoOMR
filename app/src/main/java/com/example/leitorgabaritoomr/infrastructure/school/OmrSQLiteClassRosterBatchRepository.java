package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.classroom.OmrClassRosterBatchRepository;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Importação SQLite atômica de turmas, alunos e listas de chamada. */
public final class OmrSQLiteClassRosterBatchRepository
        implements OmrClassRosterBatchRepository, AutoCloseable {

    private final OmrSchoolDatabase database;
    private final OmrClassGroupBinaryCodec classCodec;
    private final OmrStudentProfileBinaryCodec studentCodec;
    private final OmrClassEnrollmentBinaryCodec enrollmentCodec;

    public OmrSQLiteClassRosterBatchRepository(Context context) {
        this(context, OmrSchoolDatabase.DEFAULT_DATABASE_NAME);
    }

    public OmrSQLiteClassRosterBatchRepository(
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
        classCodec = new OmrClassGroupBinaryCodec();
        studentCodec = new OmrStudentProfileBinaryCodec();
        enrollmentCodec = new OmrClassEnrollmentBinaryCodec();
    }

    @Override
    public void replaceRosters(
            List<OmrClassGroup> classGroups,
            List<OmrStudentProfile> students,
            List<OmrClassEnrollment> enrollments
    ) {
        validateBatch(classGroups, students, enrollments);

        SQLiteDatabase writableDatabase = database.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
            for (OmrClassGroup classGroup : classGroups) {
                upsertClassGroup(writableDatabase, classGroup);
            }
            for (OmrStudentProfile student : students) {
                upsertStudent(writableDatabase, student);
            }
            for (OmrClassGroup classGroup : classGroups) {
                writableDatabase.delete(
                        OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                        OmrSchoolDatabase.COLUMN_ENROLLMENT_CLASS_GROUP_ID
                                + " = ?",
                        new String[]{classGroup.getClassGroupId()}
                );
            }
            for (OmrClassEnrollment enrollment : enrollments) {
                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                        null,
                        enrollmentValues(enrollment)
                );
            }
            writableDatabase.setTransactionSuccessful();
        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "O lote contém turma, aluno ou posição incompatível.",
                    exception
            );
        } finally {
            writableDatabase.endTransaction();
        }
    }

    @Override
    public void close() {
        database.close();
    }

    private void upsertClassGroup(
            SQLiteDatabase writableDatabase,
            OmrClassGroup classGroup
    ) {
        ContentValues values = classGroupValues(classGroup);
        int updated = writableDatabase.update(
                OmrSchoolDatabase.TABLE_CLASS_GROUP,
                values,
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID + " = ?",
                new String[]{classGroup.getClassGroupId()}
        );
        if (updated == 0) {
            writableDatabase.insertOrThrow(
                    OmrSchoolDatabase.TABLE_CLASS_GROUP,
                    null,
                    values
            );
        }
    }

    private void upsertStudent(
            SQLiteDatabase writableDatabase,
            OmrStudentProfile student
    ) {
        ContentValues values = studentValues(student);
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

    private ContentValues classGroupValues(OmrClassGroup classGroup) {
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
                classCodec.encode(classGroup)
        );
        return values;
    }

    private ContentValues studentValues(OmrStudentProfile student) {
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
                studentCodec.encode(student)
        );
        return values;
    }

    private ContentValues enrollmentValues(
            OmrClassEnrollment enrollment
    ) {
        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_ENROLLMENT_CLASS_GROUP_ID,
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
                enrollmentCodec.encode(enrollment)
        );
        return values;
    }

    private static void validateBatch(
            List<OmrClassGroup> classGroups,
            List<OmrStudentProfile> students,
            List<OmrClassEnrollment> enrollments
    ) {
        if (classGroups == null || students == null || enrollments == null) {
            throw new IllegalArgumentException(
                    "As coleções do lote escolar são obrigatórias."
            );
        }
        if (classGroups.isEmpty()
                || students.isEmpty()
                || enrollments.isEmpty()) {
            throw new IllegalArgumentException(
                    "O lote escolar não pode estar vazio."
            );
        }

        Set<String> classIds = new HashSet<>();
        for (OmrClassGroup classGroup : classGroups) {
            if (classGroup == null
                    || !classIds.add(classGroup.getClassGroupId())) {
                throw new IllegalArgumentException(
                        "O lote possui turma nula ou repetida."
                );
            }
        }

        Set<String> studentIds = new HashSet<>();
        Set<String> registrations = new HashSet<>();
        for (OmrStudentProfile student : students) {
            if (student == null
                    || !studentIds.add(student.getStudentId())
                    || !registrations.add(
                    student.getRegistration().toUpperCase(Locale.ROOT)
            )) {
                throw new IllegalArgumentException(
                        "O lote possui aluno nulo ou repetido."
                );
            }
        }

        Set<String> enrollmentKeys = new HashSet<>();
        Set<String> positionKeys = new HashSet<>();
        Set<String> enrolledClasses = new HashSet<>();
        Set<String> enrolledStudents = new HashSet<>();

        for (OmrClassEnrollment enrollment : enrollments) {
            if (enrollment == null
                    || !classIds.contains(enrollment.getClassGroupId())
                    || !studentIds.contains(enrollment.getStudentId())) {
                throw new IllegalArgumentException(
                        "Toda matrícula deve apontar para turma e aluno do lote."
                );
            }
            String enrollmentKey = enrollment.getClassGroupId()
                    + "\u0000" + enrollment.getStudentId();
            String positionKey = enrollment.getClassGroupId()
                    + "\u0000" + enrollment.getRosterPosition();
            if (!enrollmentKeys.add(enrollmentKey)
                    || !positionKeys.add(positionKey)) {
                throw new IllegalArgumentException(
                        "O lote possui aluno ou posição repetida em uma turma."
                );
            }
            enrolledClasses.add(enrollment.getClassGroupId());
            enrolledStudents.add(enrollment.getStudentId());
        }

        if (!enrolledClasses.equals(classIds)
                || !enrolledStudents.equals(studentIds)) {
            throw new IllegalArgumentException(
                    "Toda turma e todo aluno do lote devem possuir matrícula."
            );
        }
    }
}
