package com.example.leitorgabaritoomr.infrastructure.school;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Repositório SQLite das listas congeladas de participantes.
 */
public final class OmrSQLiteAssessmentParticipantRepository
        implements OmrAssessmentParticipantRepository,
        AutoCloseable {

    private static final String[] PROJECTION = {
            OmrSchoolDatabase.COLUMN_APPLICATION_ID,
            OmrSchoolDatabase.COLUMN_STUDENT_ID,
            OmrSchoolDatabase.COLUMN_REGISTRATION,
            OmrSchoolDatabase.COLUMN_PARTICIPANT_STUDENT_NAME,
            OmrSchoolDatabase.COLUMN_PARTICIPANT_CLASS_NAME,
            OmrSchoolDatabase.COLUMN_ROSTER_POSITION,
            OmrSchoolDatabase.COLUMN_PARTICIPANT_PAYLOAD
    };

    private static final String ROSTER_ORDER =
            OmrSchoolDatabase.COLUMN_ROSTER_POSITION
                    + " ASC, "
                    + OmrSchoolDatabase.COLUMN_STUDENT_ID
                    + " ASC";

    private final OmrSchoolDatabase database;
    private final OmrAssessmentParticipantBinaryCodec codec;

    public OmrSQLiteAssessmentParticipantRepository(
            Context context
    ) {
        this(
                context,
                OmrSchoolDatabase.DEFAULT_DATABASE_NAME
        );
    }

    OmrSQLiteAssessmentParticipantRepository(
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

        codec = new OmrAssessmentParticipantBinaryCodec();
    }

    @Override
    public boolean saveRoster(
            String applicationId,
            List<OmrAssessmentParticipant> participants
    ) {
        String normalizedApplicationId =
                requireApplicationId(applicationId);

        validateRoster(
                normalizedApplicationId,
                participants
        );

        SQLiteDatabase writableDatabase =
                database.getWritableDatabase();

        writableDatabase.beginTransaction();

        try {
            long existingCount = DatabaseUtils.queryNumEntries(
                    writableDatabase,
                    OmrSchoolDatabase
                            .TABLE_ASSESSMENT_PARTICIPANT,
                    OmrSchoolDatabase.COLUMN_APPLICATION_ID
                            + " = ?",
                    new String[]{normalizedApplicationId}
            );

            if (existingCount > 0L) {
                writableDatabase.setTransactionSuccessful();
                return false;
            }

            for (OmrAssessmentParticipant participant
                    : participants) {

                writableDatabase.insertOrThrow(
                        OmrSchoolDatabase
                                .TABLE_ASSESSMENT_PARTICIPANT,
                        null,
                        valuesOf(participant)
                );
            }

            writableDatabase.setTransactionSuccessful();
            return true;

        } catch (SQLiteConstraintException exception) {
            throw new IllegalArgumentException(
                    "A chamada exige uma aplicação cadastrada e "
                            + "identidades, matrículas e posições únicas.",
                    exception
            );

        } finally {
            writableDatabase.endTransaction();
        }
    }

    @Override
    public List<OmrAssessmentParticipant> loadForApplication(
            String applicationId
    ) {
        String normalizedId = normalizeOrNull(applicationId);

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return queryParticipants(
                OmrSchoolDatabase.COLUMN_APPLICATION_ID
                        + " = ?",
                new String[]{normalizedId},
                ROSTER_ORDER
        );
    }

    @Override
    public OmrAssessmentParticipant findOrNull(
            String applicationId,
            String studentId
    ) {
        String normalizedApplicationId =
                normalizeOrNull(applicationId);
        String normalizedStudentId = normalizeOrNull(studentId);

        if (normalizedApplicationId == null
                || normalizedStudentId == null) {
            return null;
        }

        List<OmrAssessmentParticipant> matches =
                queryParticipants(
                        OmrSchoolDatabase.COLUMN_APPLICATION_ID
                                + " = ? AND "
                                + OmrSchoolDatabase.COLUMN_STUDENT_ID
                                + " = ?",
                        new String[]{
                                normalizedApplicationId,
                                normalizedStudentId
                        },
                        null
                );

        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public void close() {
        database.close();
    }

    private ContentValues valuesOf(
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
                codec.encode(participant)
        );

        return values;
    }

    private List<OmrAssessmentParticipant> queryParticipants(
            String selection,
            String[] selectionArguments,
            String orderBy
    ) {
        List<OmrAssessmentParticipant> participants =
                new ArrayList<>();

        try (Cursor cursor = database
                .getReadableDatabase()
                .query(
                        OmrSchoolDatabase
                                .TABLE_ASSESSMENT_PARTICIPANT,
                        PROJECTION,
                        selection,
                        selectionArguments,
                        null,
                        null,
                        orderBy
                )) {

            while (cursor.moveToNext()) {
                OmrAssessmentParticipant participant =
                        decodeAndValidateOrNull(cursor);

                if (participant != null) {
                    participants.add(participant);
                }
            }
        }

        return Collections.unmodifiableList(participants);
    }

    private OmrAssessmentParticipant decodeAndValidateOrNull(
            Cursor cursor
    ) {
        try {
            byte[] payload = cursor.getBlob(
                    cursor.getColumnIndexOrThrow(
                            OmrSchoolDatabase
                                    .COLUMN_PARTICIPANT_PAYLOAD
                    )
            );

            OmrAssessmentParticipant participant =
                    codec.decode(payload);

            return metadataMatches(cursor, participant)
                    ? participant
                    : null;

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean metadataMatches(
            Cursor cursor,
            OmrAssessmentParticipant participant
    ) {
        return textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_APPLICATION_ID
        ).equals(participant.getApplicationId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_STUDENT_ID
        ).equals(participant.getStudentId())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_REGISTRATION
        ).equals(participant.getRegistration())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_PARTICIPANT_STUDENT_NAME
        ).equals(participant.getStudentName())
                && textAt(
                cursor,
                OmrSchoolDatabase.COLUMN_PARTICIPANT_CLASS_NAME
        ).equals(participant.getClassName())
                && intAt(
                cursor,
                OmrSchoolDatabase.COLUMN_ROSTER_POSITION
        ) == participant.getRosterPosition();
    }

    private static void validateRoster(
            String applicationId,
            List<OmrAssessmentParticipant> participants
    ) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException(
                    "A chamada deve possuir pelo menos um participante."
            );
        }

        Set<String> studentIds = new HashSet<>();
        Set<String> registrations = new HashSet<>();
        Set<Integer> rosterPositions = new HashSet<>();

        for (OmrAssessmentParticipant participant
                : participants) {

            if (participant == null) {
                throw new IllegalArgumentException(
                        "A chamada não pode conter participantes nulos."
                );
            }

            if (!participant.belongsToApplication(applicationId)) {
                throw new IllegalArgumentException(
                        "Todos os participantes devem pertencer "
                                + "à aplicação informada."
                );
            }

            if (!studentIds.add(participant.getStudentId())) {
                throw new IllegalArgumentException(
                        "Aluno repetido na chamada: "
                                + participant.getStudentId()
                );
            }

            String normalizedRegistration =
                    participant.getRegistration()
                            .toUpperCase(Locale.ROOT);

            if (!registrations.add(normalizedRegistration)) {
                throw new IllegalArgumentException(
                        "Matrícula repetida na chamada: "
                                + participant.getRegistration()
                );
            }

            if (!rosterPositions.add(
                    participant.getRosterPosition()
            )) {
                throw new IllegalArgumentException(
                        "Posição repetida na chamada: "
                                + participant.getRosterPosition()
                );
            }
        }
    }

    private static String requireApplicationId(String value) {
        String normalized = normalizeOrNull(value);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "O identificador da aplicação é obrigatório."
            );
        }

        return normalized;
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

    private static String normalizeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
