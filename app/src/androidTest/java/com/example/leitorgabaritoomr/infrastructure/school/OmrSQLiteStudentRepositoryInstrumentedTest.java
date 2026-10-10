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
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class OmrSQLiteStudentRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteStudentRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        testDatabaseName =
                "omr_school_student_test_"
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
        assertTrue(repository.loadAll().isEmpty());
        assertNull(repository.findByIdOrNull("student-001"));
        assertNull(repository.findByRegistrationOrNull("000123"));
    }

    @Test
    public void saveAndFindPreserveCompleteStudent() {
        OmrStudentProfile original = student(
                "student-001",
                "000123",
                "Ana Beatriz"
        );

        repository.save(original);

        assertCompleteStudent(
                original,
                repository.findByIdOrNull(" student-001 ")
        );
    }

    @Test
    public void savingSameIdUpdatesRegistrationAndName() {
        repository.save(student(
                "student-001",
                "000123",
                "Nome anterior"
        ));

        OmrStudentProfile updated = student(
                "student-001",
                "000999-A",
                "Ana Beatriz Costa"
        );

        repository.save(updated);

        assertEquals(1, repository.loadAll().size());
        assertCompleteStudent(
                updated,
                repository.findByIdOrNull("student-001")
        );
        assertNull(repository.findByRegistrationOrNull("000123"));
        assertCompleteStudent(
                updated,
                repository.findByRegistrationOrNull("000999-a")
        );
    }

    @Test
    public void duplicateRegistrationIsRejectedAndTransactionRollsBack() {
        OmrStudentProfile first = student(
                "student-001",
                "000123",
                "Ana Beatriz"
        );
        OmrStudentProfile second = student(
                "student-002",
                "000456",
                "Bruno Lima"
        );

        repository.save(first);

        expectIllegalArgument(() -> repository.save(student(
                "student-002",
                "000123",
                "Bruno Lima"
        )));

        assertCompleteStudent(
                first,
                repository.findByIdOrNull("student-001")
        );
        assertNull(repository.findByIdOrNull("student-002"));

        repository.save(second);

        expectIllegalArgument(() -> repository.save(student(
                "student-002",
                "000123",
                "Nome que não deve persistir"
        )));

        assertCompleteStudent(
                second,
                repository.findByIdOrNull("student-002")
        );
        assertEquals(2, repository.loadAll().size());
    }

    @Test
    public void registrationLookupIgnoresSpacesAndCase() {
        OmrStudentProfile original = student(
                "student-001",
                "0007-A/26",
                "Ana Beatriz"
        );

        repository.save(original);

        assertCompleteStudent(
                original,
                repository.findByRegistrationOrNull(
                        " 0007-a/26 "
                )
        );
    }

    @Test
    public void loadAllUsesNameRegistrationAndIdOrder() {
        repository.save(student(
                "student-z",
                "000002",
                "ana"
        ));
        repository.save(student(
                "student-a",
                "000001",
                "Ana"
        ));
        repository.save(student(
                "student-b",
                "000003",
                "Beatriz"
        ));

        List<OmrStudentProfile> stored =
                repository.loadAll();

        assertEquals(3, stored.size());
        assertEquals("student-a", stored.get(0).getStudentId());
        assertEquals("student-z", stored.get(1).getStudentId());
        assertEquals("student-b", stored.get(2).getStudentId());
    }

    @Test
    public void newInstanceReadsPersistedStudents() {
        OmrStudentProfile original = student(
                "student-persisted",
                "000777",
                "Aluno persistido"
        );

        repository.save(original);
        closeRepository();
        openRepository();

        assertCompleteStudent(
                original,
                repository.findByIdOrNull("student-persisted")
        );
    }

    @Test
    public void returnedListIsImmutable() {
        repository.save(student());

        expectUnsupportedOperation(() ->
                repository.loadAll().clear()
        );
    }

    @Test
    public void invalidIdentitiesReturnNull() {
        repository.save(student());

        assertNull(repository.findByIdOrNull(null));
        assertNull(repository.findByIdOrNull(" "));
        assertNull(repository.findByIdOrNull("missing"));
        assertNull(repository.findByRegistrationOrNull(null));
        assertNull(repository.findByRegistrationOrNull(" "));
        assertNull(repository.findByRegistrationOrNull("missing"));
    }

    @Test
    public void rejectsNullStudentAndInvalidConstructorData() {
        expectIllegalArgument(() -> repository.save(null));

        expectIllegalArgument(() ->
                new OmrSQLiteStudentRepository(
                        null,
                        testDatabaseName
                )
        );

        expectIllegalArgument(() ->
                new OmrSQLiteStudentRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void tamperedMetadataIsSkippedWithoutHidingHealthyRows() {
        repository.save(student(
                "student-tampered",
                "000111",
                "Aluno adulterado"
        ));
        repository.save(student(
                "student-healthy",
                "000222",
                "Aluno saudável"
        ));

        closeRepository();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_NAME,
                "Nome adulterado"
        );

        updateStudentRow(
                values,
                OmrSchoolDatabase.COLUMN_STUDENT_ID + " = ?",
                new String[]{"student-tampered"}
        );

        openRepository();

        List<OmrStudentProfile> stored =
                repository.loadAll();

        assertEquals(1, stored.size());
        assertEquals(
                "student-healthy",
                stored.get(0).getStudentId()
        );
        assertNull(repository.findByIdOrNull(
                "student-tampered"
        ));
    }

    @Test
    public void corruptedPayloadIsSkippedWithoutHidingHealthyRows() {
        repository.save(student(
                "student-corrupted",
                "000111",
                "Aluno corrompido"
        ));
        repository.save(student(
                "student-healthy",
                "000222",
                "Aluno saudável"
        ));

        closeRepository();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_STUDENT_PAYLOAD,
                new byte[]{1, 2, 3}
        );

        updateStudentRow(
                values,
                OmrSchoolDatabase.COLUMN_STUDENT_ID + " = ?",
                new String[]{"student-corrupted"}
        );

        openRepository();

        assertEquals(1, repository.loadAll().size());
        assertNull(repository.findByIdOrNull(
                "student-corrupted"
        ));
        assertNotNull(repository.findByIdOrNull(
                "student-healthy"
        ));
    }

    @Test
    public void migrationFromVersionOnePreservesAssessmentsAndAddsStudents() {
        closeRepository();
        applicationContext.deleteDatabase(testDatabaseName);

        OmrAssessmentDefinition assessment =
                assessment();

        try (LegacyVersionOneDatabase legacyDatabase =
                     new LegacyVersionOneDatabase(
                             applicationContext,
                             testDatabaseName
                     )) {

            long rowId = legacyDatabase
                    .getWritableDatabase()
                    .insertOrThrow(
                            OmrSchoolDatabase.TABLE_ASSESSMENT,
                            null,
                            assessmentValues(assessment)
                    );

            assertTrue(rowId != -1L);
        }

        openRepository();

        OmrStudentProfile student = student();
        repository.save(student);
        closeRepository();

        SQLiteDatabase rawDatabase =
                SQLiteDatabase.openDatabase(
                        applicationContext
                                .getDatabasePath(testDatabaseName)
                                .getPath(),
                        null,
                        SQLiteDatabase.OPEN_READONLY
                );

        try {
            assertEquals(
                    OmrSchoolDatabase.DATABASE_VERSION,
                    rawDatabase.getVersion()
            );
        } finally {
            rawDatabase.close();
        }

        try (OmrSQLiteAssessmentRepository assessmentRepository =
                     new OmrSQLiteAssessmentRepository(
                             applicationContext,
                             testDatabaseName
                     )) {

            OmrAssessmentDefinition restored =
                    assessmentRepository.findOrNull(
                            assessment.getAssessmentId(),
                            assessment.getVersion()
                    );

            assertNotNull(restored);
            assertEquals(assessment, restored);
            assertEquals(
                    assessment.getTitle(),
                    restored.getTitle()
            );
        }

        openRepository();
        assertCompleteStudent(
                student,
                repository.findByIdOrNull(
                        student.getStudentId()
                )
        );
    }

    private void openRepository() {
        repository = new OmrSQLiteStudentRepository(
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

    private void updateStudentRow(
            ContentValues values,
            String whereClause,
            String[] whereArguments
    ) {
        SQLiteDatabase database =
                SQLiteDatabase.openDatabase(
                        applicationContext
                                .getDatabasePath(testDatabaseName)
                                .getPath(),
                        null,
                        SQLiteDatabase.OPEN_READWRITE
                );

        try {
            int updated = database.update(
                    OmrSchoolDatabase.TABLE_STUDENT,
                    values,
                    whereClause,
                    whereArguments
            );

            assertEquals(1, updated);
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

    private OmrAssessmentDefinition assessment() {
        return new OmrAssessmentDefinition(
                "assessment-before-migration",
                3,
                "Prova preservada na migração",
                "Matemática",
                "9º ano",
                "3º bimestre",
                2026,
                "answer-key-before-migration",
                4
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

    private static final class LegacyVersionOneDatabase
            extends SQLiteOpenHelper {

        LegacyVersionOneDatabase(
                Context context,
                String databaseName
        ) {
            super(context, databaseName, null, 1);
        }

        @Override
        public void onCreate(
                SQLiteDatabase database
        ) {
            OmrSchoolDatabase.createAssessmentTable(database);
        }

        @Override
        public void onUpgrade(
                SQLiteDatabase database,
                int oldVersion,
                int newVersion
        ) {
            // Helper de teste que representa exatamente a versão 1.
        }
    }
}
