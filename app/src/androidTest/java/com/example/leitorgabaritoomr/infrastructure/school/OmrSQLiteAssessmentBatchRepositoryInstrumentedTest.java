package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentBatchRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteAssessmentBatchRepository batchRepository;
    private OmrSQLiteAssessmentRepository assessmentRepository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();
        testDatabaseName = "omr_school_assessment_batch_test_"
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
    public void constructorRejectsMissingContextOrDatabaseName() {
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentBatchRepository(null));
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentBatchRepository(
                        applicationContext,
                        null
                ));
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentBatchRepository(
                        applicationContext,
                        "  "
                ));
    }

    @Test
    public void rejectsMissingOrEmptyBatch() {
        expectIllegalArgument(() ->
                batchRepository.saveAllNew(null));
        expectIllegalArgument(() ->
                batchRepository.saveAllNew(Collections.emptyList()));

        assertTrue(assessmentRepository.loadAll().isEmpty());
    }

    @Test
    public void rejectsNullAssessmentWithoutWritingAnything() {
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(
                        assessment("assessment-a", 1, "Primeira"),
                        null
                )
        ));

        assertTrue(assessmentRepository.loadAll().isEmpty());
    }

    @Test
    public void rejectsDuplicateIdentityInsideBatch() {
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(
                        assessment("assessment-a", 1, "Original"),
                        assessment("assessment-a", 1, "Conflitante")
                )
        ));

        assertTrue(assessmentRepository.loadAll().isEmpty());
    }

    @Test
    public void savesMultipleAssessmentsAsOneCompleteBatch() {
        OmrAssessmentDefinition first = assessment(
                "assessment-a",
                1,
                "Prova de Matemática"
        );
        OmrAssessmentDefinition second = assessment(
                "assessment-b",
                1,
                "Prova de Ciências"
        );

        batchRepository.saveAllNew(Arrays.asList(first, second));

        assertEquals(2, assessmentRepository.loadAll().size());
        assertComplete(
                first,
                assessmentRepository.findOrNull("assessment-a", 1)
        );
        assertComplete(
                second,
                assessmentRepository.findOrNull("assessment-b", 1)
        );
    }

    @Test
    public void savesSeveralVersionsWithoutReplacingHistory() {
        OmrAssessmentDefinition versionOne = assessment(
                "assessment-a",
                1,
                "Versão um"
        );
        OmrAssessmentDefinition versionTwo = assessment(
                "assessment-a",
                2,
                "Versão dois"
        );
        OmrAssessmentDefinition versionThree = assessment(
                "assessment-a",
                3,
                "Versão três"
        );

        batchRepository.saveAllNew(Arrays.asList(
                versionOne,
                versionTwo,
                versionThree
        ));

        assertEquals(3, assessmentRepository.loadAll().size());
        assertComplete(
                versionOne,
                assessmentRepository.findOrNull("assessment-a", 1)
        );
        assertComplete(
                versionThree,
                assessmentRepository.findLatestVersionOrNull(
                        "assessment-a"
                )
        );
    }

    @Test
    public void committedBatchSurvivesRepositoryReopen() {
        OmrAssessmentDefinition original = assessment(
                "assessment-a",
                1,
                "Prova persistida"
        );

        batchRepository.saveAllNew(
                Collections.singletonList(original)
        );

        closeRepositories();
        openRepositories();

        assertComplete(
                original,
                assessmentRepository.findOrNull("assessment-a", 1)
        );
    }

    @Test
    public void existingIdentityRollsBackEveryNewRow() {
        OmrAssessmentDefinition existing = assessment(
                "assessment-a",
                1,
                "Já existente"
        );
        assertTrue(assessmentRepository.save(existing));

        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(
                        assessment("assessment-b", 1, "Nova antes"),
                        assessment("assessment-a", 1, "Conflitante"),
                        assessment("assessment-c", 1, "Nova depois")
                )
        ));

        assertEquals(1, assessmentRepository.loadAll().size());
        assertComplete(
                existing,
                assessmentRepository.findOrNull("assessment-a", 1)
        );
        assertNull(assessmentRepository.findOrNull(
                "assessment-b",
                1
        ));
        assertNull(assessmentRepository.findOrNull(
                "assessment-c",
                1
        ));
    }

    @Test
    public void failedBatchKeepsAllPreviouslyStoredVersions() {
        OmrAssessmentDefinition first = assessment(
                "assessment-a",
                1,
                "Versão um"
        );
        OmrAssessmentDefinition second = assessment(
                "assessment-a",
                2,
                "Versão dois"
        );
        batchRepository.saveAllNew(Arrays.asList(first, second));

        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(
                        assessment("assessment-a", 3, "Versão três"),
                        assessment("assessment-a", 2, "Conflito")
                )
        ));

        assertEquals(2, assessmentRepository.loadAll().size());
        assertComplete(
                first,
                assessmentRepository.findOrNull("assessment-a", 1)
        );
        assertComplete(
                second,
                assessmentRepository.findOrNull("assessment-a", 2)
        );
        assertNull(assessmentRepository.findOrNull(
                "assessment-a",
                3
        ));
    }

    @Test
    public void repositoryRemainsUsableAfterRolledBackBatch() {
        OmrAssessmentDefinition existing = assessment(
                "assessment-a",
                1,
                "Existente"
        );
        assertTrue(assessmentRepository.save(existing));

        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(existing)
        ));

        OmrAssessmentDefinition next = assessment(
                "assessment-a",
                2,
                "Próxima versão"
        );
        batchRepository.saveAllNew(Collections.singletonList(next));

        assertEquals(2, assessmentRepository.loadAll().size());
        assertComplete(
                next,
                assessmentRepository.findLatestVersionOrNull(
                        "assessment-a"
                )
        );
    }

    private void openRepositories() {
        batchRepository =
                new OmrSQLiteAssessmentBatchRepository(
                        applicationContext,
                        testDatabaseName
                );
        assessmentRepository =
                new OmrSQLiteAssessmentRepository(
                        applicationContext,
                        testDatabaseName
                );
    }

    private void closeRepositories() {
        if (batchRepository != null) {
            batchRepository.close();
            batchRepository = null;
        }
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title
    ) {
        return new OmrAssessmentDefinition(
                id,
                version,
                title,
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                version
        );
    }

    private static void assertComplete(
            OmrAssessmentDefinition expected,
            OmrAssessmentDefinition actual
    ) {
        assertNotNull(actual);
        assertEquals(
                expected.getAssessmentId(),
                actual.getAssessmentId()
        );
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getSubject(), actual.getSubject());
        assertEquals(
                expected.getGradeLevel(),
                actual.getGradeLevel()
        );
        assertEquals(
                expected.getAcademicPeriod(),
                actual.getAcademicPeriod()
        );
        assertEquals(
                expected.getSchoolYear(),
                actual.getSchoolYear()
        );
        assertEquals(
                expected.getAnswerKeyId(),
                actual.getAnswerKeyId()
        );
        assertEquals(
                expected.getAnswerKeyVersion(),
                actual.getAnswerKeyVersion()
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
