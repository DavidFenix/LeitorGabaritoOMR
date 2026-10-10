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
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentApplicationRepositoriesInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteAssessmentApplicationRepository
            applicationRepository;
    private OmrSQLiteAssessmentParticipantRepository
            participantRepository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        testDatabaseName =
                "omr_school_application_test_"
                        + UUID.randomUUID()
                        + ".db";

        applicationContext.deleteDatabase(testDatabaseName);
        openRepositories();
    }

    @After
    public void tearDown() {
        closeRepositories();
        applicationContext.deleteDatabase(testDatabaseName);
    }

    @Test
    public void emptyRepositoriesReturnEmptyAndNullResults() {
        assertTrue(applicationRepository.loadAll().isEmpty());
        assertTrue(applicationRepository
                .loadForClassGroup("class-001")
                .isEmpty());
        assertTrue(applicationRepository
                .loadForAssessment("assessment-001", 1)
                .isEmpty());
        assertNull(applicationRepository.findByIdOrNull(
                "application-001"
        ));

        assertTrue(participantRepository
                .loadForApplication("application-001")
                .isEmpty());
        assertNull(participantRepository.findOrNull(
                "application-001",
                "student-001"
        ));
    }

    @Test
    public void saveAndFindPreserveCompleteApplication() {
        prepareAssessmentAndClass();
        OmrAssessmentApplication original = application();

        assertTrue(applicationRepository.save(original));

        assertCompleteApplication(
                original,
                applicationRepository.findByIdOrNull(
                        " application-001 "
                )
        );
    }

    @Test
    public void duplicateApplicationIdentityPreservesOriginal() {
        prepareAssessmentAndClass();
        OmrAssessmentApplication original = application();

        OmrAssessmentApplication conflicting =
                new OmrAssessmentApplication(
                        original.getApplicationId(),
                        original.getAssessmentId(),
                        original.getAssessmentVersion(),
                        original.getClassGroupId(),
                        original.getCreatedAtEpochMillis() + 999L
                );

        assertTrue(applicationRepository.save(original));
        assertFalse(applicationRepository.save(conflicting));

        assertEquals(1, applicationRepository.loadAll().size());
        assertCompleteApplication(
                original,
                applicationRepository.findByIdOrNull(
                        original.getApplicationId()
                )
        );
    }

    @Test
    public void applicationListsUseDocumentedOrderAndFilters() {
        saveAssessment(assessment("assessment-001", 1));
        saveAssessment(assessment("assessment-001", 2));
        saveClassGroup(classGroup("class-a", "9º A"));
        saveClassGroup(classGroup("class-b", "9º B"));

        applicationRepository.save(new OmrAssessmentApplication(
                "application-old",
                "assessment-001",
                1,
                "class-a",
                1_000L
        ));
        applicationRepository.save(new OmrAssessmentApplication(
                "application-middle",
                "assessment-001",
                2,
                "class-b",
                2_000L
        ));
        applicationRepository.save(new OmrAssessmentApplication(
                "application-new",
                "assessment-001",
                2,
                "class-a",
                3_000L
        ));

        List<OmrAssessmentApplication> all =
                applicationRepository.loadAll();

        assertEquals(3, all.size());
        assertEquals("application-new", all.get(0).getApplicationId());
        assertEquals("application-middle", all.get(1).getApplicationId());
        assertEquals("application-old", all.get(2).getApplicationId());

        List<OmrAssessmentApplication> classApplications =
                applicationRepository.loadForClassGroup(" class-a ");

        assertEquals(2, classApplications.size());
        assertEquals(
                "application-new",
                classApplications.get(0).getApplicationId()
        );
        assertEquals(
                "application-old",
                classApplications.get(1).getApplicationId()
        );

        List<OmrAssessmentApplication> assessmentApplications =
                applicationRepository.loadForAssessment(
                        " assessment-001 ",
                        2
                );

        assertEquals(2, assessmentApplications.size());
        assertEquals(
                "application-new",
                assessmentApplications.get(0).getApplicationId()
        );
        assertEquals(
                "application-middle",
                assessmentApplications.get(1).getApplicationId()
        );
    }

    @Test
    public void applicationPersistsAcrossRepositoryReopen() {
        prepareAssessmentAndClass();
        OmrAssessmentApplication original = application();
        applicationRepository.save(original);

        closeRepositories();
        openRepositories();

        assertCompleteApplication(
                original,
                applicationRepository.findByIdOrNull(
                        original.getApplicationId()
                )
        );
    }

    @Test
    public void applicationRequiresExistingAssessmentAndClass() {
        OmrAssessmentApplication original = application();

        expectIllegalArgument(() ->
                applicationRepository.save(original)
        );
        assertTrue(applicationRepository.loadAll().isEmpty());

        saveAssessment(assessment());

        expectIllegalArgument(() ->
                applicationRepository.save(original)
        );
        assertTrue(applicationRepository.loadAll().isEmpty());

        saveClassGroup(classGroup());
        assertTrue(applicationRepository.save(original));
    }

    @Test
    public void applicationInvalidArgumentsReturnEmptyOrAreRejected() {
        expectIllegalArgument(() ->
                applicationRepository.save(null)
        );

        assertNull(applicationRepository.findByIdOrNull(null));
        assertNull(applicationRepository.findByIdOrNull(" "));
        assertTrue(applicationRepository
                .loadForClassGroup(null)
                .isEmpty());
        assertTrue(applicationRepository
                .loadForClassGroup(" ")
                .isEmpty());
        assertTrue(applicationRepository
                .loadForAssessment(null, 1)
                .isEmpty());
        assertTrue(applicationRepository
                .loadForAssessment("assessment-001", 0)
                .isEmpty());
    }

    @Test
    public void applicationListsAreImmutable() {
        prepareAssessmentAndClass();
        applicationRepository.save(application());

        expectUnsupportedOperation(() ->
                applicationRepository.loadAll().clear()
        );
        expectUnsupportedOperation(() ->
                applicationRepository
                        .loadForClassGroup("class-001")
                        .clear()
        );
        expectUnsupportedOperation(() ->
                applicationRepository
                        .loadForAssessment("assessment-001", 2)
                        .clear()
        );
    }

    @Test
    public void damagedApplicationsAreSkippedWithoutHidingHealthyRows() {
        prepareAssessmentAndClass();

        applicationRepository.save(application(
                "application-tampered",
                1_000L
        ));
        applicationRepository.save(application(
                "application-corrupted",
                2_000L
        ));
        applicationRepository.save(application(
                "application-healthy",
                3_000L
        ));

        closeRepositories();

        ContentValues tampered = new ContentValues();
        tampered.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_CREATED_AT,
                7_000L
        );
        updateRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_APPLICATION,
                tampered,
                OmrSchoolDatabase.COLUMN_APPLICATION_ID + " = ?",
                new String[]{"application-tampered"}
        );

        ContentValues corrupted = new ContentValues();
        corrupted.put(
                OmrSchoolDatabase.COLUMN_APPLICATION_PAYLOAD,
                new byte[]{1, 2, 3}
        );
        updateRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_APPLICATION,
                corrupted,
                OmrSchoolDatabase.COLUMN_APPLICATION_ID + " = ?",
                new String[]{"application-corrupted"}
        );

        openRepositories();

        List<OmrAssessmentApplication> stored =
                applicationRepository.loadAll();

        assertEquals(1, stored.size());
        assertEquals(
                "application-healthy",
                stored.get(0).getApplicationId()
        );
        assertNull(applicationRepository.findByIdOrNull(
                "application-tampered"
        ));
        assertNull(applicationRepository.findByIdOrNull(
                "application-corrupted"
        ));
    }

    @Test
    public void saveRosterPreservesCompleteOrderedParticipants() {
        prepareApplication();
        List<OmrAssessmentParticipant> original = Arrays.asList(
                participant("student-002", "MAT-002", "Bruno", 2),
                participant("student-001", "MAT-001", "Ana", 1)
        );

        assertTrue(participantRepository.saveRoster(
                " application-001 ",
                original
        ));

        List<OmrAssessmentParticipant> restored =
                participantRepository.loadForApplication(
                        " application-001 "
                );

        assertEquals(2, restored.size());
        assertCompleteParticipant(original.get(1), restored.get(0));
        assertCompleteParticipant(original.get(0), restored.get(1));
        assertCompleteParticipant(
                original.get(1),
                participantRepository.findOrNull(
                        " application-001 ",
                        " student-001 "
                )
        );
    }

    @Test
    public void rosterPersistsAcrossRepositoryReopen() {
        prepareApplication();
        OmrAssessmentParticipant original = participant();

        participantRepository.saveRoster(
                "application-001",
                Collections.singletonList(original)
        );

        closeRepositories();
        openRepositories();

        assertCompleteParticipant(
                original,
                participantRepository.findOrNull(
                        "application-001",
                        "student-001"
                )
        );
    }

    @Test
    public void secondRosterDoesNotRewriteFrozenSnapshot() {
        prepareApplication();
        OmrAssessmentParticipant original = participant();
        OmrAssessmentParticipant conflicting = participant(
                "student-999",
                "MAT-999",
                "Outro aluno",
                9
        );

        assertTrue(participantRepository.saveRoster(
                "application-001",
                Collections.singletonList(original)
        ));
        assertFalse(participantRepository.saveRoster(
                "application-001",
                Collections.singletonList(conflicting)
        ));

        List<OmrAssessmentParticipant> stored =
                participantRepository.loadForApplication(
                        "application-001"
                );

        assertEquals(1, stored.size());
        assertCompleteParticipant(original, stored.get(0));
    }

    @Test
    public void rosterRequiresExistingApplicationAndRollsBack() {
        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        Collections.singletonList(participant())
                )
        );

        assertTrue(participantRepository
                .loadForApplication("application-001")
                .isEmpty());
    }

    @Test
    public void rosterValidationRejectsInvalidCollectionsAtomically() {
        prepareApplication();

        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        " ",
                        Collections.singletonList(participant())
                )
        );
        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        null
                )
        );
        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        Collections.emptyList()
                )
        );

        List<OmrAssessmentParticipant> withNull =
                new ArrayList<>();
        withNull.add(null);
        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        withNull
                )
        );

        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        Collections.singletonList(
                                new OmrAssessmentParticipant(
                                        "application-other",
                                        "student-001",
                                        "MAT-001",
                                        "Ana",
                                        "9º A",
                                        1
                                )
                        )
                )
        );

        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        Arrays.asList(
                                participant(
                                        "student-001",
                                        "MAT-001",
                                        "Ana",
                                        1
                                ),
                                participant(
                                        "student-001",
                                        "MAT-002",
                                        "Ana repetida",
                                        2
                                )
                        )
                )
        );

        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        Arrays.asList(
                                participant(
                                        "student-001",
                                        "MAT-001",
                                        "Ana",
                                        1
                                ),
                                participant(
                                        "student-002",
                                        "mat-001",
                                        "Bruno",
                                        2
                                )
                        )
                )
        );

        expectIllegalArgument(() ->
                participantRepository.saveRoster(
                        "application-001",
                        Arrays.asList(
                                participant(
                                        "student-001",
                                        "MAT-001",
                                        "Ana",
                                        1
                                ),
                                participant(
                                        "student-002",
                                        "MAT-002",
                                        "Bruno",
                                        1
                                )
                        )
                )
        );

        assertTrue(participantRepository
                .loadForApplication("application-001")
                .isEmpty());
    }

    @Test
    public void laterStudentAndClassChangesDoNotRewriteRosterSnapshot() {
        prepareApplication();
        OmrAssessmentParticipant snapshot = participant();

        participantRepository.saveRoster(
                "application-001",
                Collections.singletonList(snapshot)
        );

        saveStudent(new OmrStudentProfile(
                "student-001",
                "MAT-001",
                "Nome alterado depois da aplicação"
        ));
        saveClassGroup(new OmrClassGroup(
                "class-001",
                "9º A renomeada",
                "9º ano",
                2026
        ));

        OmrAssessmentParticipant restored =
                participantRepository.findOrNull(
                        "application-001",
                        "student-001"
                );

        assertCompleteParticipant(snapshot, restored);
        assertEquals("Ana Beatriz", restored.getStudentName());
        assertEquals("9º A", restored.getClassName());
    }

    @Test
    public void participantQueriesAreDefensiveAndImmutable() {
        prepareApplication();
        participantRepository.saveRoster(
                "application-001",
                Collections.singletonList(participant())
        );

        assertTrue(participantRepository
                .loadForApplication(null)
                .isEmpty());
        assertTrue(participantRepository
                .loadForApplication(" ")
                .isEmpty());
        assertNull(participantRepository.findOrNull(
                null,
                "student-001"
        ));
        assertNull(participantRepository.findOrNull(
                "application-001",
                " "
        ));
        assertNull(participantRepository.findOrNull(
                "application-001",
                "missing"
        ));
        expectUnsupportedOperation(() ->
                participantRepository
                        .loadForApplication("application-001")
                        .clear()
        );
    }

    @Test
    public void damagedParticipantsAreSkippedWithoutHidingHealthyRows() {
        prepareApplication();
        participantRepository.saveRoster(
                "application-001",
                Arrays.asList(
                        participant(
                                "student-tampered",
                                "MAT-010",
                                "Adulterado",
                                1
                        ),
                        participant(
                                "student-corrupted",
                                "MAT-011",
                                "Corrompido",
                                2
                        ),
                        participant(
                                "student-healthy",
                                "MAT-012",
                                "Saudável",
                                3
                        )
                )
        );

        closeRepositories();

        ContentValues tampered = new ContentValues();
        tampered.put(
                OmrSchoolDatabase.COLUMN_PARTICIPANT_STUDENT_NAME,
                "Nome adulterado"
        );
        updateRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_PARTICIPANT,
                tampered,
                OmrSchoolDatabase.COLUMN_STUDENT_ID + " = ?",
                new String[]{"student-tampered"}
        );

        ContentValues corrupted = new ContentValues();
        corrupted.put(
                OmrSchoolDatabase.COLUMN_PARTICIPANT_PAYLOAD,
                new byte[]{1, 2, 3}
        );
        updateRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_PARTICIPANT,
                corrupted,
                OmrSchoolDatabase.COLUMN_STUDENT_ID + " = ?",
                new String[]{"student-corrupted"}
        );

        openRepositories();

        List<OmrAssessmentParticipant> stored =
                participantRepository.loadForApplication(
                        "application-001"
                );

        assertEquals(1, stored.size());
        assertEquals(
                "student-healthy",
                stored.get(0).getStudentId()
        );
        assertNull(participantRepository.findOrNull(
                "application-001",
                "student-tampered"
        ));
        assertNull(participantRepository.findOrNull(
                "application-001",
                "student-corrupted"
        ));
    }

    @Test
    public void repositoriesRejectInvalidConstructorData() {
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentApplicationRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentApplicationRepository(
                        applicationContext,
                        " "
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentParticipantRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentParticipantRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void migrationFromVersionThreePreservesExistingSchoolData() {
        closeRepositories();
        applicationContext.deleteDatabase(testDatabaseName);

        OmrAssessmentDefinition assessment = assessment();
        OmrStudentProfile student = student();
        OmrClassGroup classGroup = classGroup();
        OmrClassEnrollment enrollment = enrollment();

        try (LegacyVersionThreeDatabase legacyDatabase =
                     new LegacyVersionThreeDatabase(
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
        }

        openRepositories();

        OmrAssessmentApplication application = application();
        assertTrue(applicationRepository.save(application));
        assertTrue(participantRepository.saveRoster(
                application.getApplicationId(),
                Collections.singletonList(participant())
        ));

        closeRepositories();
        assertDatabaseVersion(OmrSchoolDatabase.DATABASE_VERSION);

        try (OmrSQLiteAssessmentRepository assessmentRepository =
                     new OmrSQLiteAssessmentRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteStudentRepository studentRepository =
                     new OmrSQLiteStudentRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteClassGroupRepository classRepository =
                     new OmrSQLiteClassGroupRepository(
                             applicationContext,
                             testDatabaseName
                     );
             OmrSQLiteClassEnrollmentRepository enrollmentRepository =
                     new OmrSQLiteClassEnrollmentRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            assertEquals(
                    assessment,
                    assessmentRepository.findOrNull(
                            assessment.getAssessmentId(),
                            assessment.getVersion()
                    )
            );
            assertCompleteStudent(
                    student,
                    studentRepository.findByIdOrNull(
                            student.getStudentId()
                    )
            );
            assertCompleteClassGroup(
                    classGroup,
                    classRepository.findByIdOrNull(
                            classGroup.getClassGroupId()
                    )
            );
            assertCompleteEnrollment(
                    enrollment,
                    enrollmentRepository.findOrNull(
                            enrollment.getClassGroupId(),
                            enrollment.getStudentId()
                    )
            );
        }

        openRepositories();
        assertCompleteApplication(
                application,
                applicationRepository.findByIdOrNull(
                        application.getApplicationId()
                )
        );
        assertCompleteParticipant(
                participant(),
                participantRepository.findOrNull(
                        "application-001",
                        "student-001"
                )
        );
    }

    private void openRepositories() {
        applicationRepository =
                new OmrSQLiteAssessmentApplicationRepository(
                        applicationContext,
                        testDatabaseName
                );
        participantRepository =
                new OmrSQLiteAssessmentParticipantRepository(
                        applicationContext,
                        testDatabaseName
                );
    }

    private void closeRepositories() {
        if (participantRepository != null) {
            participantRepository.close();
            participantRepository = null;
        }

        if (applicationRepository != null) {
            applicationRepository.close();
            applicationRepository = null;
        }
    }

    private void prepareAssessmentAndClass() {
        saveAssessment(assessment());
        saveClassGroup(classGroup());
    }

    private void prepareApplication() {
        prepareAssessmentAndClass();
        assertTrue(applicationRepository.save(application()));
    }

    private void saveAssessment(
            OmrAssessmentDefinition assessment
    ) {
        try (OmrSQLiteAssessmentRepository repository =
                     new OmrSQLiteAssessmentRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            repository.save(assessment);
        }
    }

    private void saveStudent(OmrStudentProfile student) {
        try (OmrSQLiteStudentRepository repository =
                     new OmrSQLiteStudentRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            repository.save(student);
        }
    }

    private void saveClassGroup(OmrClassGroup classGroup) {
        try (OmrSQLiteClassGroupRepository repository =
                     new OmrSQLiteClassGroupRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            repository.save(classGroup);
        }
    }

    private void updateRow(
            String table,
            ContentValues values,
            String whereClause,
            String[] whereArguments
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
                    table,
                    values,
                    whereClause,
                    whereArguments
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

    private OmrAssessmentDefinition assessment() {
        return assessment("assessment-001", 2);
    }

    private OmrAssessmentDefinition assessment(
            String assessmentId,
            int version
    ) {
        return new OmrAssessmentDefinition(
                assessmentId,
                version,
                "Prova de Matemática",
                "Matemática",
                "9º ano",
                "3º bimestre",
                2026,
                "answer-key-001",
                version
        );
    }

    private OmrClassGroup classGroup() {
        return classGroup("class-001", "9º A");
    }

    private OmrClassGroup classGroup(
            String classGroupId,
            String name
    ) {
        return new OmrClassGroup(
                classGroupId,
                name,
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

    private OmrStudentProfile student() {
        return new OmrStudentProfile(
                "student-001",
                "MAT-001",
                "Ana Beatriz"
        );
    }

    private OmrAssessmentApplication application() {
        return application("application-001", 2_000L);
    }

    private OmrAssessmentApplication application(
            String applicationId,
            long createdAt
    ) {
        return new OmrAssessmentApplication(
                applicationId,
                "assessment-001",
                2,
                "class-001",
                createdAt
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

    private void assertCompleteStudent(
            OmrStudentProfile expected,
            OmrStudentProfile actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getRegistration(), actual.getRegistration());
        assertEquals(expected.getName(), actual.getName());
    }

    private void assertCompleteClassGroup(
            OmrClassGroup expected,
            OmrClassGroup actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getGradeLevel(), actual.getGradeLevel());
        assertEquals(expected.getSchoolYear(), actual.getSchoolYear());
    }

    private void assertCompleteEnrollment(
            OmrClassEnrollment expected,
            OmrClassEnrollment actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
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

    private static final class LegacyVersionThreeDatabase
            extends SQLiteOpenHelper {

        LegacyVersionThreeDatabase(
                Context context,
                String databaseName
        ) {
            super(context, databaseName, null, 3);
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
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase database,
                int oldVersion,
                int newVersion
        ) {
            // Helper de teste que representa exatamente a versão 3.
        }
    }
}
