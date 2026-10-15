package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Collections;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentStructureBatchRepositoryInstrumentedTest {

    private Context context;
    private String databaseName;
    private OmrSQLiteAssessmentRepository assessments;
    private OmrSQLiteAssessmentStructureRepository structures;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        databaseName = "omr_structure_batch_"
                + UUID.randomUUID()
                + ".db";
        context.deleteDatabase(databaseName);
        openRepositories();
    }

    @After
    public void tearDown() {
        closeRepositories();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void batchPersistsAllStructuresAndSharedPolicyOnce() {
        saveAssessment("assessment-a", 1);
        saveAssessment("assessment-b", 1);

        structures.saveAllNew(Arrays.asList(
                structure("assessment-a", 1),
                structure("assessment-b", 1)
        ));

        assertNotNull(structures.findStoredOrNull("assessment-a", 1));
        assertNotNull(structures.findStoredOrNull("assessment-b", 1));
        assertEquals(2, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
        ));
        assertEquals(6, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_BLOCK
        ));
        assertEquals(1, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY
        ));
    }

    @Test
    public void batchSurvivesRepositoryReopening() {
        saveAssessment("assessment-a", 1);
        saveAssessment("assessment-b", 1);
        structures.saveAllNew(Arrays.asList(
                structure("assessment-a", 1),
                structure("assessment-b", 1)
        ));

        closeRepositories();
        openRepositories();

        assertEquals(
                structure("assessment-a", 1),
                structures.findStoredOrNull("assessment-a", 1)
        );
        assertEquals(
                structure("assessment-b", 1),
                structures.findStoredOrNull("assessment-b", 1)
        );
    }

    @Test
    public void missingAssessmentRollsBackEntireBatch() {
        saveAssessment("assessment-a", 1);

        expectIllegalArgument(() -> structures.saveAllNew(Arrays.asList(
                structure("assessment-a", 1),
                structure("assessment-missing", 1)
        )));

        assertNull(structures.findStoredOrNull("assessment-a", 1));
        assertEquals(0, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
        ));
        assertEquals(0, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY
        ));
    }

    @Test
    public void existingStructureRollsBackOtherNewStructure() {
        saveAssessment("assessment-a", 1);
        saveAssessment("assessment-b", 1);
        OmrAssessmentStructure first = structure("assessment-a", 1);
        assertTrue(structures.save(first));

        expectIllegalArgument(() -> structures.saveAllNew(Arrays.asList(
                structure("assessment-b", 1),
                first
        )));

        assertNull(structures.findStoredOrNull("assessment-b", 1));
        assertEquals(1, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
        ));
    }

    @Test
    public void conflictingPolicyRollsBackEntireBatch() {
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
                        BigDecimal.valueOf(100),
                        1,
                        RoundingMode.HALF_UP
                );

        expectIllegalState(() -> structures.saveAllNew(Arrays.asList(
                singleBlock("assessment-a", firstPolicy),
                singleBlock("assessment-b", conflictingPolicy)
        )));

        assertEquals(0, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
        ));
        assertEquals(0, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_SCORING_POLICY
        ));
    }

    @Test
    public void duplicateIdentityIsRejectedBeforeWriting() {
        saveAssessment("assessment-a", 1);
        OmrAssessmentStructure repeated = structure("assessment-a", 1);

        expectIllegalArgument(() -> structures.saveAllNew(Arrays.asList(
                repeated,
                repeated
        )));

        assertEquals(0, countRows(
                OmrSchoolDatabase.TABLE_ASSESSMENT_STRUCTURE
        ));
    }

    @Test
    public void nullEmptyAndNullElementBatchesAreRejected() {
        expectIllegalArgument(() -> structures.saveAllNew(null));
        expectIllegalArgument(() -> structures.saveAllNew(
                Collections.emptyList()
        ));
        expectIllegalArgument(() -> structures.saveAllNew(
                Collections.singletonList(null)
        ));
    }

    private void saveAssessment(String id, int version) {
        assertTrue(assessments.save(new OmrAssessmentDefinition(
                id,
                version,
                "Caderno " + id,
                "Caderno multidisciplinar",
                "Ensino medio",
                "3 periodo",
                2026,
                "answer-key-45",
                1
        )));
    }

    private static OmrAssessmentStructure structure(
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

    private static OmrAssessmentBlock block(
            String id,
            int order,
            String code,
            String name,
            int first
    ) {
        return new OmrAssessmentBlock(
                id,
                order,
                "Ciencias da Natureza",
                code,
                name,
                first,
                15,
                OmrProportionalScoringPolicy.correctQuestionsToTenV1()
        );
    }

    private static OmrAssessmentStructure singleBlock(
            String assessmentId,
            OmrProportionalScoringPolicy policy
    ) {
        return new OmrAssessmentStructure(
                assessmentId,
                1,
                45,
                Collections.singletonList(new OmrAssessmentBlock(
                        "geral",
                        1,
                        null,
                        null,
                        "Geral",
                        1,
                        45,
                        policy
                ))
        );
    }

    private void openRepositories() {
        assessments = new OmrSQLiteAssessmentRepository(
                context,
                databaseName
        );
        structures = new OmrSQLiteAssessmentStructureRepository(
                context,
                databaseName
        );
    }

    private void closeRepositories() {
        if (structures != null) {
            structures.close();
            structures = null;
        }
        if (assessments != null) {
            assessments.close();
            assessments = null;
        }
    }

    private int countRows(String table) {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                context.getDatabasePath(databaseName).getPath(),
                null,
                SQLiteDatabase.OPEN_READONLY
        );
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + table,
                null
        )) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            database.close();
        }
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            assertTrue(true);
        }
    }
}
