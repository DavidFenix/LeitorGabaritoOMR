package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentBatchRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Acrescenta versões de provas ao SQLite como uma única transação. */
public final class OmrSQLiteAssessmentBatchRepository
        implements OmrAssessmentBatchRepository, AutoCloseable {

    private final OmrSchoolDatabase database;
    private final OmrAssessmentDefinitionBinaryCodec codec;

    public OmrSQLiteAssessmentBatchRepository(Context context) {
        this(context, OmrSchoolDatabase.DEFAULT_DATABASE_NAME);
    }

    public OmrSQLiteAssessmentBatchRepository(
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
    public void saveAllNew(
            List<OmrAssessmentDefinition> assessments
    ) {
        validateBatch(assessments);

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();

        writableDatabase.beginTransaction();

        try {
            for (OmrAssessmentDefinition assessment
                    : assessments) {

                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase.TABLE_ASSESSMENT,
                        null,
                        valuesOf(assessment)
                );
            }

            writableDatabase.setTransactionSuccessful();

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "O lote contém uma versão de prova já armazenada.",
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

    private static void validateBatch(
            List<OmrAssessmentDefinition> assessments
    ) {
        if (assessments == null) {
            throw new IllegalArgumentException(
                    "A coleção de provas é obrigatória."
            );
        }

        if (assessments.isEmpty()) {
            throw new IllegalArgumentException(
                    "O lote de provas não pode estar vazio."
            );
        }

        Set<String> identities = new HashSet<>();

        for (OmrAssessmentDefinition assessment
                : assessments) {

            if (assessment == null) {
                throw new IllegalArgumentException(
                        "O lote de provas não pode conter valores nulos."
                );
            }

            String identity = assessment.getAssessmentId()
                    + "\u0000"
                    + assessment.getVersion();

            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "O lote possui uma versão de prova repetida."
                );
            }
        }
    }
}
