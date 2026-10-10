package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
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

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteClassroomRepositoriesInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteClassGroupRepository classGroupRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        testDatabaseName =
                "omr_school_classroom_test_"
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
        assertTrue(classGroupRepository.loadAll().isEmpty());
        assertTrue(classGroupRepository
                .loadForSchoolYear(2026)
                .isEmpty());
        assertNull(classGroupRepository.findByIdOrNull(
                "class-001"
        ));

        assertTrue(enrollmentRepository
                .loadForClassGroup("class-001")
                .isEmpty());
        assertTrue(enrollmentRepository
                .loadForStudent("student-001")
                .isEmpty());
        assertNull(enrollmentRepository.findOrNull(
                "class-001",
                "student-001"
        ));
    }

    @Test
    public void saveAndFindPreserveCompleteClassGroup() {
        OmrClassGroup original = classGroup();

        classGroupRepository.save(original);

        assertCompleteClassGroup(
                original,
                classGroupRepository.findByIdOrNull(
                        " class-001 "
                )
        );
    }

    @Test
    public void savingSameClassIdUpdatesDescriptiveData() {
        classGroupRepository.save(classGroup());

        OmrClassGroup updated = new OmrClassGroup(
                "class-001",
                "9º A – Manhã",
                "Ensino Fundamental – Anos Finais",
                2027
        );

        classGroupRepository.save(updated);

        assertEquals(1, classGroupRepository.loadAll().size());
        assertCompleteClassGroup(
                updated,
                classGroupRepository.findByIdOrNull(
                        "class-001"
                )
        );
    }

    @Test
    public void classGroupsUseSchoolOrderAndYearFilter() {
        classGroupRepository.save(new OmrClassGroup(
                "class-c",
                "8º A",
                "8º ano",
                2027
        ));
        classGroupRepository.save(new OmrClassGroup(
                "class-b",
                "9º B",
                "9º ano",
                2026
        ));
        classGroupRepository.save(new OmrClassGroup(
                "class-a",
                "9º A",
                "9º ano",
                2026
        ));

        List<OmrClassGroup> all =
                classGroupRepository.loadAll();

        assertEquals(3, all.size());
        assertEquals("class-c", all.get(0).getClassGroupId());
        assertEquals("class-a", all.get(1).getClassGroupId());
        assertEquals("class-b", all.get(2).getClassGroupId());

        List<OmrClassGroup> from2026 =
                classGroupRepository.loadForSchoolYear(2026);

        assertEquals(2, from2026.size());
        assertEquals(
                "class-a",
                from2026.get(0).getClassGroupId()
        );
        assertEquals(
                "class-b",
                from2026.get(1).getClassGroupId()
        );
    }

    @Test
    public void enrollmentRequiresExistingClassGroupAndStudent() {
        OmrClassEnrollment enrollment = enrollment();

        expectIllegalArgument(() ->
                enrollmentRepository.save(enrollment)
        );

        classGroupRepository.save(classGroup());

        expectIllegalArgument(() ->
                enrollmentRepository.save(enrollment)
        );

        saveStudent(student());

        expectIllegalArgument(() -> enrollmentRepository.save(
                new OmrClassEnrollment(
                        "class-missing",
                        "student-001",
                        7
                )
        ));

        enrollmentRepository.save(enrollment);

        assertNotNull(enrollmentRepository.findOrNull(
                "class-001",
                "student-001"
        ));
    }

    @Test
    public void saveAndFindPreserveCompleteEnrollment() {
        prepareClassGroupAndStudent();
        OmrClassEnrollment original = enrollment();

        enrollmentRepository.save(original);

        assertCompleteEnrollment(
                original,
                enrollmentRepository.findOrNull(
                        " class-001 ",
                        " student-001 "
                )
        );
    }

    @Test
    public void savingSameEnrollmentUpdatesRosterPosition() {
        prepareClassGroupAndStudent();
        enrollmentRepository.save(enrollment());

        OmrClassEnrollment updated =
                new OmrClassEnrollment(
                        "class-001",
                        "student-001",
                        19
                );

        enrollmentRepository.save(updated);

        assertEquals(
                1,
                enrollmentRepository
                        .loadForClassGroup("class-001")
                        .size()
        );
        assertCompleteEnrollment(
                updated,
                enrollmentRepository.findOrNull(
                        "class-001",
                        "student-001"
                )
        );
    }

    @Test
    public void duplicateRosterPositionIsRejectedAndRolledBack() {
        classGroupRepository.save(classGroup());
        saveStudent(student(
                "student-001",
                "000001",
                "Ana"
        ));
        saveStudent(student(
                "student-002",
                "000002",
                "Bruno"
        ));

        OmrClassEnrollment first = new OmrClassEnrollment(
                "class-001",
                "student-001",
                1
        );
        OmrClassEnrollment second = new OmrClassEnrollment(
                "class-001",
                "student-002",
                2
        );

        enrollmentRepository.save(first);

        expectIllegalArgument(() -> enrollmentRepository.save(
                new OmrClassEnrollment(
                        "class-001",
                        "student-002",
                        1
                )
        ));

        assertNull(enrollmentRepository.findOrNull(
                "class-001",
                "student-002"
        ));

        enrollmentRepository.save(second);

        expectIllegalArgument(() -> enrollmentRepository.save(
                new OmrClassEnrollment(
                        "class-001",
                        "student-002",
                        1
                )
        ));

        assertCompleteEnrollment(
                second,
                enrollmentRepository.findOrNull(
                        "class-001",
                        "student-002"
                )
        );
    }

    @Test
    public void classRosterIsOrderedByPosition() {
        classGroupRepository.save(classGroup());
        saveStudent(student("student-a", "000001", "Ana"));
        saveStudent(student("student-b", "000002", "Bruno"));
        saveStudent(student("student-c", "000003", "Carla"));

        enrollmentRepository.save(new OmrClassEnrollment(
                "class-001",
                "student-c",
                12
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-001",
                "student-a",
                1
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-001",
                "student-b",
                7
        ));

        List<OmrClassEnrollment> roster =
                enrollmentRepository.loadForClassGroup(
                        "class-001"
                );

        assertEquals(3, roster.size());
        assertEquals(1, roster.get(0).getRosterPosition());
        assertEquals(7, roster.get(1).getRosterPosition());
        assertEquals(12, roster.get(2).getRosterPosition());
    }

    @Test
    public void studentEnrollmentsAreFoundAcrossClasses() {
        classGroupRepository.save(new OmrClassGroup(
                "class-b",
                "9º B",
                "9º ano",
                2026
        ));
        classGroupRepository.save(new OmrClassGroup(
                "class-a",
                "9º A",
                "9º ano",
                2026
        ));
        saveStudent(student());
        saveStudent(student(
                "student-002",
                "000002",
                "Bruno"
        ));

        enrollmentRepository.save(new OmrClassEnrollment(
                "class-b",
                "student-001",
                8
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-a",
                "student-001",
                7
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-a",
                "student-002",
                9
        ));

        List<OmrClassEnrollment> matches =
                enrollmentRepository.loadForStudent(
                        " student-001 "
                );

        assertEquals(2, matches.size());
        assertEquals(
                "class-a",
                matches.get(0).getClassGroupId()
        );
        assertEquals(
                "class-b",
                matches.get(1).getClassGroupId()
        );
    }

    @Test
    public void newInstancesReadPersistedClassAndEnrollment() {
        prepareClassGroupAndStudent();
        enrollmentRepository.save(enrollment());

        closeRepositories();
        openRepositories();

        assertCompleteClassGroup(
                classGroup(),
                classGroupRepository.findByIdOrNull(
                        "class-001"
                )
        );
        assertCompleteEnrollment(
                enrollment(),
                enrollmentRepository.findOrNull(
                        "class-001",
                        "student-001"
                )
        );
    }

    @Test
    public void returnedListsAreImmutableAndInvalidQueriesAreEmpty() {
        prepareClassGroupAndStudent();
        enrollmentRepository.save(enrollment());

        expectUnsupportedOperation(() ->
                classGroupRepository.loadAll().clear()
        );
        expectUnsupportedOperation(() ->
                classGroupRepository
                        .loadForSchoolYear(2026)
                        .clear()
        );
        expectUnsupportedOperation(() ->
                enrollmentRepository
                        .loadForClassGroup("class-001")
                        .clear()
        );
        expectUnsupportedOperation(() ->
                enrollmentRepository
                        .loadForStudent("student-001")
                        .clear()
        );

        assertTrue(classGroupRepository
                .loadForSchoolYear(0)
                .isEmpty());
        assertNull(classGroupRepository.findByIdOrNull(" "));
        assertTrue(enrollmentRepository
                .loadForClassGroup(null)
                .isEmpty());
        assertTrue(enrollmentRepository
                .loadForStudent(" ")
                .isEmpty());
        assertNull(enrollmentRepository.findOrNull(
                null,
                "student-001"
        ));
        assertNull(enrollmentRepository.findOrNull(
                "class-001",
                " "
        ));
    }

    @Test
    public void rejectsNullEntitiesAndInvalidConstructorData() {
        expectIllegalArgument(() ->
                classGroupRepository.save(null)
        );
        expectIllegalArgument(() ->
                enrollmentRepository.save(null)
        );

        expectIllegalArgument(() ->
                new OmrSQLiteClassGroupRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteClassGroupRepository(
                        applicationContext,
                        " "
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteClassEnrollmentRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteClassEnrollmentRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void tamperedClassMetadataIsSkippedWithoutHidingHealthyRows() {
        classGroupRepository.save(classGroup());
        classGroupRepository.save(new OmrClassGroup(
                "class-healthy",
                "8º A",
                "8º ano",
                2026
        ));

        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_CLASS_NAME,
                "Nome adulterado"
        );

        updateRow(
                OmrSchoolDatabase.TABLE_CLASS_GROUP,
                values,
                OmrSchoolDatabase.COLUMN_CLASS_GROUP_ID + " = ?",
                new String[]{"class-001"}
        );

        openRepositories();

        assertEquals(1, classGroupRepository.loadAll().size());
        assertNull(classGroupRepository.findByIdOrNull(
                "class-001"
        ));
        assertNotNull(classGroupRepository.findByIdOrNull(
                "class-healthy"
        ));
    }

    @Test
    public void corruptedEnrollmentIsSkippedWithoutHidingHealthyRows() {
        classGroupRepository.save(classGroup());
        saveStudent(student("student-001", "000001", "Ana"));
        saveStudent(student("student-002", "000002", "Bruno"));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-001",
                "student-001",
                1
        ));
        enrollmentRepository.save(new OmrClassEnrollment(
                "class-001",
                "student-002",
                2
        ));

        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_ENROLLMENT_PAYLOAD,
                new byte[]{1, 2, 3}
        );

        updateRow(
                OmrSchoolDatabase.TABLE_CLASS_ENROLLMENT,
                values,
                OmrSchoolDatabase
                        .COLUMN_ENROLLMENT_STUDENT_ID
                        + " = ?",
                new String[]{"student-001"}
        );

        openRepositories();

        List<OmrClassEnrollment> roster =
                enrollmentRepository.loadForClassGroup(
                        "class-001"
                );

        assertEquals(1, roster.size());
        assertEquals(
                "student-002",
                roster.get(0).getStudentId()
        );
        assertNull(enrollmentRepository.findOrNull(
                "class-001",
                "student-001"
        ));
    }

    @Test
    public void migrationFromVersionTwoPreservesExistingSchoolData() {
        closeRepositories();
        applicationContext.deleteDatabase(testDatabaseName);

        OmrAssessmentDefinition assessment = assessment();
        OmrStudentProfile student = student();

        try (LegacyVersionTwoDatabase legacyDatabase =
                     new LegacyVersionTwoDatabase(
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
        }

        openRepositories();

        OmrClassGroup classGroup = classGroup();
        OmrClassEnrollment enrollment = enrollment();

        classGroupRepository.save(classGroup);
        enrollmentRepository.save(enrollment);
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
                     )) {

            OmrAssessmentDefinition restoredAssessment =
                    assessmentRepository.findOrNull(
                            assessment.getAssessmentId(),
                            assessment.getVersion()
                    );

            assertNotNull(restoredAssessment);
            assertEquals(assessment, restoredAssessment);
            assertEquals(
                    assessment.getTitle(),
                    restoredAssessment.getTitle()
            );

            OmrStudentProfile restoredStudent =
                    studentRepository.findByIdOrNull(
                            student.getStudentId()
                    );

            assertCompleteStudent(student, restoredStudent);
        }

        openRepositories();

        assertCompleteClassGroup(
                classGroup,
                classGroupRepository.findByIdOrNull(
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

    private void openRepositories() {
        classGroupRepository =
                new OmrSQLiteClassGroupRepository(
                        applicationContext,
                        testDatabaseName
                );

        enrollmentRepository =
                new OmrSQLiteClassEnrollmentRepository(
                        applicationContext,
                        testDatabaseName
                );
    }

    private void closeRepositories() {
        if (enrollmentRepository != null) {
            enrollmentRepository.close();
            enrollmentRepository = null;
        }

        if (classGroupRepository != null) {
            classGroupRepository.close();
            classGroupRepository = null;
        }
    }

    private void prepareClassGroupAndStudent() {
        classGroupRepository.save(classGroup());
        saveStudent(student());
    }

    private void saveStudent(
            OmrStudentProfile student
    ) {
        try (OmrSQLiteStudentRepository studentRepository =
                     new OmrSQLiteStudentRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            studentRepository.save(student);
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

    private void assertDatabaseVersion(
            int expectedVersion
    ) {
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
                new OmrAssessmentDefinitionBinaryCodec()
                        .encode(assessment)
        );

        return values;
    }

    private ContentValues studentValues(
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
                new OmrStudentProfileBinaryCodec().encode(student)
        );

        return values;
    }

    private OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-before-v3",
                2,
                "Prova preservada na migração para v3",
                "Matemática",
                "9º ano",
                "3º bimestre",
                2026,
                "answer-key-before-v3",
                3
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
                7
        );
    }

    private OmrStudentProfile student() {
        return student(
                "student-001",
                "000123",
                "Ana Beatriz"
        );
    }

    private OmrStudentProfile student(
            String studentId,
            String registration,
            String name
    ) {
        return new OmrStudentProfile(
                studentId,
                registration,
                name
        );
    }

    private void assertCompleteClassGroup(
            OmrClassGroup expected,
            OmrClassGroup actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getName(), actual.getName());
        assertEquals(
                expected.getGradeLevel(),
                actual.getGradeLevel()
        );
        assertEquals(
                expected.getSchoolYear(),
                actual.getSchoolYear()
        );
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

    private void assertCompleteStudent(
            OmrStudentProfile expected,
            OmrStudentProfile actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getRegistration(),
                actual.getRegistration()
        );
        assertEquals(expected.getName(), actual.getName());
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private void expectUnsupportedOperation(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    private static final class LegacyVersionTwoDatabase
            extends SQLiteOpenHelper {

        LegacyVersionTwoDatabase(
                Context context,
                String databaseName
        ) {
            super(context, databaseName, null, 2);
        }

        @Override
        public void onCreate(
                SQLiteDatabase database
        ) {
            OmrSchoolDatabase.createAssessmentTable(database);
            OmrSchoolDatabase.createStudentTable(database);
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase database,
                int oldVersion,
                int newVersion
        ) {
            // Helper de teste que representa exatamente a versão 2.
        }
    }
}
