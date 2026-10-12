package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationBatchRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Salva aplicações e fotografias de chamada em uma única transação SQLite. */
public final class OmrSQLiteAssessmentApplicationBatchRepository
        implements OmrAssessmentApplicationBatchRepository,
        AutoCloseable {

    private final OmrSchoolDatabase database;
    private final OmrAssessmentApplicationBinaryCodec applicationCodec;
    private final OmrAssessmentParticipantBinaryCodec participantCodec;

    public OmrSQLiteAssessmentApplicationBatchRepository(
            Context context
    ) {
        this(context, OmrSchoolDatabase.DEFAULT_DATABASE_NAME);
    }

    public OmrSQLiteAssessmentApplicationBatchRepository(
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

        Context applicationContext = context.getApplicationContext();
        database = new OmrSchoolDatabase(
                applicationContext == null
                        ? context
                        : applicationContext,
                databaseName.trim()
        );
        applicationCodec = new OmrAssessmentApplicationBinaryCodec();
        participantCodec = new OmrAssessmentParticipantBinaryCodec();
    }

    @Override
    public void saveAllNew(
            List<OmrAssessmentApplication> applications,
            List<OmrAssessmentParticipant> participants
    ) {
        validateBatch(applications, participants);

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();
        writableDatabase.beginTransaction();

        try {
            for (OmrAssessmentApplication application
                    : applications) {
                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase
                                .TABLE_ASSESSMENT_APPLICATION,
                        null,
                        applicationValues(application)
                );
            }

            for (OmrAssessmentParticipant participant
                    : participants) {
                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase
                                .TABLE_ASSESSMENT_PARTICIPANT,
                        null,
                        participantValues(participant)
                );
            }

            writableDatabase.setTransactionSuccessful();

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "O lote contém aplicação, prova, turma ou participante "
                            + "incompatível ou já armazenado.",
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

    private ContentValues applicationValues(
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
                applicationCodec.encode(application)
        );
        return values;
    }

    private ContentValues participantValues(
            OmrAssessmentParticipant participant
    ) {
        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_ID,
                participant.getApplicationId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_ID,
                participant.getStudentId()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_REGISTRATION,
                participant.getRegistration()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_PARTICIPANT_STUDENT_NAME,
                participant.getStudentName()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_PARTICIPANT_CLASS_NAME,
                participant.getClassName()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_ROSTER_POSITION,
                participant.getRosterPosition()
        );
        values.put(
                OmrSchoolDatabase.COLUMN_PARTICIPANT_PAYLOAD,
                participantCodec.encode(participant)
        );
        return values;
    }

    private static void validateBatch(
            List<OmrAssessmentApplication> applications,
            List<OmrAssessmentParticipant> participants
    ) {
        if (applications == null || participants == null) {
            throw new IllegalArgumentException(
                    "As coleções do lote de aplicações são obrigatórias."
            );
        }
        if (applications.isEmpty() || participants.isEmpty()) {
            throw new IllegalArgumentException(
                    "O lote de aplicações não pode estar vazio."
            );
        }

        Set<String> applicationIds = new HashSet<>();
        Map<String, Integer> participantCount = new HashMap<>();

        for (OmrAssessmentApplication application : applications) {
            if (application == null
                    || !applicationIds.add(
                    application.getApplicationId()
            )) {
                throw new IllegalArgumentException(
                        "O lote possui aplicação nula ou repetida."
                );
            }
            participantCount.put(application.getApplicationId(), 0);
        }

        Set<String> studentKeys = new HashSet<>();
        Set<String> registrationKeys = new HashSet<>();
        Set<String> positionKeys = new HashSet<>();

        for (OmrAssessmentParticipant participant : participants) {
            if (participant == null
                    || !applicationIds.contains(
                    participant.getApplicationId()
            )) {
                throw new IllegalArgumentException(
                        "Todo participante deve pertencer a uma aplicação "
                                + "do lote."
                );
            }

            String applicationId = participant.getApplicationId();
            String studentKey = applicationId
                    + "\u0000" + participant.getStudentId();
            String registrationKey = applicationId
                    + "\u0000"
                    + participant.getRegistration()
                    .toUpperCase(Locale.ROOT);
            String positionKey = applicationId
                    + "\u0000" + participant.getRosterPosition();

            if (!studentKeys.add(studentKey)
                    || !registrationKeys.add(registrationKey)
                    || !positionKeys.add(positionKey)) {
                throw new IllegalArgumentException(
                        "O lote possui aluno, matrícula ou posição repetida "
                                + "na mesma aplicação."
                );
            }

            participantCount.put(
                    applicationId,
                    participantCount.get(applicationId) + 1
            );
        }

        for (Map.Entry<String, Integer> entry
                : participantCount.entrySet()) {
            if (entry.getValue() == 0) {
                throw new IllegalArgumentException(
                        "Toda aplicação deve possuir participantes: "
                                + entry.getKey()
                );
            }
        }
    }
}
