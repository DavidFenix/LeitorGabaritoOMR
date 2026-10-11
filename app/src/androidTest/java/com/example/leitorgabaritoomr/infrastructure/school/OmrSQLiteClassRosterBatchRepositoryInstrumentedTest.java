package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
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
public final class OmrSQLiteClassRosterBatchRepositoryInstrumentedTest {

    private Context applicationContext;
    private String testDatabaseName;
    private OmrSQLiteClassRosterBatchRepository batchRepository;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;

    @Before
    public void setUp() {
        applicationContext = ApplicationProvider.getApplicationContext();
        testDatabaseName = "omr_school_roster_batch_test_"
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
    public void savesCompleteRosterBatch() {
        OmrClassGroup classGroup = classGroup(
                "class-a", "7º A", "Fundamental II", 2026
        );
        OmrStudentProfile ana = student(
                "student-a", "000001", "Ana"
        );
        OmrStudentProfile bruno = student(
                "student-b", "000002", "Bruno"
        );

        batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Arrays.asList(ana, bruno),
                Arrays.asList(
                        enrollment("class-a", "student-a", 1),
                        enrollment("class-a", "student-b", 2)
                )
        );

        assertCompleteClass(
                classGroup,
                classRepository.findByIdOrNull("class-a")
        );
        assertCompleteStudent(
                ana,
                studentRepository.findByIdOrNull("student-a")
        );
        assertEquals(
                2,
                enrollmentRepository.loadForClassGroup(
                        "class-a"
                ).size()
        );
    }

