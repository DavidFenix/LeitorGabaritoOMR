package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Banco local dos dados escolares associados ao núcleo OMR.
 *
 * As tabelas de provas, alunos, turmas, aplicações, participantes e
 * tentativas evoluem por migrações neste mesmo banco, evitando
 * armazenamentos isolados difíceis de consultar em conjunto.
 */
final class OmrSchoolDatabase extends SQLiteOpenHelper {

    static final String DEFAULT_DATABASE_NAME =
            "omr_school.db";

    static final int DATABASE_VERSION = 6;

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

    static final String TABLE_ASSESSMENT_SCORING_POLICY =
            "omr_assessment_scoring_policy";

    static final String COLUMN_POLICY_ID =
            "policy_id";
    static final String COLUMN_POLICY_VERSION =
            "policy_version";
    static final String COLUMN_POLICY_TYPE =
            "policy_type";
    static final String COLUMN_POLICY_BASIS =
            "policy_basis";
    static final String COLUMN_MAXIMUM_SCORE =
            "maximum_score";
    static final String COLUMN_DECIMAL_PLACES =
            "decimal_places";
    static final String COLUMN_ROUNDING_MODE =
            "rounding_mode";
    static final String COLUMN_POLICY_PAYLOAD =
            "payload";

    static final String TABLE_ASSESSMENT_STRUCTURE =
            "omr_assessment_structure";

    static final String COLUMN_STRUCTURE_QUESTION_COUNT =
            "question_count";
    static final String COLUMN_STRUCTURE_PAYLOAD =
            "payload";

    static final String TABLE_ASSESSMENT_BLOCK =
            "omr_assessment_block";

    static final String COLUMN_BLOCK_ID =
            "block_id";
    static final String COLUMN_BLOCK_ORDER =
            "block_order";
    static final String COLUMN_AREA_NAME =
            "area_name";
    static final String COLUMN_COMPONENT_CODE =
            "component_code";
    static final String COLUMN_COMPONENT_NAME =
            "component_name";
    static final String COLUMN_FIRST_QUESTION_POSITION =
            "first_question_position";
    static final String COLUMN_BLOCK_QUESTION_COUNT =
            "question_count";
    static final String COLUMN_BLOCK_PAYLOAD =
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

    static final String TABLE_ASSESSMENT_APPLICATION =
            "omr_assessment_application";

    static final String COLUMN_APPLICATION_ID =
            "application_id";
    static final String COLUMN_APPLICATION_CREATED_AT =
            "created_at_epoch_millis";
    static final String COLUMN_APPLICATION_PAYLOAD =
            "payload";

    static final String TABLE_ASSESSMENT_PARTICIPANT =
            "omr_assessment_participant";

    static final String COLUMN_PARTICIPANT_STUDENT_NAME =
            "student_name";
    static final String COLUMN_PARTICIPANT_CLASS_NAME =
            "class_name";
    static final String COLUMN_PARTICIPANT_PAYLOAD =
            "payload";

    static final String TABLE_ASSESSMENT_ATTEMPT =
            "omr_assessment_attempt";

    static final String COLUMN_ATTEMPT_ID =
            "attempt_id";
    static final String COLUMN_ATTEMPT_REVISION =
            "revision";
    static final String COLUMN_HISTORY_RECORD_ID =
            "history_record_id";
    static final String COLUMN_ATTEMPT_RECORDED_AT =
            "recorded_at_epoch_millis";
    static final String COLUMN_ATTEMPT_PAYLOAD =
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
        createAssessmentScoringPolicyTable(database);
        createAssessmentStructureTable(database);
        createAssessmentBlockTable(database);
        createStudentTable(database);
        createClassGroupTable(database);
        createClassEnrollmentTable(database);
        createAssessmentApplicationTable(database);
        createAssessmentParticipantTable(database);
        createAssessmentAttemptTable(database);
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

    static void createAssessmentScoringPolicyTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT_SCORING_POLICY + " ("
                        + COLUMN_POLICY_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_POLICY_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_POLICY_VERSION + " > 0), "
                        + COLUMN_POLICY_TYPE
                        + " TEXT NOT NULL, "
                        + COLUMN_POLICY_BASIS
                        + " TEXT NOT NULL, "
                        + COLUMN_MAXIMUM_SCORE
                        + " TEXT NOT NULL, "
                        + COLUMN_DECIMAL_PLACES
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_DECIMAL_PLACES
                        + " >= 0 AND "
                        + COLUMN_DECIMAL_PLACES + " <= 6), "
                        + COLUMN_ROUNDING_MODE
                        + " TEXT NOT NULL, "
                        + COLUMN_POLICY_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "PRIMARY KEY ("
                        + COLUMN_POLICY_ID + ", "
                        + COLUMN_POLICY_VERSION + ")"
                        + ")"
        );
    }

    static void createAssessmentStructureTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT_STRUCTURE + " ("
                        + COLUMN_ASSESSMENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ASSESSMENT_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ASSESSMENT_VERSION + " > 0), "
                        + COLUMN_STRUCTURE_QUESTION_COUNT
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_STRUCTURE_QUESTION_COUNT + " > 0), "
                        + COLUMN_STRUCTURE_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "PRIMARY KEY ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + "), "
                        + "FOREIGN KEY ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION
                        + ") REFERENCES "
                        + TABLE_ASSESSMENT + " ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT"
                        + ")"
        );
    }

    static void createAssessmentBlockTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT_BLOCK + " ("
                        + COLUMN_ASSESSMENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ASSESSMENT_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ASSESSMENT_VERSION + " > 0), "
                        + COLUMN_BLOCK_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_BLOCK_ORDER
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_BLOCK_ORDER + " > 0), "
                        + COLUMN_AREA_NAME
                        + " TEXT, "
                        + COLUMN_COMPONENT_CODE
                        + " TEXT, "
                        + COLUMN_COMPONENT_NAME
                        + " TEXT NOT NULL, "
                        + COLUMN_FIRST_QUESTION_POSITION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_FIRST_QUESTION_POSITION + " > 0), "
                        + COLUMN_BLOCK_QUESTION_COUNT
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_BLOCK_QUESTION_COUNT + " > 0), "
                        + COLUMN_POLICY_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_POLICY_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_POLICY_VERSION + " > 0), "
                        + COLUMN_BLOCK_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "PRIMARY KEY ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ", "
                        + COLUMN_BLOCK_ID + "), "
                        + "UNIQUE ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ", "
                        + COLUMN_BLOCK_ORDER + "), "
                        + "FOREIGN KEY ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION
                        + ") REFERENCES "
                        + TABLE_ASSESSMENT_STRUCTURE + " ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT, "
                        + "FOREIGN KEY ("
                        + COLUMN_POLICY_ID + ", "
                        + COLUMN_POLICY_VERSION
                        + ") REFERENCES "
                        + TABLE_ASSESSMENT_SCORING_POLICY + " ("
                        + COLUMN_POLICY_ID + ", "
                        + COLUMN_POLICY_VERSION + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_assessment_block_component"
                        + " ON " + TABLE_ASSESSMENT_BLOCK
                        + " (" + COLUMN_COMPONENT_NAME
                        + " COLLATE NOCASE, "
                        + COLUMN_AREA_NAME + " COLLATE NOCASE, "
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ")"
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

    static void createAssessmentApplicationTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT_APPLICATION + " ("
                        + COLUMN_APPLICATION_ID
                        + " TEXT PRIMARY KEY NOT NULL, "
                        + COLUMN_ASSESSMENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ASSESSMENT_VERSION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ASSESSMENT_VERSION + " > 0), "
                        + COLUMN_CLASS_GROUP_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_APPLICATION_CREATED_AT
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_APPLICATION_CREATED_AT + " > 0), "
                        + COLUMN_APPLICATION_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "FOREIGN KEY ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION
                        + ") REFERENCES "
                        + TABLE_ASSESSMENT + " ("
                        + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT, "
                        + "FOREIGN KEY ("
                        + COLUMN_CLASS_GROUP_ID
                        + ") REFERENCES "
                        + TABLE_CLASS_GROUP
                        + " (" + COLUMN_CLASS_GROUP_ID + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_application_class"
                        + " ON " + TABLE_ASSESSMENT_APPLICATION
                        + " (" + COLUMN_CLASS_GROUP_ID + ", "
                        + COLUMN_APPLICATION_CREATED_AT + " DESC, "
                        + COLUMN_APPLICATION_ID + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_application_assessment"
                        + " ON " + TABLE_ASSESSMENT_APPLICATION
                        + " (" + COLUMN_ASSESSMENT_ID + ", "
                        + COLUMN_ASSESSMENT_VERSION + ", "
                        + COLUMN_APPLICATION_CREATED_AT + " DESC, "
                        + COLUMN_APPLICATION_ID + ")"
        );
    }

    static void createAssessmentParticipantTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT_PARTICIPANT + " ("
                        + COLUMN_APPLICATION_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_STUDENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_REGISTRATION
                        + " TEXT NOT NULL COLLATE NOCASE, "
                        + COLUMN_PARTICIPANT_STUDENT_NAME
                        + " TEXT NOT NULL, "
                        + COLUMN_PARTICIPANT_CLASS_NAME
                        + " TEXT NOT NULL, "
                        + COLUMN_ROSTER_POSITION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ROSTER_POSITION + " > 0), "
                        + COLUMN_PARTICIPANT_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "PRIMARY KEY ("
                        + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_STUDENT_ID + "), "
                        + "UNIQUE ("
                        + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_REGISTRATION + "), "
                        + "UNIQUE ("
                        + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_ROSTER_POSITION + "), "
                        + "FOREIGN KEY ("
                        + COLUMN_APPLICATION_ID
                        + ") REFERENCES "
                        + TABLE_ASSESSMENT_APPLICATION
                        + " (" + COLUMN_APPLICATION_ID + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_participant_student"
                        + " ON " + TABLE_ASSESSMENT_PARTICIPANT
                        + " (" + COLUMN_STUDENT_ID + ", "
                        + COLUMN_APPLICATION_ID + ")"
        );
    }

    static void createAssessmentAttemptTable(
            SQLiteDatabase database
    ) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS "
                        + TABLE_ASSESSMENT_ATTEMPT + " ("
                        + COLUMN_ATTEMPT_ID
                        + " TEXT PRIMARY KEY NOT NULL, "
                        + COLUMN_APPLICATION_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_STUDENT_ID
                        + " TEXT NOT NULL, "
                        + COLUMN_ATTEMPT_REVISION
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ATTEMPT_REVISION + " > 0), "
                        + COLUMN_HISTORY_RECORD_ID
                        + " TEXT NOT NULL UNIQUE, "
                        + COLUMN_ATTEMPT_RECORDED_AT
                        + " INTEGER NOT NULL CHECK ("
                        + COLUMN_ATTEMPT_RECORDED_AT + " > 0), "
                        + COLUMN_ATTEMPT_PAYLOAD
                        + " BLOB NOT NULL, "
                        + "UNIQUE ("
                        + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_STUDENT_ID + ", "
                        + COLUMN_ATTEMPT_REVISION + "), "
                        + "FOREIGN KEY ("
                        + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_STUDENT_ID
                        + ") REFERENCES "
                        + TABLE_ASSESSMENT_PARTICIPANT + " ("
                        + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_STUDENT_ID + ")"
                        + " ON UPDATE RESTRICT ON DELETE RESTRICT"
                        + ")"
        );

        database.execSQL(
                "CREATE INDEX IF NOT EXISTS "
                        + "index_omr_attempt_application"
                        + " ON " + TABLE_ASSESSMENT_ATTEMPT
                        + " (" + COLUMN_APPLICATION_ID + ", "
                        + COLUMN_STUDENT_ID + ", "
                        + COLUMN_ATTEMPT_REVISION + ")"
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

        if (oldVersion < 4) {
            createAssessmentApplicationTable(database);
            createAssessmentParticipantTable(database);
        }

        if (oldVersion < 5) {
            createAssessmentAttemptTable(database);
        }

        if (oldVersion < 6) {
            createAssessmentScoringPolicyTable(database);
            createAssessmentStructureTable(database);
            createAssessmentBlockTable(database);
        }
    }
}
