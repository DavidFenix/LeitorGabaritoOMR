package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationDeletionRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public final class
OmrSQLiteAssessmentApplicationDeletionRepositoryInstrumentedTest {

    private Context context;
    private String databaseName;
    private OmrSQLiteAssessmentRepository assessments;
    private OmrSQLiteClassGroupRepository classes;
    private OmrSQLiteAssessmentApplicationRepository applications;
    private OmrSQLiteAssessmentParticipantRepository participants;
    private OmrSQLiteAssessmentAttemptRepository attempts;
    private OmrSQLiteAssessmentApplicationDeletionRepository deletions;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        databaseName = "omr_application_deletion_"
                + UUID.randomUUID() + ".db";
        context.deleteDatabase(databaseName);
        assessments = new OmrSQLiteAssessmentRepository(
                context, databaseName
        );
        classes = new OmrSQLiteClassGroupRepository(
                context, databaseName
        );
        applications = new OmrSQLiteAssessmentApplicationRepository(
                context, databaseName
        );
        participants = new OmrSQLiteAssessmentParticipantRepository(
                context, databaseName
        );
        attempts = new OmrSQLiteAssessmentAttemptRepository(
                context, databaseName
        );
        deletions =
                new OmrSQLiteAssessmentApplicationDeletionRepository(
                        context, databaseName
                );
    }

    @After
    public void tearDown() {
        deletions.close();
        attempts.close();
        participants.close();
        applications.close();
        classes.close();
        assessments.close();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void deletesUncorrectedApplicationAndRosterAtomically() {
        prepareApplicationWithRoster();

        OmrAssessmentApplicationDeletionRepository.DeletionResult result =
                deletions.deleteIfUncorrected(" application-delete ");

        assertEquals(
                OmrAssessmentApplicationDeletionRepository.DeletionResult
                        .Status.DELETED,
                result.getStatus()
        );
        assertEquals(2, result.getDeletedParticipantCount());
        assertNull(applications.findByIdOrNull("application-delete"));
        assertTrue(participants.loadForApplication(
                "application-delete"
        ).isEmpty());
        assertNotNull(assessments.findOrNull("assessment-1", 1));
        assertNotNull(classes.findByIdOrNull("class-1"));
    }

    @Test
    public void attemptBlocksDeletionAndPreservesEveryRecord() {
        prepareApplicationWithRoster();
        assertTrue(attempts.save(new OmrAssessmentAttempt(
                "attempt-1",
                "application-delete",
                "student-1",
                1,
                "history-1",
                2_000L
        )));

        OmrAssessmentApplicationDeletionRepository.DeletionResult result =
                deletions.deleteIfUncorrected("application-delete");

        assertEquals(
                OmrAssessmentApplicationDeletionRepository.DeletionResult
                        .Status.BLOCKED_BY_ATTEMPTS,
                result.getStatus()
        );
        assertEquals(1, result.getBlockingAttemptCount());
        assertNotNull(applications.findByIdOrNull("application-delete"));
        assertEquals(2, participants.loadForApplication(
                "application-delete"
        ).size());
        assertEquals(1, attempts.loadForApplication(
                "application-delete"
        ).size());
    }

    @Test
    public void missingAndBlankIdentityReturnNotFound() {
        prepareApplicationWithRoster();

        assertEquals(
                OmrAssessmentApplicationDeletionRepository.DeletionResult
                        .Status.NOT_FOUND,
                deletions.deleteIfUncorrected("missing").getStatus()
        );
        assertEquals(
                OmrAssessmentApplicationDeletionRepository.DeletionResult
                        .Status.NOT_FOUND,
                deletions.deleteIfUncorrected(" ").getStatus()
        );
        assertEquals(
                OmrAssessmentApplicationDeletionRepository.DeletionResult
                        .Status.NOT_FOUND,
                deletions.deleteIfUncorrected(null).getStatus()
        );
        assertNotNull(applications.findByIdOrNull("application-delete"));
    }

    private void prepareApplicationWithRoster() {
        assertTrue(assessments.save(new OmrAssessmentDefinition(
                "assessment-1",
                1,
                "Prova",
                "Natureza",
                "1ª série",
                "1º período",
                2026,
                "answer-key-1",
                1
        )));
        classes.save(new OmrClassGroup(
                "class-1", "1ª A", "1ª série", 2026
        ));
        assertTrue(applications.save(new OmrAssessmentApplication(
                "application-delete",
                "assessment-1",
                1,
                "class-1",
                1_000L
        )));
        assertTrue(participants.saveRoster(
                "application-delete",
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-delete",
                                "student-1",
                                "MAT-1",
                                "Ana",
                                "1ª A",
                                1
                        ),
                        new OmrAssessmentParticipant(
                                "application-delete",
                                "student-2",
                                "MAT-2",
                                "Bruno",
                                "1ª A",
                                2
                        )
                )
        ));
    }
}
