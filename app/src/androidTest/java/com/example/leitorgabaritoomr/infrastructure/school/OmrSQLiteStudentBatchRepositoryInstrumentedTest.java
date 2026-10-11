package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
public final class OmrSQLiteStudentBatchRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteStudentRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();
        testDatabaseName =
                "omr_school_student_batch_test_"
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
    public void emptyBatchIsAcceptedWithoutCreatingRows() {
        repository.saveAll(Collections.emptyList());

        assertTrue(repository.loadAll().isEmpty());
    }

    @Test
    public void savesCompleteBatch() {
        List<OmrStudentProfile> students = Arrays.asList(
                student("student-1", "000001", "Ana"),
                student("student-2", "000002", "Bruno"),
                student("student-3", "000003", "Carla")
        );

        repository.saveAll(students);

        assertEquals(3, repository.loadAll().size());
        assertCompleteStudent(
                students.get(0),
                repository.findByRegistrationOrNull("000001")
        );
        assertCompleteStudent(
                students.get(2),
                repository.findByIdOrNull("student-3")
        );
    }

    @Test
    public void updatesCompleteBatchAndKeepsStableIds() {
        repository.saveAll(Arrays.asList(
                student("student-1", "000001", "Ana antiga"),
                student("student-2", "000002", "Bruno antigo")
        ));

        List<OmrStudentProfile> updated = Arrays.asList(
                student("student-1", "000001", "Ana atualizada"),
                student("student-2", "000020", "Bruno atualizado")
        );

        repository.saveAll(updated);

        assertEquals(2, repository.loadAll().size());
        assertCompleteStudent(
                updated.get(0),
                repository.findByIdOrNull("student-1")
        );
        assertCompleteStudent(
                updated.get(1),
                repository.findByRegistrationOrNull("000020")
        );
        assertNull(repository.findByRegistrationOrNull("000002"));
    }

    @Test
    public void rejectsNullBatchOrMemberBeforeWriting() {
        OmrStudentProfile existing = student(
                "student-existing",
                "000001",
                "Ana"
        );
        repository.save(existing);

        expectIllegalArgument(() -> repository.saveAll(null));
        expectIllegalArgument(() -> repository.saveAll(
                Arrays.asList(
                        student("student-new", "000002", "Bruno"),
                        null
                )
        ));

        assertEquals(1, repository.loadAll().size());
        assertCompleteStudent(
                existing,
                repository.findByIdOrNull("student-existing")
        );
        assertNull(repository.findByIdOrNull("student-new"));
    }

    @Test
    public void rejectsRepeatedStudentIdBeforeWriting() {
        expectIllegalArgument(() -> repository.saveAll(
                Arrays.asList(
                        student("student-1", "000001", "Ana"),
                        student("student-1", "000002", "Bruno")
                )
        ));

        assertTrue(repository.loadAll().isEmpty());
    }

    @Test
    public void rejectsRepeatedRegistrationBeforeWriting() {
        expectIllegalArgument(() -> repository.saveAll(
                Arrays.asList(
                        student("student-1", "ABC-1", "Ana"),
                        student("student-2", "abc-1", "Bruno")
                )
        ));

        assertTrue(repository.loadAll().isEmpty());
    }

    @Test
    public void databaseConflictRollsBackEarlierRowsFromSameBatch() {
        OmrStudentProfile existing = student(
                "student-existing",
                "000001",
                "Ana"
        );
        repository.save(existing);

        expectIllegalArgument(() -> repository.saveAll(
                Arrays.asList(
                        student("student-new-1", "000002", "Bruno"),
                        student("student-new-2", "000001", "Carla")
                )
        ));

        assertEquals(1, repository.loadAll().size());
        assertCompleteStudent(
                existing,
                repository.findByIdOrNull("student-existing")
        );
        assertNull(repository.findByIdOrNull("student-new-1"));
        assertNull(repository.findByIdOrNull("student-new-2"));
    }

    @Test
    public void newRepositoryInstanceReadsPreviouslySavedBatch() {
        List<OmrStudentProfile> students = Arrays.asList(
                student("student-1", "000001", "Ana"),
                student("student-2", "000002", "Bruno")
        );

        repository.saveAll(students);
        closeRepository();
        openRepository();

        assertEquals(2, repository.loadAll().size());
        assertCompleteStudent(
                students.get(1),
                repository.findByRegistrationOrNull("000002")
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

    private static OmrStudentProfile student(
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

    private static void assertCompleteStudent(
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

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
