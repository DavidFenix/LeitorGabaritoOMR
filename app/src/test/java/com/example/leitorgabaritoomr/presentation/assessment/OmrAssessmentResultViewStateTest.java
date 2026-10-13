package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultTestData;

import org.junit.Test;

public final class OmrAssessmentResultViewStateTest {

    @Test
    public void rejectsMissingReport() {
        try {
            OmrAssessmentResultViewState.from(null, "");
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    @Test
    public void emptyQueryShowsFrozenRoster() {
        OmrAssessmentResultViewState state =
                OmrAssessmentResultViewState.from(
                        OmrAssessmentResultTestData.report(), null
                );
        assertEquals(3, state.getVisibleCount());
        assertFalse(state.hasQuery());
        assertFalse(state.hasNoMatches());
    }

    @Test
    public void filtersNameIgnoringAccentAndCase() {
        OmrAssessmentResultViewState state = state("ANA SOUZA");
        assertEquals(1, state.getVisibleCount());
        assertEquals("student-1", state.getVisibleRows().get(0)
                .getParticipant().getStudentId());
    }

    @Test
    public void filtersRegistrationAndPosition() {
        assertEquals(1, state("mat-002").getVisibleCount());
        assertEquals(1, state("3").getVisibleCount());
    }

    @Test
    public void filtersOperationalStatuses() {
        assertEquals(1, state("pendente").getVisibleCount());
        assertEquals(1, state("corrigido").getVisibleCount());
        assertEquals(1, state("inconsistente").getVisibleCount());
    }

    @Test
    public void unmatchedQueryExposesEmptyResult() {
        OmrAssessmentResultViewState state = state("ninguém");
        assertTrue(state.hasQuery());
        assertTrue(state.hasNoMatches());
        assertEquals(0, state.getVisibleCount());
    }

    private static OmrAssessmentResultViewState state(String query) {
        return OmrAssessmentResultViewState.from(
                OmrAssessmentResultTestData.report(), query
        );
    }
}