    @Test
    public void reimportReplacesCompleteRosterAndKeepsStudents() {
        OmrClassGroup classGroup = classGroup(
                "class-a", "A", "Etapa", 2026
        );
        OmrStudentProfile ana = student(
                "student-a", "001", "Ana"
        );
        OmrStudentProfile bruno = student(
                "student-b", "002", "Bruno"
        );

        batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Arrays.asList(ana, bruno),
                Arrays.asList(
                        enrollment("class-a", "student-a", 1),
                        enrollment("class-a", "student-b", 2)
                )
        );
        batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Collections.singletonList(bruno),
                Collections.singletonList(enrollment(
                        "class-a", "student-b", 7
                ))
        );

        assertNull(enrollmentRepository.findOrNull(
                "class-a", "student-a"
        ));
        OmrClassEnrollment remaining =
                enrollmentRepository.findOrNull(
                        "class-a", "student-b"
                );
        assertNotNull(remaining);
        assertEquals(7, remaining.getRosterPosition());
        assertNotNull(studentRepository.findByIdOrNull(
                "student-a"
        ));
    }

    @Test
    public void updatesClassAndStudentMetadataWithStableIds() {
        batchRepository.replaceRosters(
                Collections.singletonList(classGroup(
                        "class-a", "A", "Etapa antiga", 2026
                )),
                Collections.singletonList(student(
                        "student-a", "001", "Nome antigo"
                )),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        );

        OmrClassGroup updatedClass = classGroup(
                "class-a", "A atualizada", "Etapa atual", 2026
        );
        OmrStudentProfile updatedStudent = student(
                "student-a", "001", "Ana atualizada"
        );
        batchRepository.replaceRosters(
                Collections.singletonList(updatedClass),
                Collections.singletonList(updatedStudent),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 4
                ))
        );

        assertCompleteClass(
                updatedClass,
                classRepository.findByIdOrNull("class-a")
        );
        assertCompleteStudent(
                updatedStudent,
                studentRepository.findByIdOrNull("student-a")
        );
    }

    @Test
    public void replacementDoesNotChangeUnmentionedClass() {
        OmrClassGroup classA = classGroup(
                "class-a", "A", "Etapa", 2026
        );
        OmrClassGroup classB = classGroup(
                "class-b", "B", "Etapa", 2026
        );
        OmrStudentProfile ana = student(
                "student-a", "001", "Ana"
        );
        OmrStudentProfile bruno = student(
                "student-b", "002", "Bruno"
        );

        batchRepository.replaceRosters(
                Arrays.asList(classA, classB),
                Arrays.asList(ana, bruno),
                Arrays.asList(
                        enrollment("class-a", "student-a", 1),
                        enrollment("class-b", "student-b", 1)
                )
        );
        batchRepository.replaceRosters(
                Collections.singletonList(classA),
                Collections.singletonList(ana),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 5
                ))
        );

        assertNotNull(enrollmentRepository.findOrNull(
                "class-b", "student-b"
        ));
        assertEquals(
                1,
                enrollmentRepository.findOrNull(
                        "class-b", "student-b"
                ).getRosterPosition()
        );
    }

    @Test
    public void rejectsNullOrEmptyCollectionsBeforeWriting() {
        List<OmrClassGroup> classes = Collections.singletonList(
                classGroup("class-a", "A", "Etapa", 2026)
        );
        List<OmrStudentProfile> students = Collections.singletonList(
                student("student-a", "001", "Ana")
        );
        List<OmrClassEnrollment> enrollments =
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ));

        expectIllegalArgument(() -> batchRepository.replaceRosters(
                null, students, enrollments
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                classes, null, enrollments
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                classes, students, null
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.emptyList(), students, enrollments
        ));

        assertTrue(classRepository.loadAll().isEmpty());
        assertTrue(studentRepository.loadAll().isEmpty());
    }

    @Test
    public void rejectsNullOrRepeatedClass() {
        OmrClassGroup first = classGroup(
                "class-a", "A", "Etapa", 2026
        );
        OmrClassGroup repeated = classGroup(
                "class-a", "Outra", "Etapa", 2027
        );
        OmrStudentProfile student = student(
                "student-a", "001", "Ana"
        );

        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Arrays.asList(first, null),
                Collections.singletonList(student),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Arrays.asList(first, repeated),
                Collections.singletonList(student),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        ));

        assertTrue(classRepository.loadAll().isEmpty());
    }

    @Test
    public void rejectsNullDuplicateStudentIdOrRegistration() {
        OmrClassGroup classGroup = classGroup(
                "class-a", "A", "Etapa", 2026
        );
        OmrStudentProfile ana = student(
                "student-a", "ABC", "Ana"
        );

        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Arrays.asList(ana, null),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Arrays.asList(
                        ana,
                        student("student-a", "DEF", "Bruno")
                ),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Arrays.asList(
                        ana,
                        student("student-b", "abc", "Bruno")
                ),
                Arrays.asList(
                        enrollment("class-a", "student-a", 1),
                        enrollment("class-a", "student-b", 2)
                )
        ));

        assertTrue(studentRepository.loadAll().isEmpty());
    }

    @Test
    public void rejectsEnrollmentOutsideBatch() {
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup(
                        "class-a", "A", "Etapa", 2026
                )),
                Collections.singletonList(student(
                        "student-a", "001", "Ana"
                )),
                Collections.singletonList(enrollment(
                        "class-other", "student-a", 1
                ))
        ));

        assertTrue(classRepository.loadAll().isEmpty());
    }

    @Test
    public void rejectsRepeatedStudentOrPositionInClass() {
        OmrClassGroup classGroup = classGroup(
                "class-a", "A", "Etapa", 2026
        );
        OmrStudentProfile ana = student(
                "student-a", "001", "Ana"
        );
        OmrStudentProfile bruno = student(
                "student-b", "002", "Bruno"
        );

        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Collections.singletonList(ana),
                Arrays.asList(
                        enrollment("class-a", "student-a", 1),
                        enrollment("class-a", "student-a", 2)
                )
        ));
        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup),
                Arrays.asList(ana, bruno),
                Arrays.asList(
                        enrollment("class-a", "student-a", 1),
                        enrollment("class-a", "student-b", 1)
                )
        ));

        assertTrue(enrollmentRepository
                .loadForClassGroup("class-a").isEmpty());
    }

    @Test
    public void requiresEveryClassAndStudentToHaveEnrollment() {
        OmrClassGroup classA = classGroup(
                "class-a", "A", "Etapa", 2026
        );
        OmrClassGroup classB = classGroup(
                "class-b", "B", "Etapa", 2026
        );
        OmrStudentProfile ana = student(
                "student-a", "001", "Ana"
        );
        OmrStudentProfile bruno = student(
                "student-b", "002", "Bruno"
        );

        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Arrays.asList(classA, classB),
                Arrays.asList(ana, bruno),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        ));

        assertTrue(classRepository.loadAll().isEmpty());
    }

    @Test
    public void databaseConstraintRollsBackEarlierClassUpdate() {
        OmrClassGroup originalClass = classGroup(
                "class-a", "Nome original", "Etapa", 2026
        );
        OmrStudentProfile existingStudent = student(
                "student-existing", "001", "Ana"
        );
        classRepository.save(originalClass);
        studentRepository.save(existingStudent);
        enrollmentRepository.save(enrollment(
                "class-a", "student-existing", 1
        ));

        expectIllegalArgument(() -> batchRepository.replaceRosters(
                Collections.singletonList(classGroup(
                        "class-a", "Nome alterado", "Etapa", 2026
                )),
                Collections.singletonList(student(
                        "student-new", "001", "Outra pessoa"
                )),
                Collections.singletonList(enrollment(
                        "class-a", "student-new", 2
                ))
        ));

        assertEquals(
                "Nome original",
                classRepository.findByIdOrNull(
                        "class-a"
                ).getName()
        );
        assertNotNull(enrollmentRepository.findOrNull(
                "class-a", "student-existing"
        ));
        assertNull(studentRepository.findByIdOrNull(
                "student-new"
        ));
    }

    @Test
    public void newRepositoryInstancesReadCommittedBatch() {
        batchRepository.replaceRosters(
                Collections.singletonList(classGroup(
                        "class-a", "A", "Etapa", 2026
                )),
                Collections.singletonList(student(
                        "student-a", "001", "Ana"
                )),
                Collections.singletonList(enrollment(
                        "class-a", "student-a", 1
                ))
        );

        closeRepositories();
        openRepositories();

        assertNotNull(classRepository.findByIdOrNull("class-a"));
        assertNotNull(studentRepository.findByIdOrNull("student-a"));
        assertNotNull(enrollmentRepository.findOrNull(
                "class-a", "student-a"
        ));
    }

    private void openRepositories() {
        batchRepository = new OmrSQLiteClassRosterBatchRepository(
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
        if (studentRepository != null) {
            studentRepository.close();
            studentRepository = null;
        }
        if (classRepository != null) {
            classRepository.close();
            classRepository = null;
        }
        if (batchRepository != null) {
            batchRepository.close();
            batchRepository = null;
        }
    }

    private static OmrClassGroup classGroup(
            String id,
            String name,
            String grade,
            int year
    ) {
        return new OmrClassGroup(id, name, grade, year);
    }

    private static OmrStudentProfile student(
            String id,
            String registration,
            String name
    ) {
        return new OmrStudentProfile(id, registration, name);
    }

    private static OmrClassEnrollment enrollment(
            String classId,
            String studentId,
            int position
    ) {
        return new OmrClassEnrollment(classId, studentId, position);
    }

    private static void assertCompleteClass(
            OmrClassGroup expected,
            OmrClassGroup actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getGradeLevel(), actual.getGradeLevel());
        assertEquals(expected.getSchoolYear(), actual.getSchoolYear());
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
