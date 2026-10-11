package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentAttemptRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteAssessmentAttemptRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        testDatabaseName =
                "omr_school_attempt_test_"
                        + UUID.randomUUID()
                        + ".db";

        applicationContext.deleteDatabase(testDatabaseName);
        openRepository();
    }

    @After
    public void tearDown() {
        closeRepository();
        applicationContext.deleteDatabase(testDatabaseName);
    }

    @Test
    public void emptyRepositoryReturnsEmptyAndNullResults() {
        assertTrue(repository
                .loadForApplication("application-001")
                .isEmpty());
        assertTrue(repository
                .loadForParticipant(
                        "application-001",
                        "student-001"
                )
                .isEmpty());
        assertNull(repository.findLatestForParticipantOrNull(
                "application-001",
                "student-001"
        ));
        assertNull(repository.findByIdOrNull("attempt-001"));
        assertNull(repository.findByHistoryRecordIdOrNull(
                "history-001"
        ));
    }

    @Test
    public void saveAndFindPreserveCompleteAttempt() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        OmrAssessmentAttempt original = attempt();

        assertTrue(repository.save(original));

        assertCompleteAttempt(
                original,
                repository.findByIdOrNull(" attempt-001 ")
        );
    }

    @Test
    public void duplicateAttemptIdentityPreservesOriginal() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        OmrAssessmentAttempt original = attempt();
        OmrAssessmentAttempt conflicting =
                new OmrAssessmentAttempt(
                        "attempt-001",
                        "application-001",
                        "student-001",
                        2,
                        "history-conflicting",
                        9_000L
                );

        assertTrue(repository.save(original));
        assertFalse(repository.save(conflicting));

        assertEquals(
                1,
                repository.loadForApplication(
                        "application-001"
                ).size()
        );
        assertCompleteAttempt(
                original,
                repository.findByIdOrNull("attempt-001")
        );
    }

    @Test
    public void participantRevisionsAreOrderedAndLatestIsResolved() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );

        repository.save(attempt(
                "attempt-003",
                "application-001",
                "student-001",
                3,
                "history-003",
                3_000L
        ));
        repository.save(attempt(
                "attempt-001",
                "application-001",
                "student-001",
                1,
                "history-001",
                1_000L
        ));
        repository.save(attempt(
                "attempt-002",
                "application-001",
                "student-001",
                2,
                "history-002",
                2_000L
        ));

        List<OmrAssessmentAttempt> attempts =
                repository.loadForParticipant(
                        " application-001 ",
                        " student-001 "
                );

        assertEquals(3, attempts.size());
        assertEquals(1, attempts.get(0).getRevision());
        assertEquals(2, attempts.get(1).getRevision());
        assertEquals(3, attempts.get(2).getRevision());

        OmrAssessmentAttempt latest =
                repository.findLatestForParticipantOrNull(
                        "application-001",
                        "student-001"
                );

        assertNotNull(latest);
        assertEquals("attempt-003", latest.getAttemptId());
    }

    @Test
    public void applicationListUsesStudentAndRevisionOrder() {
        OmrAssessmentParticipant studentA = participant(
                "student-a",
                "MAT-A",
                "Ana",
                1
        );
        OmrAssessmentParticipant studentB = participant(
                "student-b",
                "MAT-B",
                "Bruno",
                2
        );

        prepareApplicationWithRoster(Arrays.asList(
                studentB,
                studentA
        ));

        repository.save(attempt(
                "attempt-b1",
                "application-001",
                "student-b",
                1,
                "history-b1",
                1_000L
        ));
        repository.save(attempt(
                "attempt-a2",
                "application-001",
                "student-a",
                2,
                "history-a2",
                3_000L
        ));
        repository.save(attempt(
                "attempt-a1",
                "application-001",
                "student-a",
                1,
                "history-a1",
                2_000L
        ));

        List<OmrAssessmentAttempt> attempts =
                repository.loadForApplication(
                        " application-001 "
                );

        assertEquals(3, attempts.size());
        assertEquals("attempt-a1", attempts.get(0).getAttemptId());
        assertEquals("attempt-a2", attempts.get(1).getAttemptId());
        assertEquals("attempt-b1", attempts.get(2).getAttemptId());
    }

    @Test
    public void findsAttemptByUniqueHistoryRecord() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        OmrAssessmentAttempt original = attempt();
        repository.save(original);

        assertCompleteAttempt(
                original,
                repository.findByHistoryRecordIdOrNull(
                        " history-001 "
                )
        );
    }

    @Test
    public void attemptPersistsAcrossRepositoryReopen() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        OmrAssessmentAttempt original = attempt();
        repository.save(original);

        closeRepository();
        openRepository();

        assertCompleteAttempt(
                original,
                repository.findByIdOrNull("attempt-001")
        );
    }

    @Test
    public void attemptRequiresExistingParticipant() {
        expectIllegalArgument(() -> repository.save(attempt()));

        assertTrue(repository
                .loadForApplication("application-001")
                .isEmpty());

        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        assertTrue(repository.save(attempt()));
    }

    @Test
    public void duplicateRevisionForParticipantIsRejected() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        OmrAssessmentAttempt first = attempt();
        OmrAssessmentAttempt duplicateRevision = attempt(
                "attempt-002",
                "application-001",
                "student-001",
                1,
                "history-002",
                2_001L
        );

        assertTrue(repository.save(first));
        expectIllegalArgument(() ->
                repository.save(duplicateRevision)
        );

        List<OmrAssessmentAttempt> stored =
                repository.loadForParticipant(
                        "application-001",
                        "student-001"
                );

        assertEquals(1, stored.size());
        assertCompleteAttempt(first, stored.get(0));
    }

    @Test
    public void duplicateHistoryRecordIsRejected() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        OmrAssessmentAttempt first = attempt();
        OmrAssessmentAttempt duplicateHistory = attempt(
                "attempt-002",
                "application-001",
                "student-001",
                2,
                "history-001",
                2_001L
        );

        assertTrue(repository.save(first));
        expectIllegalArgument(() ->
                repository.save(duplicateHistory)
        );

        assertEquals(
                1,
                repository.loadForApplication(
                        "application-001"
                ).size()
        );
    }

    @Test
    public void sameRevisionIsAllowedForDifferentParticipants() {
        OmrAssessmentParticipant studentA = participant(
                "student-a",
                "MAT-A",
                "Ana",
                1
        );
        OmrAssessmentParticipant studentB = participant(
                "student-b",
                "MAT-B",
                "Bruno",
                2
        );
        prepareApplicationWithRoster(Arrays.asList(
                studentA,
                studentB
        ));

        assertTrue(repository.save(attempt(
                "attempt-a1",
                "application-001",
                "student-a",
                1,
                "history-a1",
                1_000L
        )));
        assertTrue(repository.save(attempt(
                "attempt-b1",
                "application-001",
                "student-b",
                1,
                "history-b1",
                1_001L
        )));

        assertEquals(
                2,
                repository.loadForApplication(
                        "application-001"
                ).size()
        );
    }

    @Test
    public void invalidArgumentsReturnEmptyOrAreRejected() {
        expectIllegalArgument(() -> repository.save(null));

        assertTrue(repository.loadForApplication(null).isEmpty());
        assertTrue(repository.loadForApplication(" ").isEmpty());
        assertTrue(repository.loadForParticipant(
                null,
                "student-001"
        ).isEmpty());
        assertTrue(repository.loadForParticipant(
                "application-001",
                " "
        ).isEmpty());
        assertNull(repository.findLatestForParticipantOrNull(
                " ",
                "student-001"
        ));
        assertNull(repository.findByIdOrNull(null));
        assertNull(repository.findByIdOrNull(" "));
        assertNull(repository.findByHistoryRecordIdOrNull(null));
        assertNull(repository.findByHistoryRecordIdOrNull(" "));
    }

    @Test
    public void returnedListsAreImmutable() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );
        repository.save(attempt());

        expectUnsupportedOperation(() ->
                repository.loadForApplication(
                        "application-001"
                ).clear()
        );
        expectUnsupportedOperation(() ->
                repository.loadForParticipant(
                        "application-001",
                        "student-001"
                ).clear()
        );
    }

    @Test
    public void damagedAttemptsAreSkippedAndLatestFallsBack() {
        prepareApplicationWithRoster(
                Collections.singletonList(participant())
        );

        repository.save(attempt(
                "attempt-001",
                "application-001",
                "student-001",
                1,
                "history-001",
                1_000L
        ));
        repository.save(attempt(
                "attempt-002",
                "application-001",
                "student-001",
                2,
                "history-002",
                2_000L
        ));
        repository.save(attempt(
                "attempt-003",
                "application-001",
                "student-001",
                3,
                "history-003",
                3_000L
        ));

        closeRepository();

        ContentValues tampered = new ContentValues();
        tampered.put(
                OmrSchoolDatabase.COLUMN_ATTEMPT_RECORDED_AT,
                7_000L
        );
        updateRow(tampered, "attempt-002");

        ContentValues corrupted = new ContentValues();
        corrupted.put(
                OmrSchoolDatabase.COLUMN_ATTEMPT_PAYLOAD,
                new byte[]{1, 2, 3}
        );
        updateRow(corrupted, "attempt-003");

        openRepository();

        List<OmrAssessmentAttempt> stored =
                repository.loadForParticipant(
                        "application-001",
                        "student-001"
                );

        assertEquals(1, stored.size());
        assertEquals("attempt-001", stored.get(0).getAttemptId());
        assertEquals(
                "attempt-001",
                repository.findLatestForParticipantOrNull(
                        "application-001",
                        "student-001"
                ).getAttemptId()
        );
        assertNull(repository.findByIdOrNull("attempt-002"));
        assertNull(repository.findByIdOrNull("attempt-003"));
    }

    @Test
    public void repositoryRejectsInvalidConstructorData() {
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentAttemptRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentAttemptRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void attemptsFromDifferentApplicationsRemainIsolated() {
        prepareAssessmentAndClass();

        OmrAssessmentApplication firstApplication = application();
        OmrAssessmentApplication secondApplication =
                new OmrAssessmentApplication(
                        "application-002",
                        "assessment-001",
                        2,
                        "class-001",
                        2_001L
                );

        try (OmrSQLiteAssessmentApplicationRepository applications =
                     new OmrSQLiteAssessmentApplicationRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteAssessmentParticipantRepository participants =
                     new OmrSQLiteAssessmentParticipantRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            applications.save(firstApplication);
            applications.save(secondApplication);
            participants.saveRoster(
                    "application-001",
                    Collections.singletonList(participant())
            );
            participants.saveRoster(
                    "application-002",
                    Collections.singletonList(
                            new OmrAssessmentParticipant(
                                    "application-002",
                                    "student-001",
                                    "MAT-001",
                                    "Ana Beatriz",
                                    "9º A",
                                    1
                            )
                    )
            );
        }

        repository.save(attempt());
        repository.save(attempt(
                "attempt-002",
                "application-002",
                "student-001",
                1,
                "history-002",
                2_001L
        ));

        assertEquals(
                1,
                repository.loadForApplication(
                        "application-001"
                ).size()
        );
        assertEquals(
                1,
                repository.loadForApplication(
                        "application-002"
                ).size()
        );
    }

    @Test
    public void migrationFromVersionFourPreservesCompleteSchoolGraph() {
        closeRepository();
        applicationContext.deleteDatabase(testDatabaseName);

        OmrAssessmentDefinition assessment = assessment();
        OmrStudentProfile student = student();
        OmrClassGroup classGroup = classGroup();
        OmrClassEnrollment enrollment = enrollment();
        OmrAssessmentApplication application = application();
        OmrAssessmentParticipant participant = participant();

        try (LegacyVersionFourDatabase legacyDatabase =
                     new LegacyVersionFourDatabase(
                             applicationContext,
                             testDatabaseName
                     )) {

            SQLiteDatabase writable =
                    legacyDatabase.getWritableDatabase();

            assertTrue(writable.insertOrThrow(
                    OmrSchoolDatabase.TABLE_ASSESSMENT,
                    null,
                    assessmentValues(assessment)
            ) != -1L);
            assertTrue(writable.insertOrThrow(
                    OmrSchoolDatabase.TABLE_STUDENT,
                    null,
                    studentValues(student)
            ) != -1L);
            assertTrue(writable.insertOrThrow(
                    OmrSchoolDatabase.TABLE_CLASS_GROUP,
                    null,
                    classGroupValues(classGroup)
            ) != -1L);
            assertTrue(writable.insertOrThrow(
                    OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                    null,
                    enrollmentValues(enrollment)
            ) != -1L);
            assertTrue(writable.insertOrThrow(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_APPLICATION,
                    null,
                    applicationValues(application)
            ) != -1L);
            assertTrue(writable.insertOrThrow(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_PARTICIPANT,
                    null,
                    participantValues(participant)
            ) != -1L);
        }

        openRepository();
        OmrAssessmentAttempt attempt = attempt();
        assertTrue(repository.save(attempt));
        closeRepository();

        assertDatabaseVersion(OmrSchoolDatabase.DATABASE_VERSION);

        try (OmrSQLiteAssessmentRepository assessments =
                     new OmrSQLiteAssessmentRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteStudentRepository students =
                     new OmrSQLiteStudentRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteClassGroupRepository classes =
                     new OmrSQLiteClassGroupRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteClassEnrollmentRepository enrollments =
                     new OmrSQLiteClassEnrollmentRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteAssessmentApplicationRepository applications =
                     new OmrSQLiteAssessmentApplicationRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteAssessmentParticipantRepository participants =
                     new OmrSQLiteAssessmentParticipantRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            assertEquals(
                    assessment,
                    assessments.findOrNull(
                            assessment.getAssessmentId(),
                            assessment.getVersion()
                    )
            );
            assertEquals(
                    student,
                    students.findByIdOrNull(student.getStudentId())
            );
            assertEquals(
                    classGroup,
                    classes.findByIdOrNull(classGroup.getClassGroupId())
            );
            assertEquals(
                    enrollment,
                    enrollments.findOrNull(
                            enrollment.getClassGroupId(),
                            enrollment.getStudentId()
                    )
            );
            assertCompleteApplication(
                    application,
                    applications.findByIdOrNull(
                            application.getApplicationId()
                    )
            );
            assertCompleteParticipant(
                    participant,
                    participants.findOrNull(
                            participant.getApplicationId(),
                            participant.getStudentId()
                    )
            );
        }

        openRepository();
        assertCompleteAttempt(
                attempt,
                repository.findByIdOrNull(attempt.getAttemptId())
        );
    }

    private void openRepository() {
        repository = new OmrSQLiteAssessmentAttemptRepository(
                applicationContext,
                testDatabaseName
        );
    }

    private void closeRepository() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    private void prepareApplicationWithRoster(
            List<OmrAssessmentParticipant> participants
    ) {
        prepareAssessmentAndClass();

        try (OmrSQLiteAssessmentApplicationRepository applications =
                     new OmrSQLiteAssessmentApplicationRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteAssessmentParticipantRepository roster =
                     new OmrSQLiteAssessmentParticipantRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            assertTrue(applications.save(application()));
            assertTrue(roster.saveRoster(
                    "application-001",
                    participants
            ));
        }
    }

    private void prepareAssessmentAndClass() {
        try (OmrSQLiteAssessmentRepository assessments =
                     new OmrSQLiteAssessmentRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteClassGroupRepository classes =
                     new OmrSQLiteClassGroupRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            assessments.save(assessment());
            classes.save(classGroup());
        }
    }

    private void updateRow(
            ContentValues values,
            String attemptId
    ) {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                applicationContext
                        .getDatabasePath(testDatabaseName)
                        .getPath(),
                null,
                SQLiteDatabase.OPEN_READWRITE
        );

        try {
            int updated = database.update(
                    OmrSchoolDatabase.TABLE_ASSESSMENT_ATTEMPT,
                    values,
                    OmrSchoolDatabase.COLUMN_ATTEMPT_ID + " = ?",
                    new String[]{attemptId}
            );

            assertEquals(1, updated);
        } finally {
            database.close();
        }
    }

    private void assertDatabaseVersion(int expectedVersion) {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                applicationContext
                        .getDatabasePath(testDatabaseName)
                        .getPath(),
                null,
                SQLiteDatabase.OPEN_READONLY
        );

        try {
            assertEquals(expectedVersion, database.getVersion());
        } finally {
            database.close();
        }
    }

    private ContentValues assessmentValues(
            OmrAssessmentDefinition assessment
    ) {
        ContentValues values = new ContentValues();
        values.put(OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                assessment.getAssessmentId());
        values.put(OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
                assessment.getVersion());
        values.put(OmrSchoolDatabase.COLUMN_TITLE,
                assessment.getTitle());
        values.put(OmrSchoolDatabase.COLUMN_SUBJECT,
                assessment.getSubject());
        values.put(OmrSchoolDatabase.COLUMN_GRADE_LEVEL,
                assessment.getGradeLevel());
        values.put(OmrSchoolDatabase.COLUMN_ACADEMIC_PERIOD,
                assessment.getAcademicPeriod());
        values.put(OmrSchoolDatabase.COLUMN_SCHOOL_YEAR,
                assessment.getSchoolYear());
        values.put(OmrSchoolDatabase.COLUMN_ANSWER_KEY_ID,
                assessment.getAnswerKeyId());
        values.put(OmrSchoolDatabase.COLUMN_ANSWER_KEY_VERSION,
                assessment.getAnswerKeyVersion());
        values.put(OmrSchoolDatabase.COLUMN_PAYLOAD,
                new OmrAssessmentDefinitionBinaryCodec()
                        .encode(assessment));
        return values;
    }

    private ContentValues studentValues(OmrStudentProfile student) {
        ContentValues values = new ContentValues();
        values.put(OmrSchoolDatabase.COLUMN_STUDENT_ID,
                student.getStudentId());
        values.put(OmrSchoolDatabase.COLUMN_REGISTRATION,
                student.getRegistration());
        values.put(OmrSchoolDatabase.COLUMN_STUDENT_NAME,
                student.getName());
        values.put(OmrSchoolDatabase.COLUMN_STUDENT_PAYLOAD,
                new OmrStudentProfileBinaryCodec().encode(student));
        return values;
    }

    private ContentValues classGroupValues(OmrClassGroup classGroup) {
        ContentValues values = new ContentValues();
        values.put(OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID,
                classGroup.getClassGroupId());
        values.put(OmrSchoolDatabase.COLUMN_CLASS_NAME,
                classGroup.getName());
        values.put(OmrSchoolDatabase.COLUMN_CLASS_GRADE_LEVEL,
                classGroup.getGradeLevel());
        values.put(OmrSchoolDatabase.COLUMN_CLASS_SCHOOL_YEAR,
                classGroup.getSchoolYear());
        values.put(OmrSchoolDatabase.COLUMN_CLASS_PAYLOAD,
                new OmrClassGroupBinaryCodec().encode(classGroup));
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
                new OmrClassEnrollmentBinaryCodec()
                        .encode(enrollment)
        );
        return values;
    }

    private ContentValues applicationValues(
            OmrAssessmentApplication application
    ) {
        ContentValues values = new ContentValues();
        values.put(OmrSchoolDatabase.COLUMN_APPLICATION_ID,
                application.getApplicationId());
        values.put(OmrSchoolDatabase.COLUMN_ASSESSMENT_ID,
                application.getAssessmentId());
        values.put(OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION,
                application.getAssessmentVersion());
        values.put(OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID,
                application.getClassGroupId());
        values.put(OmrSchoolDatabase.COLUMN_APPLICATION_CREATED_AT,
                application.getCreatedAtEpochMillis());
        values.put(OmrSchoolDatabase.COLUMN_APPLICATION_PAYLOAD,
                new OmrAssessmentApplicationBinaryCodec()
                        .encode(application));
        return values;
    }

    private ContentValues participantValues(
            OmrAssessmentParticipant participant
    ) {
        ContentValues values = new ContentValues();
        values.put(OmrSchoolDatabase.COLUMN_APPLICATION_ID,
                participant.getApplicationId());
        values.put(OmrSchoolDatabase.COLUMN_STUDENT_ID,
                participant.getStudentId());
        values.put(OmrSchoolDatabase.COLUMN_REGISTRATION,
                participant.getRegistration());
        values.put(OmrSchoolDatabase.COLUMN_PARTICIPANT_STUDENT_NAME,
                participant.getStudentName());
        values.put(OmrSchoolDatabase.COLUMN_PARTICIPANT_CLASS_NAME,
                participant.getClassName());
        values.put(OmrSchoolDatabase.COLUMN_ROSTER_POSITION,
                participant.getRosterPosition());
        values.put(OmrSchoolDatabase.COLUMN_PARTICIPANT_PAYLOAD,
                new OmrAssessmentParticipantBinaryCodec()
                        .encode(participant));
        return values;
    }

    private OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-001",
                2,
                "Prova de Matemática",
                "Matemática",
                "9º ano",
                "3º bimestre",
                2026,
                "answer-key-001",
                2
        );
    }

    private OmrStudentProfile student() {
        return new OmrStudentProfile(
                "student-001",
                "MAT-001",
                "Ana Beatriz"
        );
    }

    private OmrClassGroup classGroup() {
        return new OmrClassGroup(
                "class-001",
                "9º A",
                "9º ano",
                2026
        );
    }

    private OmrClassEnrollment enrollment() {
        return new OmrClassEnrollment(
                "class-001",
                "student-001",
                1
        );
    }

    private OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-001",
                "assessment-001",
                2,
                "class-001",
                2_000L
        );
    }

    private OmrAssessmentParticipant participant() {
        return participant(
                "student-001",
                "MAT-001",
                "Ana Beatriz",
                1
        );
    }

    private OmrAssessmentParticipant participant(
            String studentId,
            String registration,
            String name,
            int rosterPosition
    ) {
        return new OmrAssessmentParticipant(
                "application-001",
                studentId,
                registration,
                name,
                "9º A",
                rosterPosition
        );
    }

    private OmrAssessmentAttempt attempt() {
        return attempt(
                "attempt-001",
                "application-001",
                "student-001",
                1,
                "history-001",
                2_000L
        );
    }

    private OmrAssessmentAttempt attempt(
            String attemptId,
            String applicationId,
            String studentId,
            int revision,
            String historyRecordId,
            long recordedAt
    ) {
        return new OmrAssessmentAttempt(
                attemptId,
                applicationId,
                studentId,
                revision,
                historyRecordId,
                recordedAt
        );
    }

    private void assertCompleteAttempt(
            OmrAssessmentAttempt expected,
            OmrAssessmentAttempt actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getApplicationId(), actual.getApplicationId());
        assertEquals(expected.getStudentId(), actual.getStudentId());
        assertEquals(expected.getRevision(), actual.getRevision());
        assertEquals(
                expected.getHistoryRecordId(),
                actual.getHistoryRecordId()
        );
        assertEquals(
                expected.getRecordedAtEpochMillis(),
                actual.getRecordedAtEpochMillis()
        );
    }

    private void assertCompleteApplication(
            OmrAssessmentApplication expected,
            OmrAssessmentApplication actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getAssessmentId(), actual.getAssessmentId());
        assertEquals(
                expected.getAssessmentVersion(),
                actual.getAssessmentVersion()
        );
        assertEquals(expected.getClassGroupId(), actual.getClassGroupId());
        assertEquals(
                expected.getCreatedAtEpochMillis(),
                actual.getCreatedAtEpochMillis()
        );
    }

    private void assertCompleteParticipant(
            OmrAssessmentParticipant expected,
            OmrAssessmentParticipant actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getRegistration(), actual.getRegistration());
        assertEquals(expected.getStudentName(), actual.getStudentName());
        assertEquals(expected.getClassName(), actual.getClassName());
        assertEquals(
                expected.getRosterPosition(),
                actual.getRosterPosition()
        );
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private void expectUnsupportedOperation(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    private static final class LegacyVersionFourDatabase
            extends SQLiteOpenHelper {

        LegacyVersionFourDatabase(
                Context context,
                String databaseName
        ) {
            super(context, databaseName, null, 4);
        }

        @Override
        public void onConfigure(SQLiteDatabase database) {
            super.onConfigure(database);
            database.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            OmrSchoolDatabase.createAssessmentTable(database);
            OmrSchoolDatabase.createStudentTable(database);
            OmrSchoolDatabase.createClassGroupTable(database);
            OmrSchoolDatabase.createClassEnrollmentTable(database);
            OmrSchoolDatabase.createAssessmentApplicationTable(database);
            OmrSchoolDatabase.createAssessmentParticipantTable(database);
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase database,
                int oldVersion,
                int newVersion
        ) {
            // Helper de teste que representa exatamente a versão 4.
        }
    }
}
