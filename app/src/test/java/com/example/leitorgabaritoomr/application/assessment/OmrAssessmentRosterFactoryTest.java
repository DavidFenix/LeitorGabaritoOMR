package com.example.leitorgabaritoomr.application.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentRosterFactoryTest {

    private static final long CREATED_AT = 1_799_000_000_000L;

    private final OmrAssessmentRosterFactory factory =
            new OmrAssessmentRosterFactory();

    @Test
    public void createsRosterOrderedByRosterPosition() {
        List<OmrAssessmentParticipant> participants =
                factory.create(
                        application(),
                        classGroup(),
                        Arrays.asList(
                                enrollment("student-002", 2),
                                enrollment("student-001", 1)
                        ),
                        Arrays.asList(
                                student(
                                        "student-002",
                                        "000002",
                                        "Bruno Lima"
                                ),
                                student(
                                        "student-001",
                                        "000001",
                                        "Ana Beatriz"
                                )
                        )
                );

        assertEquals(2, participants.size());
        assertEquals(
                "student-001",
                participants.get(0).getStudentId()
        );
        assertEquals(1, participants.get(0).getRosterPosition());
        assertEquals("9º A", participants.get(0).getClassName());
        assertEquals(
                "student-002",
                participants.get(1).getStudentId()
        );
        assertEquals(2, participants.get(1).getRosterPosition());

        for (OmrAssessmentParticipant participant
                : participants) {

            assertTrue(participant.belongsToApplication(
                    "application-001"
            ));
        }
    }

    @Test
    public void returnedRosterIsImmutable() {
        List<OmrAssessmentParticipant> participants =
                validRoster();

        try {
            participants.add(participants.get(0));
            fail("A lista de participantes deve ser imutável.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    @Test
    public void permitsUnrelatedStudentProfiles() {
        List<OmrAssessmentParticipant> participants =
                factory.create(
                        application(),
                        classGroup(),
                        Collections.singletonList(
                                enrollment("student-001", 1)
                        ),
                        Arrays.asList(
                                student(
                                        "student-999",
                                        "000999",
                                        "Outro Aluno"
                                ),
                                student(
                                        "student-001",
                                        "000001",
                                        "Ana Beatriz"
                                )
                        )
                );

        assertEquals(1, participants.size());
        assertEquals(
                "student-001",
                participants.get(0).getStudentId()
        );
    }

    @Test
    public void rosterIsIndependentFromInputLists() {
        List<OmrClassEnrollment> enrollments =
                new ArrayList<>();
        enrollments.add(enrollment("student-001", 1));

        List<OmrStudentProfile> students =
                new ArrayList<>();
        students.add(student(
                "student-001",
                "000001",
                "Ana Beatriz"
        ));

        List<OmrAssessmentParticipant> participants =
                factory.create(
                        application(),
                        classGroup(),
                        enrollments,
                        students
                );

        enrollments.clear();
        students.clear();

        assertEquals(1, participants.size());
        assertEquals(
                "Ana Beatriz",
                participants.get(0).getStudentName()
        );
    }

    @Test
    public void rejectsMissingApplication() {
        expectIllegalArgument(() -> factory.create(
                null,
                classGroup(),
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                Collections.singletonList(
                        student("student-001", "000001", "Ana")
                )
        ));
    }

    @Test
    public void rejectsMissingClassGroup() {
        expectIllegalArgument(() -> factory.create(
                application(),
                null,
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                Collections.singletonList(
                        student("student-001", "000001", "Ana")
                )
        ));
    }

    @Test
    public void rejectsClassGroupFromAnotherApplication() {
        OmrClassGroup otherClass = new OmrClassGroup(
                "class-002",
                "9º B",
                "9º ano",
                2026
        );

        expectIllegalArgument(() -> factory.create(
                application(),
                otherClass,
                Collections.singletonList(
                        new OmrClassEnrollment(
                                "class-002",
                                "student-001",
                                1
                        )
                ),
                Collections.singletonList(
                        student("student-001", "000001", "Ana")
                )
        ));
    }

    @Test
    public void rejectsMissingOrEmptyEnrollments() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                null,
                Collections.emptyList()
        ));

        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Collections.emptyList(),
                Collections.emptyList()
        ));
    }

    @Test
    public void rejectsMissingStudentCollection() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                null
        ));
    }

    @Test
    public void rejectsNullEnrollment() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Arrays.asList(
                        enrollment("student-001", 1),
                        null
                ),
                Collections.singletonList(
                        student("student-001", "000001", "Ana")
                )
        ));
    }

    @Test
    public void rejectsEnrollmentFromAnotherClass() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Collections.singletonList(
                        new OmrClassEnrollment(
                                "class-002",
                                "student-001",
                                1
                        )
                ),
                Collections.singletonList(
                        student("student-001", "000001", "Ana")
                )
        ));
    }

    @Test
    public void rejectsRepeatedStudentEnrollment() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Arrays.asList(
                        enrollment("student-001", 1),
                        enrollment("student-001", 2)
                ),
                Collections.singletonList(
                        student("student-001", "000001", "Ana")
                )
        ));
    }

    @Test
    public void rejectsRepeatedRosterPosition() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Arrays.asList(
                        enrollment("student-001", 1),
                        enrollment("student-002", 1)
                ),
                Arrays.asList(
                        student("student-001", "000001", "Ana"),
                        student("student-002", "000002", "Bruno")
                )
        ));
    }

    @Test
    public void rejectsMissingEnrolledStudentProfile() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                Collections.singletonList(
                        student("student-999", "000999", "Outro")
                )
        ));
    }

    @Test
    public void rejectsNullStudentProfile() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                Arrays.asList(
                        student("student-001", "000001", "Ana"),
                        null
                )
        ));
    }

    @Test
    public void rejectsRepeatedStudentProfileId() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                Arrays.asList(
                        student("student-001", "000001", "Ana"),
                        student("student-001", "000999", "Outra Ana")
                )
        ));
    }

    @Test
    public void rejectsRepeatedRegistrationInRoster() {
        expectIllegalArgument(() -> factory.create(
                application(),
                classGroup(),
                Arrays.asList(
                        enrollment("student-001", 1),
                        enrollment("student-002", 2)
                ),
                Arrays.asList(
                        student("student-001", "000001", "Ana"),
                        student("student-002", "000001", "Bruno")
                )
        ));
    }

    private List<OmrAssessmentParticipant> validRoster() {
        return factory.create(
                application(),
                classGroup(),
                Collections.singletonList(
                        enrollment("student-001", 1)
                ),
                Collections.singletonList(
                        student(
                                "student-001",
                                "000001",
                                "Ana Beatriz"
                        )
                )
        );
    }

    private OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-001",
                "assessment-001",
                1,
                "class-001",
                CREATED_AT
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

    private OmrClassEnrollment enrollment(
            String studentId,
            int rosterPosition
    ) {
        return new OmrClassEnrollment(
                "class-001",
                studentId,
                rosterPosition
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
}
