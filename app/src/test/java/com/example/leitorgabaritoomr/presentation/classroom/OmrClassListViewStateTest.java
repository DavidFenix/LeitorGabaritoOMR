package com.example.leitorgabaritoomr.presentation.classroom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmrClassListViewStateTest {

    @Test
    public void emptyCatalogHasExpectedFlags() {
        OmrClassListViewState state = OmrClassListViewState.from(
                Collections.emptyList(),
                Collections.emptyMap(),
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
        OmrClassListViewState state = state(classes(), "   ");

        assertEquals(3, state.getVisibleCount());
        assertEquals(
                "class-2027",
                state.getVisibleClasses().get(0).getClassGroupId()
        );
        assertEquals(
                "class-9a",
                state.getVisibleClasses().get(1).getClassGroupId()
        );
    }

    @Test
    public void searchMatchesNameIgnoringCaseAndAccents() {
        OmrClassListViewState state = state(classes(), "NONO A");

        assertEquals(1, state.getVisibleCount());
        assertEquals("Nono Á", state.getVisibleClasses().get(0).getName());
    }

    @Test
    public void searchMatchesGradeLevelIgnoringAccents() {
        OmrClassListViewState state = state(classes(), "medio");

        assertEquals(1, state.getVisibleCount());
        assertEquals("class-2027", state.getVisibleClasses()
                .get(0).getClassGroupId());
    }

    @Test
    public void searchMatchesSchoolYear() {
        OmrClassListViewState state = state(classes(), "2026");

        assertEquals(2, state.getVisibleCount());
        assertTrue(state.hasQuery());
    }

    @Test
    public void queryWithoutMatchesIsNotEmptyCatalog() {
        OmrClassListViewState state = state(classes(), "inexistente");

        assertFalse(state.isCatalogEmpty());
        assertTrue(state.hasNoMatches());
        assertEquals(3, state.getTotalCount());
        assertEquals(0, state.getVisibleCount());
    }

    @Test
    public void itemExposesCompleteClassAndCount() {
        OmrClassListViewState.ClassItem item = state(
                classes(), "Nono"
        ).getVisibleClasses().get(0);

        assertEquals("class-9a", item.getClassGroupId());
        assertEquals("Nono Á", item.getName());
        assertEquals("Ensino Fundamental", item.getGradeLevel());
        assertEquals(2026, item.getSchoolYear());
        assertEquals(36, item.getStudentCount());
        assertTrue(item.hasClassGroupId(" class-9a "));
        assertFalse(item.hasClassGroupId(null));
    }

    @Test
    public void findsOnlyVisibleClassById() {
        OmrClassListViewState state = state(classes(), "Nono");

        assertNotNull(state.findVisibleClassOrNull("class-9a"));
        assertNull(state.findVisibleClassOrNull("class-8b"));
        assertNull(state.findVisibleClassOrNull(null));
    }

    @Test
    public void visibleCollectionIsImmutable() {
        OmrClassListViewState state = state(classes(), "");

        try {
            state.getVisibleClasses().clear();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    @Test
    public void rejectsNullInputsAndMember() {
        expectIllegalArgument(() -> OmrClassListViewState.from(
                null, counts(), ""
        ));
        expectIllegalArgument(() -> OmrClassListViewState.from(
                classes(), null, ""
        ));
        expectIllegalArgument(() -> OmrClassListViewState.from(
                Arrays.asList(class9a(), null), counts(), ""
        ));
    }

    @Test
    public void rejectsRepeatedClassId() {
        List<OmrClassGroup> repeated = Arrays.asList(
                class9a(),
                new OmrClassGroup(
                        "class-9a", "Outra", "Outra etapa", 2026
                )
        );

        expectIllegalArgument(() -> OmrClassListViewState.from(
                repeated, counts(), ""
        ));
    }

    @Test
    public void rejectsMissingOrNegativeCount() {
        Map<String, Integer> missing = new LinkedHashMap<>(counts());
        missing.remove("class-9a");
        expectIllegalArgument(() -> OmrClassListViewState.from(
                classes(), missing, ""
        ));

        Map<String, Integer> negative = new LinkedHashMap<>(counts());
        negative.put("class-9a", -1);
        expectIllegalArgument(() -> OmrClassListViewState.from(
                classes(), negative, ""
        ));
    }

    private static OmrClassListViewState state(
            List<OmrClassGroup> classes,
            String query
    ) {
        return OmrClassListViewState.from(classes, counts(), query);
    }

    private static List<OmrClassGroup> classes() {
        return Arrays.asList(
                new OmrClassGroup(
                        "class-2027", "1ª série A", "Ensino Médio", 2027
                ),
                class9a(),
                new OmrClassGroup(
                        "class-8b", "8º ano B", "Ensino Fundamental", 2026
                )
        );
    }

    private static OmrClassGroup class9a() {
        return new OmrClassGroup(
                "class-9a", "Nono Á", "Ensino Fundamental", 2026
        );
    }

    private static Map<String, Integer> counts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("class-2027", 31);
        result.put("class-9a", 36);
        result.put("class-8b", 0);
        return result;
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
