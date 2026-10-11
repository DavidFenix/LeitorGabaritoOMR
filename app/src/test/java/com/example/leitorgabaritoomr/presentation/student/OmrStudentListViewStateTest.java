package com.example.leitorgabaritoomr.presentation.student;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class OmrStudentListViewStateTest {

    @Test
    public void emptyCatalogHasExpectedFlags() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        Collections.emptyList(),
                        null
                );

        assertTrue(state.isCatalogEmpty());
        assertFalse(state.hasNoMatches());
        assertFalse(state.hasQuery());
        assertEquals(0, state.getTotalCount());
        assertEquals(0, state.getVisibleCount());
    }

    @Test
    public void blankQueryPreservesRepositoryOrder() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        students(),
                        "   "
                );

        assertEquals(3, state.getTotalCount());
        assertEquals(3, state.getVisibleCount());
        assertEquals(
                "student-2",
                state.getVisibleStudents().get(0).getStudentId()
        );
        assertEquals(
                "student-1",
                state.getVisibleStudents().get(1).getStudentId()
        );
    }

    @Test
    public void registrationSearchPreservesLeadingZeros() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        students(),
                        "00012"
                );

        assertEquals(1, state.getVisibleCount());
        assertEquals(
                "000123",
                state.getVisibleStudents().get(0).getRegistration()
        );
    }

    @Test
    public void searchIgnoresCaseAndAccents() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        students(),
                        "JOAO"
                );

        assertEquals(1, state.getVisibleCount());
        assertEquals(
                "João César",
                state.getVisibleStudents().get(0).getName()
        );
    }

    @Test
    public void searchCanMatchMiddleOfName() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        students(),
                        "beatriz"
                );

        assertEquals(1, state.getVisibleCount());
        assertEquals(
                "Ana Beatriz",
                state.getVisibleStudents().get(0).getName()
        );
    }

    @Test
    public void queryWithoutMatchesIsNotEmptyCatalog() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        students(),
                        "inexistente"
                );

        assertFalse(state.isCatalogEmpty());
        assertTrue(state.hasNoMatches());
        assertTrue(state.hasQuery());
        assertEquals(3, state.getTotalCount());
        assertEquals(0, state.getVisibleCount());
    }

    @Test
    public void itemExposesCompleteStudentData() {
        OmrStudentListViewState.StudentItem item =
                OmrStudentListViewState.from(
                        students(),
                        "000123"
                ).getVisibleStudents().get(0);

        assertEquals("student-1", item.getStudentId());
        assertEquals("000123", item.getRegistration());
        assertEquals("Ana Beatriz", item.getName());
        assertTrue(item.hasStudentId(" student-1 "));
        assertFalse(item.hasStudentId(null));
    }

    @Test
    public void findsOnlyVisibleStudentById() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(
                        students(),
                        "ana"
                );

        assertNotNull(state.findVisibleStudentOrNull("student-1"));
        assertNull(state.findVisibleStudentOrNull("student-2"));
        assertNull(state.findVisibleStudentOrNull(null));
    }

    @Test
    public void visibleCollectionIsImmutable() {
        OmrStudentListViewState state =
                OmrStudentListViewState.from(students(), "");

        try {
            state.getVisibleStudents().clear();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    @Test
    public void rejectsNullCollectionOrMember() {
        expectIllegalArgument(() ->
                OmrStudentListViewState.from(null, "")
        );
        expectIllegalArgument(() ->
                OmrStudentListViewState.from(
                        Arrays.asList(student1(), null),
                        ""
                )
        );
    }

    @Test
    public void rejectsRepeatedStudentId() {
        expectIllegalArgument(() ->
                OmrStudentListViewState.from(
                        Arrays.asList(
                                student1(),
                                new OmrStudentProfile(
                                        "student-1",
                                        "000999",
                                        "Outro aluno"
                                )
                        ),
                        ""
                )
        );
    }

    @Test
    public void rejectsRepeatedRegistration() {
        expectIllegalArgument(() ->
                OmrStudentListViewState.from(
                        Arrays.asList(
                                student1(),
                                new OmrStudentProfile(
                                        "student-other",
                                        "000123",
                                        "Outro aluno"
                                )
                        ),
                        ""
                )
        );
    }

    private static java.util.List<OmrStudentProfile> students() {
        return Arrays.asList(
                new OmrStudentProfile(
                        "student-2",
                        "ABC-9",
                        "João César"
                ),
                student1(),
                new OmrStudentProfile(
                        "student-3",
                        "000777",
                        "Carla Lima"
                )
        );
    }

    private static OmrStudentProfile student1() {
        return new OmrStudentProfile(
                "student-1",
                "000123",
                "Ana Beatriz"
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
