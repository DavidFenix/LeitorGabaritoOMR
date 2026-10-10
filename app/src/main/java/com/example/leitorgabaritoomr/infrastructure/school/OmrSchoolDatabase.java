package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Banco local dos dados escolares associados ao núcleo OMR.
 *
 * As próximas tabelas de alunos, turmas, aplicações e tentativas serão
 * acrescentadas por migrações neste mesmo banco, evitando armazenamentos
 * isolados difíceis de consultar em conjunto.
 */
final class OmrSchoolDatabase extends SQLiteOpenHelper {

    static final String DEFAULT_DATABASE_NAME =
            "omr_school.db";

    static final int DATABASE_VERSION = 3;

    static final String TABLE_ASSESSMENT =
            "omr_assessment";

    static final String COLUMN_ASSESSMENT_ID =
            "assessment_id";
    static final String COLUMN_ASSESSMENT_VERSION =
            "assessment_version";
    static final String COLUMN_TITLE =
            "title";
    static final String COLUMN_SUBJECT =
            "subject";
    static final String COLUMN_GRADE_LEVEL =
            "grade_level";
    static final String COLUMN_ACADEMIC_PERIOD =
            "academic_period";
    static final String COLUMN_SCHOOL_YEAR =
            "school_year";
    static final String COLUMN_ANSWER_KEY_ID =
            "answer_key_id";
    static final String COLUMN_ANSWER_KEY_VERSION =
            "answer_key_version";
    static final String COLUMN_PAYLOAD =
            "payload";

    static final String TABLE_STUDENT =
            "omr_student";

    static final String COLUMN_STUDENT_ID =
            "student_id";
    static final String COLUMN_REGISTRATION =
            "registration";
    static final String COLUMN_STUDENT_NAME =
            "name";
    static final String COLUMN_STUDENT_PAYLOAD =
            "payload";

    static final String TABLE_CLASS_GROUP =
            "omr_class_group";

    static final String COLUMN_CLASS_GROUP_ID =
            "class_group_id";
    static final String COLUMN_CLASS_NAME =
            "name";
    static final String COLUMN_CLASS_GRADE_LEVEL =
            "grade_level";
    static final String COLUMN_CLASS_SCHOOL_YEAR =
            "school_year";
    static final String COLUMN_CLASS_PAYLOAD =
            "payload";

    static final String TABLE_CLASS_ENROLLMENT =
            "omr_class_enrollment";

    static final String COLUMN_ENROLLMENT_CLASS_GROUP_ID =
            "class_group_id";
    static final String COLUMN_ENROLLMENT_STUDENT_ID =
            "student_id";
    static final String COLUMN_ROSTER_POSITION =
            "roster_position";
    static final String COLUMN_ENROLLMENT_PAYLOAD =
            "payload";

    OmrSchoolDatabase(
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
    public void onConfigure(
            SQLiteDatabase database
    ) {
        super.onConfigure(database);
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(
            SQLiteDatabase database
    ) {
        createAssessmentTable(database);
        createStudentTable(database);
        createClassGroupTable(database);
        createClassEnrollmentTable(database);
    }

    static void createAssessmentTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT + " ("
                        + COLUMN_ASSESSMENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ASSESSMENT_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ASSESSMENT_VERSION + " > 0), "
                        + COLUMN_TITLE
                        + " TEXT NOT NULL, "
                        + COLUMN_SUBJECT
                        + " TEXT NOT NULL, "
                        + COLUMN_GRADE_LEVEL
                        + " TEXT NOT NULL, "
                        + COLUMN_ACADEMIC_PERIOD
                        + " TEXT NOT NULL, "
                        + COLUMN_SCHOOL_YEAR
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_SCHOOL_YEAR + " > 0), "
                        + COLUMN_ANSWER_KEY_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ANSWER_KEY_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ANSWER_KEY_VERSION + " > 0), "
                        + COLUMN_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "PRIMARY KEY ("
                        + COLUMN_ASSESSMENT_ID
                        + ", "
                        + COLUMN_ASSESSMENT_VERSION
                        + ")"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_assessment_school_order"
                        + " ON " + TABLE_ASSESSMENT
                        + " (" + COLUMN_SCHOOL_YEAR + " DESC, "
                        + COLUMN_TITLE + " COLLATE NOCASE, "
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + " DESC)"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_assessment_answer_key"
                        + " ON " + TABLE_ASSESSMENT
                        + " (" + COLUMN_ANSWER_KEY_ID
                        + ", " + COLUMN_ANSWER_KEY_VERSION + ")"
        );
    }

