package com.example.leitorgabaritoomr.presentation.classroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrClassRosterViewStateTest {

    @Test
    public void emptyRosterHasClassMetadataAndExpectedFlags() {
        OmrClassRosterViewState state = OmrClassRosterViewState.from(
                classGroup(),
                Collections.emptyList(),
                students(),
                null
        );

        assertEquals("class-9a", state.getClassGroupId());
        assertEquals("9º ano A", state.getClassName());
        assertEquals("Ensino Fundamental", state.getGradeLevel());
        assertEquals(2026, state.getSchoolYear());
        assertTrue(state.isRosterEmpty());
        assertFalse(state.hasNoMatches());
        assertFalse(state.hasQuery());
    }

    @Test
    public void blankQueryPreservesRosterPositionOrder() {
        OmrClassRosterViewState state = state(enrollments(), " ");

        assertEquals(3, state.getVisibleCount());
        assertEquals(2, state.getVisibleEnrollments()
                .get(0).getRosterPosition());
        assertEquals(10, state.getVisibleEnrollments()
                .get(1).getRosterPosition());
    }

    @Test
    public void searchMatchesNameIgnoringCaseAndAccents() {
        OmrClassRosterViewState state = state(enrollments(), "JOAO");

        assertEquals(1, state.getVisibleCount());
        assertEquals("João César", state.getVisibleEnrollments()
                .get(0).getName());
    }

    @Test
    public void searchMatchesRegistrationWithLeadingZeros() {
        OmrClassRosterViewState state = state(enrollments(), "00012");

        assertEquals(1, state.getVisibleCount());
        assertEquals("000123", state.getVisibleEnrollments()
                .get(0).getRegistration());
    }

    @Test
    public void searchMatchesExactRosterPosition() {
        OmrClassRosterViewState state = state(enrollments(), "10");

        assertEquals(1, state.getVisibleCount());
        assertEquals("student-1", state.getVisibleEnrollments()
                .get(0).getStudentId());
    }

    @Test
    public void positionSearchDoesNotUsePartialMatch() {
        OmrStudentProfile student = new OmrStudentProfile(
                "student-z", "ZZZ", "Aluno teste"
        );
        OmrClassRosterViewState state = OmrClassRosterViewState.from(
                classGroup(),
                Collections.singletonList(new OmrClassEnrollment(
                        "class-9a", "student-z", 10
                )),
                Collections.singletonList(student),
                "1"
        );

        assertEquals(0, state.getVisibleCount());
        assertTrue(state.hasNoMatches());
    }

    @Test
    public void itemExposesCompleteEnrollmentData() {
        OmrClassRosterViewState.EnrollmentItem item = state(
                enrollments(), "000123"
        ).getVisibleEnrollments().get(0);

        assertEquals("student-1", item.getStudentId());
        assertEquals("000123", item.getRegistration());
        assertEquals("Ana Beatriz", item.getName());
        assertEquals(10, item.getRosterPosition());
        assertTrue(item.hasStudentId(" student-1 "));
        assertFalse(item.hasStudentId(null));
    }

    @Test
    public void findsOnlyVisibleEnrollmentByStudentId() {
        OmrClassRosterViewState state = state(enrollments(), "Ana");

        assertNotNull(state.findVisibleEnrollmentOrNull("student-1"));
        assertNull(state.findVisibleEnrollmentOrNull("student-2"));
        assertNull(state.findVisibleEnrollmentOrNull(null));
    }

    @Test
    public void visibleCollectionIsImmutable() {
        OmrClassRosterViewState state = state(enrollments(), "");

        try {
            state.getVisibleEnrollments().clear();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    @Test
    public void rejectsNullInputsAndMembers() {
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                null, enrollments(), students(), ""
        ));
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(), null, students(), ""
        ));
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(), enrollments(), null, ""
        ));
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                Arrays.asList(enrollment1(), null),
                students(),
                ""
        ));
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                enrollments(),
                Arrays.asList(student1(), null),
                ""
        ));
    }

    @Test
    public void rejectsEnrollmentFromAnotherClass() {
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                Collections.singletonList(new OmrClassEnrollment(
                        "class-other", "student-1", 1
                )),
                students(),
                ""
        ));
    }

    @Test
    public void rejectsMissingStudentReference() {
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                Collections.singletonList(new OmrClassEnrollment(
                        "class-9a", "student-missing", 1
                )),
                students(),
                ""
        ));
    }

    @Test
    public void rejectsRepeatedStudentOrPosition() {
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                Arrays.asList(
                        enrollment1(),
                        new OmrClassEnrollment(
                                "class-9a", "student-1", 20
                        )
                ),
                students(),
                ""
        ));
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                Arrays.asList(
                        enrollment1(),
                        new OmrClassEnrollment(
                                "class-9a", "student-2", 10
                        )
                ),
                students(),
                ""
        ));
    }

    @Test
    public void rejectsRepeatedStudentInCatalog() {
        expectIllegalArgument(() -> OmrClassRosterViewState.from(
                classGroup(),
                enrollments(),
                Arrays.asList(
                        student1(),
                        new OmrStudentProfile(
                                "student-1", "999", "Outro"
                        )
                ),
                ""
        ));
    }

    private static OmrClassRosterViewState state(
            List<OmrClassEnrollment> enrollments,
            String query
    ) {
        return OmrClassRosterViewState.from(
                classGroup(), enrollments, students(), query
        );
    }

    private static OmrClassGroup classGroup() {
        return new OmrClassGroup(
                "class-9a", "9º ano A", "Ensino Fundamental", 2026
        );
    }

    private static List<OmrClassEnrollment> enrollments() {
        return Arrays.asList(
                new OmrClassEnrollment("class-9a", "student-2", 2),
                enrollment1(),
                new OmrClassEnrollment("class-9a", "student-3", 27)
        );
    }

    private static OmrClassEnrollment enrollment1() {
        return new OmrClassEnrollment("class-9a", "student-1", 10);
    }

    private static List<OmrStudentProfile> students() {
        return Arrays.asList(
                student1(),
                new OmrStudentProfile(
                        "student-2", "ABC-9", "João César"
                ),
                new OmrStudentProfile(
                        "student-3", "000777", "Carla Lima"
                )
        );
    }

    private static OmrStudentProfile student1() {
        return new OmrStudentProfile(
                "student-1", "000123", "Ana Beatriz"
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
