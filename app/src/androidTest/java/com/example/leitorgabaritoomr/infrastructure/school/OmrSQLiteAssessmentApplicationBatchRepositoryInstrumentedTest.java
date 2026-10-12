package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
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
OmrSQLiteAssessmentApplicationBatchRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteAssessmentApplicationBatchRepository batchRepository;
    private OmrSQLiteAssessmentApplicationRepository applicationRepository;
    private OmrSQLiteAssessmentParticipantRepository participantRepository;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteStudentRepository studentRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        testDatabaseName = "omr_school_application_batch_test_"
                + UUID.randomUUID() + ".db";
        applicationContext.deleteDatabase(testDatabaseName);
        openRepositories();
    }

    @After
    public void tearDown() {
        closeRepositories();
        applicationContext.deleteDatabase(testDatabaseName);
    }

    @Test
    public void constructorsRejectInvalidData() {
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentApplicationBatchRepository(
                        null,
                        testDatabaseName
                )
        );
        expectIllegalArgument(() ->
                new OmrSQLiteAssessmentApplicationBatchRepository(
                        applicationContext,
                        " "
                )
        );
    }

    @Test
    public void nullAndEmptyCollectionsAreRejected() {
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                null,
                Collections.singletonList(participant())
        ));
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(application()),
                null
        ));
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.emptyList(),
                Collections.singletonList(participant())
        ));
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.emptyList()
        ));
    }

    @Test
    public void nullDuplicateAndParticipantlessApplicationsAreRejected() {
        List<OmrAssessmentApplication> withNull = new ArrayList<>();
        withNull.add(null);

        expectIllegalArgument(() -> batchRepository.saveAllNew(
                withNull,
                Collections.singletonList(participant())
        ));
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(application(), application()),
                Collections.singletonList(participant())
        ));
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(
                        application("application-001", "class-001", 1_000L),
                        application("application-002", "class-001", 2_000L)
                ),
                Collections.singletonList(participant())
        ));
    }

    @Test
    public void orphanAndNullParticipantsAreRejected() {
        List<OmrAssessmentParticipant> withNull = new ArrayList<>();
        withNull.add(null);

        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(application()),
                withNull
        ));
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.singletonList(participant(
                        "application-other",
                        "student-001",
                        "MAT-001",
                        "Ana",
                        1
                ))
        ));
    }

    @Test
    public void duplicateRosterIdentitiesAreRejected() {
        expectIllegalArgument(() -> saveWithParticipants(
                participant("application-001", "student-001", "MAT-001", "Ana", 1),
                participant("application-001", "student-001", "MAT-002", "Bia", 2)
        ));
        expectIllegalArgument(() -> saveWithParticipants(
                participant("application-001", "student-001", "MAT-001", "Ana", 1),
                participant("application-001", "student-002", "mat-001", "Bia", 2)
        ));
        expectIllegalArgument(() -> saveWithParticipants(
                participant("application-001", "student-001", "MAT-001", "Ana", 1),
                participant("application-001", "student-002", "MAT-002", "Bia", 1)
        ));

        assertTrue(applicationRepository.loadAll().isEmpty());
    }

    @Test
    public void savesApplicationAndFrozenRosterTogether() {
        prepareAssessmentAndClass();

        batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Arrays.asList(
                        participant("application-001", "student-002", "MAT-002", "Bruno", 2),
                        participant("application-001", "student-001", "MAT-001", "Ana", 1)
                )
        );

        OmrAssessmentApplication stored = applicationRepository
                .findByIdOrNull("application-001");
        List<OmrAssessmentParticipant> roster = participantRepository
                .loadForApplication("application-001");

        assertNotNull(stored);
        assertEquals(application(), stored);
        assertEquals(2, roster.size());
        assertEquals("student-001", roster.get(0).getStudentId());
        assertEquals("student-002", roster.get(1).getStudentId());
    }

    @Test
    public void savesMultipleApplicationsAndRostersAtomically() {
        prepareAssessmentAndClass();
        classRepository.save(classGroup("class-002", "9º B"));

        batchRepository.saveAllNew(
                Arrays.asList(
                        application("application-001", "class-001", 1_000L),
                        application("application-002", "class-002", 2_000L)
                ),
                Arrays.asList(
                        participant("application-001", "student-001", "MAT-001", "Ana", 1),
                        participant("application-002", "student-002", "MAT-002", "Bruno", 1)
                )
        );

        assertEquals(2, applicationRepository.loadAll().size());
        assertEquals(1, participantRepository
                .loadForApplication("application-001").size());
        assertEquals(1, participantRepository
                .loadForApplication("application-002").size());
    }

    @Test
    public void missingAssessmentOrClassRollsBackWholeBatch() {
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.singletonList(participant())
        ));
        assertTrue(applicationRepository.loadAll().isEmpty());

        assessmentRepository.save(assessment());
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.singletonList(participant())
        ));

        assertTrue(applicationRepository.loadAll().isEmpty());
        assertTrue(participantRepository
                .loadForApplication("application-001").isEmpty());
    }

    @Test
    public void duplicateApplicationRollsBackOtherNewApplication() {
        prepareAssessmentAndClass();
        batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.singletonList(participant())
        );

        OmrAssessmentApplication second = application(
                "application-002",
                "class-001",
                2_000L
        );
        expectIllegalArgument(() -> batchRepository.saveAllNew(
                Arrays.asList(second, application()),
                Arrays.asList(
                        participant("application-002", "student-002", "MAT-002", "Bruno", 1),
                        participant()
                )
        ));

        assertNull(applicationRepository.findByIdOrNull(
                "application-002"
        ));
        assertEquals(1, applicationRepository.loadAll().size());
        assertEquals(1, participantRepository
                .loadForApplication("application-001").size());
    }

    @Test
    public void applicationAndRosterPersistAcrossRepositoryReopen() {
        prepareAssessmentAndClass();
        batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.singletonList(participant())
        );

        closeRepositories();
        openRepositories();

        assertEquals(application(), applicationRepository
                .findByIdOrNull("application-001"));
        assertEquals(participant(), participantRepository
                .findOrNull("application-001", "student-001"));
    }

    @Test
    public void laterSourceChangesDoNotRewriteFrozenSnapshot() {
        prepareAssessmentAndClass();
        studentRepository.save(new OmrStudentProfile(
                "student-001",
                "MAT-001",
                "Ana original"
        ));
        OmrAssessmentParticipant snapshot = participant(
                "application-001",
                "student-001",
                "MAT-001",
                "Ana original",
                1
        );
        batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Collections.singletonList(snapshot)
        );

        studentRepository.save(new OmrStudentProfile(
                "student-001",
                "MAT-999",
                "Nome posterior"
        ));
        classRepository.save(classGroup(
                "class-001",
                "Turma renomeada"
        ));

        OmrAssessmentParticipant restored = participantRepository
                .findOrNull("application-001", "student-001");
        assertNotNull(restored);
        assertEquals("MAT-001", restored.getRegistration());
        assertEquals("Ana original", restored.getStudentName());
        assertEquals("9º A", restored.getClassName());
    }

    private void saveWithParticipants(
            OmrAssessmentParticipant first,
            OmrAssessmentParticipant second
    ) {
        batchRepository.saveAllNew(
                Collections.singletonList(application()),
                Arrays.asList(first, second)
        );
    }

    private void prepareAssessmentAndClass() {
        assertTrue(assessmentRepository.save(assessment()));
        classRepository.save(classGroup());
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
                1
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

    private OmrAssessmentApplication application() {
        return application("application-001", "class-001", 1_000L);
    }

    private OmrAssessmentApplication application(
            String applicationId,
            String classGroupId,
            long createdAt
    ) {
        return new OmrAssessmentApplication(
                applicationId,
                "assessment-001",
                2,
                classGroupId,
                createdAt
        );
    }

    private OmrAssessmentParticipant participant() {
        return participant(
                "application-001",
                "student-001",
                "MAT-001",
                "Ana",
                1
        );
    }

    private OmrAssessmentParticipant participant(
            String applicationId,
            String studentId,
            String registration,
            String studentName,
            int rosterPosition
    ) {
        return new OmrAssessmentParticipant(
                applicationId,
                studentId,
                registration,
                studentName,
                applicationId.equals("application-002")
                        ? "9º B"
                        : "9º A",
                rosterPosition
        );
    }

    private void openRepositories() {
        batchRepository =
                new OmrSQLiteAssessmentApplicationBatchRepository(
                        applicationContext,
                        testDatabaseName
                );
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
        assessmentRepository = new OmrSQLiteAssessmentRepository(
                applicationContext,
                testDatabaseName
        );
        classRepository = new OmrSQLiteClassGroupRepository(
                applicationContext,
                testDatabaseName
        );
        studentRepository = new OmrSQLiteStudentRepository(
                applicationContext,
                testDatabaseName
        );
    }

    private void closeRepositories() {
        if (studentRepository != null) {
            studentRepository.close();
            studentRepository = null;
        }
        if (classRepository != null) {
            classRepository.close();
            classRepository = null;
        }
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
        if (participantRepository != null) {
            participantRepository.close();
            participantRepository = null;
        }
        if (applicationRepository != null) {
            applicationRepository.close();
            applicationRepository = null;
        }
        if (batchRepository != null) {
            batchRepository.close();
            batchRepository = null;
        }
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