    static void createStudentTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_STUDENT + " ("
                        + COLUMN_STUDENT_ID
                        + " TEXT PRIMARY KEY NOT NULL, "
                        + COLUMN_REGISTRATION
                        + " TEXT NOT NULL COLLATE NOCASE UNIQUE, "
                        + COLUMN_STUDENT_NAME
                        + " TEXT NOT NULL, "
                        + COLUMN_STUDENT_PAYLOAD
                        + " BLOB NOT NULL"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_student_school_order"
                        + " ON " + TABLE_STUDENT
                        + " (" + COLUMN_STUDENT_NAME
                        + " COLLATE NOCASE, "
                        + COLUMN_REGISTRATION
                        + " COLLATE NOCASE, "
                        + COLUMN_STUDENT_ID + ")"
        );
    }

    static void createClassGroupTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_CLASS_GROUP + " ("
                        + COLUMN_CLASS_GROUP_ID
                        + " TEXT PRIMARY KEY NOT NULL, "
                        + COLUMN_CLASS_NAME
                        + " TEXT NOT NULL, "
                        + COLUMN_CLASS_GRADE_LEVEL
                        + " TEXT NOT NULL, "
                        + COLUMN_CLASS_SCHOOL_YEAR
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_CLASS_SCHOOL_YEAR + " > 0), "
                        + COLUMN_CLASS_PAYLOAD
                        + " BLOB NOT NULL"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_class_group_school_order"
                        + " ON " + TABLE_CLASS_GROUP
                        + " (" + COLUMN_CLASS_SCHOOL_YEAR
                        + " DESC, "
                        + COLUMN_CLASS_NAME
                        + " COLLATE NOCASE, "
                        + COLUMN_CLASS_GROUP_ID + ")"
        );
    }

    static void createClassEnrollmentTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_CLASS_ENROLLMENT + " ("
                        + COLUMN_ENROLLMENT_CLASS_GROUP_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ENROLLMENT_STUDENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ROSTER_POSITION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ROSTER_POSITION + " > 0), "
                        + COLUMN_ENROLLMENT_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "PRIMARY KEY ("
                        + COLUMN_ENROLLMENT_CLASS_GROUP_ID
                        + ", "
                        + COLUMN_ENROLLMENT_STUDENT_ID
                        + "), "
                        + "UNIQUE ("
                        + COLUMN_ENROLLMENT_CLASS_GROUP_ID
                        + ", "
                        + COLUMN_ROSTER_POSITION
                        + "), "
                        + "FOREIGN KEY ("
                        + COLUMN_ENROLLMENT_CLASS_GROUP_ID
                        + ") REFERENCES "
                        + TABLE_CLASS_GROUP
                        + " (" + COLUMN_CLASS_GROUP_ID + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT, "
                        + "FOREIGN KEY ("
                        + COLUMN_ENROLLMENT_STUDENT_ID
                        + ") REFERENCES "
                        + TABLE_STUDENT
                        + " (" + COLUMN_STUDENT_ID + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_class_enrollment_student"
                        + " ON " + TABLE_CLASS_ENROLLMENT
                        + " (" + COLUMN_ENROLLMENT_STUDENT_ID
                        + ", "
                        + COLUMN_ENROLLMENT_CLASS_GROUP_ID + ")"
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
    ) {
        if (oldVersion < 2) {
            createStudentTable(database);
        }

        if (oldVersion < 3) {
            createClassGroupTable(database);
            createClassEnrollmentTable(database);
        }
    }
}
