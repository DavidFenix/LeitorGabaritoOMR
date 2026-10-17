package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentDeletionRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentDeletionRepositoryInstrumentedTest {

    private Context applicationContext;
    private String databaseName;
    private OmrSQLiteAssessmentRepository assessments;
    private OmrSQLiteAssessmentStructureRepository structures;
    private OmrSQLiteClassGroupRepository classes;
    private OmrSQLiteAssessmentApplicationRepository applications;
    private OmrSQLiteAssessmentDeletionRepository deletions;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        databaseName = "omr_assessment_deletion_test_"
                + UUID.randomUUID() + ".db";
        applicationContext.deleteDatabase(databaseName);

        assessments = new OmrSQLiteAssessmentRepository(
                applicationContext,
                databaseName
        );
        structures = new OmrSQLiteAssessmentStructureRepository(
                applicationContext,
                databaseName
        );
        classes = new OmrSQLiteClassGroupRepository(
                applicationContext,
                databaseName
        );
        applications = new OmrSQLiteAssessmentApplicationRepository(
                applicationContext,
                databaseName
        );
        deletions = new OmrSQLiteAssessmentDeletionRepository(
                applicationContext,
                databaseName
        );
    }

    @After
    public void tearDown() {
        deletions.close();
        applications.close();
        classes.close();
        structures.close();
        assessments.close();
        applicationContext.deleteDatabase(databaseName);
    }

    @Test
    public void deletesEveryVersionAndPublishedStructureAtomically() {
        assertTrue(assessments.save(assessment(1)));
        assertTrue(assessments.save(assessment(2)));
        assertTrue(structures.save(OmrAssessmentStructure.legacySingleBlock(
                "assessment-delete",
                1,
                10
        )));
        assertTrue(structures.save(OmrAssessmentStructure.legacySingleBlock(
                "assessment-delete",
                2,
                10
        )));

        OmrAssessmentDeletionRepository.DeletionResult result =
                deletions.deleteAllVersionsIfUnused(
                        " assessment-delete "
                );

        assertEquals(
                OmrAssessmentDeletionRepository.DeletionResult.Status.DELETED,
                result.getStatus()
        );
        assertEquals(2, result.getDeletedVersionCount());
        assertEquals(0, result.getBlockingApplicationCount());
        assertNull(assessments.findOrNull("assessment-delete", 1));
        assertNull(assessments.findOrNull("assessment-delete", 2));
        assertNull(structures.findStoredOrNull("assessment-delete", 1));
        assertNull(structures.findStoredOrNull("assessment-delete", 2));
    }

    @Test
    public void applicationOnAnyVersionBlocksWholeAssessmentDeletion() {
        assertTrue(assessments.save(assessment(1)));
        assertTrue(assessments.save(assessment(2)));
        assertTrue(structures.save(OmrAssessmentStructure.legacySingleBlock(
                "assessment-delete",
                1,
                10
        )));
        classes.save(new OmrClassGroup(
                "class-1",
                "1ª A",
                "1ª série",
                2026
        ));
        assertTrue(applications.save(new OmrAssessmentApplication(
                "application-1",
                "assessment-delete",
                2,
                "class-1",
                1_000L
        )));

        OmrAssessmentDeletionRepository.DeletionResult result =
                deletions.deleteAllVersionsIfUnused(
                        "assessment-delete"
                );

        assertEquals(
                OmrAssessmentDeletionRepository.DeletionResult.Status
                        .BLOCKED_BY_APPLICATIONS,
                result.getStatus()
        );
        assertEquals(1, result.getBlockingApplicationCount());
        assertNotNull(assessments.findOrNull("assessment-delete", 1));
        assertNotNull(assessments.findOrNull("assessment-delete", 2));
        assertNotNull(structures.findStoredOrNull(
                "assessment-delete",
                1
        ));
    }

    @Test
    public void missingOrBlankAssessmentReturnsNotFoundWithoutMutation() {
        assertTrue(assessments.save(assessment(1)));

        assertEquals(
                OmrAssessmentDeletionRepository.DeletionResult.Status
                        .NOT_FOUND,
                deletions.deleteAllVersionsIfUnused("missing").getStatus()
        );
        assertEquals(
                OmrAssessmentDeletionRepository.DeletionResult.Status
                        .NOT_FOUND,
                deletions.deleteAllVersionsIfUnused("  ").getStatus()
        );
        assertEquals(
                OmrAssessmentDeletionRepository.DeletionResult.Status
                        .NOT_FOUND,
                deletions.deleteAllVersionsIfUnused(null).getStatus()
        );
        assertNotNull(assessments.findOrNull("assessment-delete", 1));
    }

    private static OmrAssessmentDefinition assessment(int version) {
        return new OmrAssessmentDefinition(
                "assessment-delete",
                version,
                "Prova de exclusão",
                "Matemática",
                "1ª série",
                "1º período",
                2026,
                "answer-key-1",
                1
        );
    }
}
