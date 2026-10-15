package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentStructureRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteAssessmentStructureRepository structureRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        testDatabaseName =
                "omr_school_structure_test_"
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
    public void emptyRepositoryDoesNotInventUnknownAssessment() {
        assertNull(structureRepository.findStoredOrNull(
                "assessment-001",
                1
        ));
        assertTrue(structureRepository
                .loadStoredVersions("assessment-001")
                .isEmpty());
        assertNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                45
        ));
    }

    @Test
    public void existingAssessmentWithoutStructureUsesLegacyBlock() {
        saveAssessment("assessment-001", 1);

        OmrAssessmentStructure resolved =
                structureRepository.resolveOrLegacy(
                        " assessment-001 ",
                        1,
                        45
                );

        assertNotNull(resolved);
        assertTrue(resolved.isLegacySingleBlock());
        assertEquals(45, resolved.getQuestionCount());
        assertEquals(1, resolved.getBlockCount());
        assertEquals(
                OmrProportionalScoringPolicy
                        .WEIGHTED_POINTS_POLICY_ID,
                resolved.getBlocks().get(0)
                        .getScoringPolicy().getPolicyId()
        );
        assertNull(structureRepository.findStoredOrNull(
                "assessment-001",
                1
        ));
    }

    @Test
    public void saveAndFindPreserveCompleteMultidisciplinaryStructure() {
        saveAssessment("assessment-001", 1);
        OmrAssessmentStructure original = structure(
                "assessment-001",
                1
        );

        assertTrue(structureRepository.save(original));

        OmrAssessmentStructure restored =
                structureRepository.findStoredOrNull(
                        " assessment-001 ",
                        1
                );

        assertCompleteStructure(original, restored);
        assertEquals(
                original,
                structureRepository.resolveOrLegacy(
                        "assessment-001",
                        1,
                        45
                )
        );
    }

    @Test
    public void duplicateAssessmentIdentityPreservesFirstStructure() {
        saveAssessment("assessment-001", 1);

        OmrAssessmentStructure original = structure(
                "assessment-001",
                1
        );
        OmrAssessmentStructure conflicting =
                OmrAssessmentStructure.legacySingleBlock(
                        "assessment-001",
                        1,
                        45
                );

        assertTrue(structureRepository.save(original));
        assertFalse(structureRepository.save(conflicting));
        assertEquals(
                original,
                structureRepository.findStoredOrNull(
                        "assessment-001",
                        1
                )
        );
    }

    @Test
    public void multipleAssessmentVersionsCoexistInDescendingOrder() {
        saveAssessment("assessment-001", 1);
        saveAssessment("assessment-001", 2);
        saveAssessment("assessment-other", 1);

        OmrAssessmentStructure versionOne = structure(
                "assessment-001",
                1
        );
        OmrAssessmentStructure versionTwo =
                OmrAssessmentStructure.legacySingleBlock(
                        "assessment-001",
                        2,
                        30
                );

        assertTrue(structureRepository.save(versionOne));
        assertTrue(structureRepository.save(versionTwo));
        assertTrue(structureRepository.save(
                OmrAssessmentStructure.legacySingleBlock(
                        "assessment-other",
                        1,
                        10
                )
        ));

        List<OmrAssessmentStructure> versions =
                structureRepository.loadStoredVersions(
                        "assessment-001"
                );

        assertEquals(2, versions.size());
        assertEquals(2, versions.get(0).getAssessmentVersion());
        assertEquals(1, versions.get(1).getAssessmentVersion());
        assertEquals(versionTwo, versions.get(0));
        assertEquals(versionOne, versions.get(1));
    }

    @Test
    public void newRepositoryInstanceReadsPersistedStructure() {
        saveAssessment("assessment-persisted", 3);
        OmrAssessmentStructure original = structure(
                "assessment-persisted",
                3
        );

        assertTrue(structureRepository.save(original));
        closeRepositories();
        openRepositories();

        assertCompleteStructure(
                original,
                structureRepository.findStoredOrNull(
                        "assessment-persisted",
                        3
                )
        );
    }

    @Test
    public void returnedVersionListIsImmutable() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));

        try {
            structureRepository
                    .loadStoredVersions("assessment-001")
                    .clear();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    @Test
    public void invalidArgumentsFailWithoutWritingData() {
        expectIllegalArgument(() -> structureRepository.save(null));
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentStructureRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentStructureRepository(
                        applicationContext,
                        " "
                )
        );

        assertNull(structureRepository.findStoredOrNull(null, 1));
        assertNull(structureRepository.findStoredOrNull(" ", 1));
        assertNull(structureRepository.findStoredOrNull("id", 0));
        assertTrue(structureRepository
                .loadStoredVersions(null)
                .isEmpty());
        assertNull(structureRepository.resolveOrLegacy("id", 0, 1));
        assertNull(structureRepository.resolveOrLegacy("id", 1, 0));
    }

    @Test
    public void structureCannotBeSavedBeforeItsAssessment() {
        expectIllegalArgument(() -> structureRepository.save(
                structure("missing-assessment", 1)
        ));

        assertNull(structureRepository.findStoredOrNull(
                "missing-assessment",
                1
        ));
    }

    @Test
    public void corruptedStructurePayloadFailsClosedWithoutLegacyFallback() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));
        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_STRUCTURE_PAYLOAD,
                new byte[]{1, 2, 3}
        );
        updateOneRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                values,
                exactAssessmentWhere(),
                new String[]{"assessment-001", "1"}
        );

        openRepositories();

        assertNull(structureRepository.findStoredOrNull(
                "assessment-001",
                1
        ));
        assertNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                45
        ));
    }

    @Test
    public void tamperedStructureMetadataFailsClosed() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));
        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_STRUCTURE_QUESTION_COUNT,
                44
        );
        updateOneRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE,
                values,
                exactAssessmentWhere(),
                new String[]{"assessment-001", "1"}
        );

        openRepositories();
        assertNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                45
        ));
    }

    @Test
    public void corruptedBlockPayloadFailsClosed() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));
        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_BLOCK_PAYLOAD,
                new byte[]{9, 8, 7}
        );
        updateOneRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK,
                values,
                exactAssessmentWhere()
                        + " AND "
                        + OmrSchoolDatabase.COLUMN_BLOCK_ID
                        + " = ?",
                new String[]{"assessment-001", "1", "quimica"}
        );

        openRepositories();
        assertNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                45
        ));
    }

    @Test
    public void tamperedBlockMetadataFailsClosed() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));
        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_COMPONENT_NAME,
                "Componente adulterado"
        );
        updateOneRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK,
                values,
                exactAssessmentWhere()
                        + " AND "
                        + OmrSchoolDatabase.COLUMN_BLOCK_ID
                        + " = ?",
                new String[]{"assessment-001", "1", "fisica"}
        );

        openRepositories();
        assertNull(structureRepository.findStoredOrNull(
                "assessment-001",
                1
        ));
    }

    @Test
    public void corruptedPolicyPayloadFailsClosed() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));
        closeRepositories();

        ContentValues values = new ContentValues();
        values.put(
                OmrSchoolDatabase.COLUMN_POLICY_PAYLOAD,
                new byte[]{4, 5, 6}
        );
        updateOneRow(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY,
                values,
                OmrSchoolDatabase.COLUMN_POLICY_ID
                        + " = ? AND "
                        + OmrSchoolDatabase.COLUMN_POLICY_VERSION
                        + " = ?",
                new String[]{
                        OmrProportionalScoringPolicy
                                .CORRECT_QUESTIONS_POLICY_ID,
                        "1"
                }
        );

        openRepositories();
        assertNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                45
        ));
    }

    @Test
    public void conflictingPolicyIdentityRollsBackWholeStructure() {
        saveAssessment("assessment-a", 1);
        saveAssessment("assessment-b", 1);

        OmrProportionalScoringPolicy firstPolicy =
                new OmrProportionalScoringPolicy(
                        "school-policy",
                        1,
                        OmrProportionalScoringPolicy.Basis
                                .CORRECT_QUESTION_COUNT,
                        BigDecimal.TEN,
                        1,
                        RoundingMode.HALF_UP
                );
        OmrProportionalScoringPolicy conflictingPolicy =
                new OmrProportionalScoringPolicy(
                        "school-policy",
                        1,
                        OmrProportionalScoringPolicy.Basis
                                .CORRECT_QUESTION_COUNT,
                        new BigDecimal("20"),
                        1,
                        RoundingMode.HALF_UP
                );

        assertTrue(structureRepository.save(singleBlockStructure(
                "assessment-a",
                firstPolicy
        )));

        expectIllegalState(() -> structureRepository.save(
                singleBlockStructure(
                        "assessment-b",
                        conflictingPolicy
                )
        ));

        assertNull(structureRepository.findStoredOrNull(
                "assessment-b",
                1
        ));
        assertTrue(structureRepository.resolveOrLegacy(
                "assessment-b",
                1,
                15
        ).isLegacySingleBlock());
        assertEquals(
                1,
                countRows(
                        OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
                )
        );
    }

    @Test
    public void identicalVersionedPolicyCanBeSharedByManyStructures() {
        saveAssessment("assessment-a", 1);
        saveAssessment("assessment-b", 1);

        OmrProportionalScoringPolicy policy =
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1();

        assertTrue(structureRepository.save(singleBlockStructure(
                "assessment-a",
                policy
        )));
        assertTrue(structureRepository.save(singleBlockStructure(
                "assessment-b",
                policy
        )));

        assertEquals(
                1,
                countRows(
                        OmrSchoolDatabase
                                .TABLE_ASSESSMENT_SCORING_POLICY
                )
        );
        assertNotNull(structureRepository.findStoredOrNull(
                "assessment-a",
                1
        ));
        assertNotNull(structureRepository.findStoredOrNull(
                "assessment-b",
                1
        ));
    }

    @Test
    public void requestedQuestionCountMustMatchStoredStructure() {
        saveAssessment("assessment-001", 1);
        structureRepository.save(structure("assessment-001", 1));

        assertNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                44
        ));
        assertNotNull(structureRepository.resolveOrLegacy(
                "assessment-001",
                1,
                45
        ));
    }

    @Test
    public void migrationFromVersionFivePreservesAssessmentAndAddsFeature() {
        closeRepositories();
        applicationContext.deleteDatabase(testDatabaseName);
        createVersionFiveDatabaseWithAssessment();

        openRepositories();

        OmrAssessmentDefinition preserved =
                assessmentRepository.findOrNull(
                        "assessment-migrated",
                        1
                );

        assertNotNull(preserved);
        assertEquals("Prova migrada", preserved.getTitle());
        assertTrue(tableExists(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY
        ));
        assertTrue(tableExists(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
        ));
        assertTrue(tableExists(
                OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK
        ));

        OmrAssessmentStructure legacy =
                structureRepository.resolveOrLegacy(
                        "assessment-migrated",
                        1,
                        45
                );
        assertNotNull(legacy);
        assertTrue(legacy.isLegacySingleBlock());

        OmrAssessmentStructure explicit = structure(
                "assessment-migrated",
                1
        );
        assertTrue(structureRepository.save(explicit));
        assertEquals(
                explicit,
                structureRepository.findStoredOrNull(
                        "assessment-migrated",
                        1
                )
        );
        assertEquals(
                OmrSchoolDatabase.DATABASE_VERSION,
                databaseVersion()
        );
    }

    private void openRepositories() {
        assessmentRepository = new OmrSQLiteAssessmentRepository(
                applicationContext,
                testDatabaseName
        );
        structureRepository =
                new OmrSQLiteAssessmentStructureRepository(
                        applicationContext,
                        testDatabaseName
                );
    }

    private void closeRepositories() {
        if (structureRepository != null) {
            structureRepository.close();
            structureRepository = null;
        }
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
    }

    private void saveAssessment(String assessmentId, int version) {
        assertTrue(assessmentRepository.save(assessment(
                assessmentId,
                version,
                "Prova " + assessmentId
        )));
    }

    private OmrAssessmentDefinition assessment(
            String assessmentId,
            int version,
            String title
    ) {
        return new OmrAssessmentDefinition(
                assessmentId,
                version,
                title,
                "Ciencias da Natureza",
                "1a serie",
                "3o periodo",
                2026,
                "answer-key-001",
                version
        );
    }

    private OmrAssessmentStructure structure(
            String assessmentId,
            int version
    ) {
        return new OmrAssessmentStructure(
                assessmentId,
                version,
                45,
                Arrays.asList(
                        block("quimica", 1, "QUI", "Quimica", 1),
                        block("fisica", 2, "FIS", "Fisica", 16),
                        block("biologia", 3, "BIO", "Biologia", 31)
                )
        );
    }

    private OmrAssessmentBlock block(
            String blockId,
            int order,
            String componentCode,
            String componentName,
            int firstPosition
    ) {
        return new OmrAssessmentBlock(
                blockId,
                order,
                "Ciencias da Natureza",
                componentCode,
                componentName,
                firstPosition,
                15,
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1()
        );
    }

    private OmrAssessmentStructure singleBlockStructure(
            String assessmentId,
            OmrProportionalScoringPolicy policy
    ) {
        return new OmrAssessmentStructure(
                assessmentId,
                1,
                15,
                Arrays.asList(new OmrAssessmentBlock(
                        "matematica",
                        1,
                        "Matematica",
                        "MAT",
                        "Matematica",
                        1,
                        15,
                        policy
                ))
        );
    }

    private void assertCompleteStructure(
            OmrAssessmentStructure expected,
            OmrAssessmentStructure actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getAssessmentId(),
                actual.getAssessmentId()
        );
        assertEquals(
                expected.getAssessmentVersion(),
                actual.getAssessmentVersion()
        );
        assertEquals(
                expected.getQuestionCount(),
                actual.getQuestionCount()
        );
        assertEquals(expected.getBlocks(), actual.getBlocks());
    }

    private void createVersionFiveDatabaseWithAssessment() {
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                applicationContext
                        .getDatabasePath(testDatabaseName)
                        .getPath(),
                null
        );

        try {
            OmrSchoolDatabase.createAssessmentTable(legacy);
            OmrSchoolDatabase.createStudentTable(legacy);
            OmrSchoolDatabase.createClassGroupTable(legacy);
            OmrSchoolDatabase.createClassEnrollmentTable(legacy);
            OmrSchoolDatabase.createAssessmentApplicationTable(legacy);
            OmrSchoolDatabase.createAssessmentParticipantTable(legacy);
            OmrSchoolDatabase.createAssessmentAttemptTable(legacy);

            OmrAssessmentDefinition assessment = assessment(
                    "assessment-migrated",
                    1,
                    "Prova migrada"
            );

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

            assertTrue(legacy.insert(
                    OmrSchoolDatabase.TABLE_ASSESSMENT,
                    null,
                    values
            ) != -1L);

            legacy.setVersion(5);
        } finally {
            legacy.close();
        }
    }

    private void updateOneRow(
            String table,
            ContentValues values,
            String whereClause,
            String[] whereArguments
    ) {
        SQLiteDatabase direct = openDirectDatabase();
        try {
            assertEquals(
                    1,
                    direct.update(
                            table,
                            values,
                            whereClause,
                            whereArguments
                    )
            );
        } finally {
            direct.close();
        }
    }

    private int countRows(String table) {
        SQLiteDatabase direct = openDirectDatabase();
        try (Cursor cursor = direct.rawQuery(
                "SELECT COUNT(*) FROM " + table,
                null
        )) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            direct.close();
        }
    }

    private boolean tableExists(String table) {
        SQLiteDatabase direct = openDirectDatabase();
        try (Cursor cursor = direct.query(
                "sqlite_master",
                new String[]{"name"},
                "type = 'table' AND name = ?",
                new String[]{table},
                null,
                null,
                null
        )) {
            return cursor.moveToFirst();
        } finally {
            direct.close();
        }
    }

    private int databaseVersion() {
        SQLiteDatabase direct = openDirectDatabase();
        try {
            return direct.getVersion();
        } finally {
            direct.close();
        }
    }

    private SQLiteDatabase openDirectDatabase() {
        return SQLiteDatabase.openDatabase(
                applicationContext
                        .getDatabasePath(testDatabaseName)
                        .getPath(),
                null,
                SQLiteDatabase.OPEN_READWRITE
        );
    }

    private static String exactAssessmentWhere() {
        return OmrSchoolDatabase.COLUMN_ASSESSMENT_ID
                + " = ? AND "
                + OmrSchoolDatabase.COLUMN_ASSESSMENT_VERSION
                + " = ?";
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
    }
}
