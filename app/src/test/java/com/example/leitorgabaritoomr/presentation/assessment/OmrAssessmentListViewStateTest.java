package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAssessmentListViewStateTest {

    @Test
    public void emptyCatalogHasExpectedFlags() {
        OmrAssessmentListViewState state =
                OmrAssessmentListViewState.from(
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
        OmrAssessmentListViewState state = state(assessments(), "   ");

        assertEquals(3, state.getVisibleCount());
        assertEquals(
                "assessment-2027",
                state.getVisibleAssessments().get(0).getAssessmentId()
        );
        assertEquals(
                "assessment-math",
                state.getVisibleAssessments().get(1).getAssessmentId()
        );
    }

    @Test
    public void searchMatchesTitleIgnoringCaseAndAccents() {
        OmrAssessmentListViewState state = state(
                assessments(),
                "AVALIACAO BIMESTRAL"
        );

        assertEquals(1, state.getVisibleCount());
        assertEquals(
                "Avaliação bimestral",
                state.getVisibleAssessments().get(0).getTitle()
        );
    }

    @Test
    public void searchMatchesSubjectGradeAndPeriod() {
        assertEquals(1, state(assessments(), "matematica")
                .getVisibleCount());
        assertEquals(2, state(assessments(), "9 ano")
                .getVisibleCount());
        assertEquals(1, state(assessments(), "diagnostico")
                .getVisibleCount());
    }

    @Test
    public void searchMatchesYearAssessmentAndAnswerKeyIds() {
        assertEquals(1, state(assessments(), "2027")
                .getVisibleCount());
        assertEquals(1, state(assessments(), "assessment-science")
                .getVisibleCount());
        assertEquals(1, state(assessments(), "key-science")
                .getVisibleCount());
    }

    @Test
    public void queryWithoutMatchesIsNotEmptyCatalog() {
        OmrAssessmentListViewState state = state(
                assessments(),
                "inexistente"
        );

        assertFalse(state.isCatalogEmpty());
        assertTrue(state.hasNoMatches());
        assertTrue(state.hasQuery());
        assertEquals(3, state.getTotalCount());
        assertEquals(0, state.getVisibleCount());
    }

    @Test
    public void itemExposesCompleteLatestVersion() {
        OmrAssessmentListViewState.AssessmentItem item =
                state(assessments(), "matematica")
                        .getVisibleAssessments().get(0);

        assertEquals("assessment-math", item.getAssessmentId());
        assertEquals(3, item.getVersion());
        assertEquals("Avaliação bimestral", item.getTitle());
        assertEquals("Matemática", item.getSubject());
        assertEquals("9º ano", item.getGradeLevel());
        assertEquals("2º bimestre", item.getAcademicPeriod());
        assertEquals(2026, item.getSchoolYear());
        assertEquals("key-math", item.getAnswerKeyId());
        assertEquals(2, item.getAnswerKeyVersion());
        assertTrue(item.hasAssessmentId(" assessment-math "));
        assertFalse(item.hasAssessmentId(null));
        assertFalse(item.hasPublishedStructure());
        assertEquals(0, item.getBlockCount());
        assertNull(item.getStructureOrNull());
    }

    @Test
    public void itemExposesPublishedStructureAndSearchesItsMetadata() {
        OmrAssessmentStructure structure = scienceStructure();
        OmrAssessmentListViewState state =
                OmrAssessmentListViewState.from(
                        assessments(),
                        Collections.singletonList(structure),
                        "quimica"
                );

        assertEquals(1, state.getVisibleCount());
        OmrAssessmentListViewState.AssessmentItem item =
                state.getVisibleAssessments().get(0);
        assertEquals("assessment-science", item.getAssessmentId());
        assertTrue(item.hasPublishedStructure());
        assertEquals(2, item.getBlockCount());
        assertEquals(4, item.getStructureQuestionCount());
        assertEquals(structure, item.getStructureOrNull());
        assertEquals(1, OmrAssessmentListViewState.from(
                assessments(),
                Collections.singletonList(structure),
                "natureza"
        ).getVisibleCount());
    }

    @Test
    public void findsOnlyVisibleAssessmentById() {
        OmrAssessmentListViewState state = state(
                assessments(),
                "matematica"
        );

        assertNotNull(state.findVisibleAssessmentOrNull(
                "assessment-math"
        ));
        assertNull(state.findVisibleAssessmentOrNull(
                "assessment-science"
        ));
        assertNull(state.findVisibleAssessmentOrNull(null));
    }

    @Test
    public void visibleCollectionIsImmutable() {
        OmrAssessmentListViewState state = state(assessments(), "");

        try {
            state.getVisibleAssessments().clear();
            fail("Era esperada uma UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
            // Resultado esperado.
        }
    }

    @Test
    public void rejectsNullListAndMember() {
        expectIllegalArgument(() -> OmrAssessmentListViewState.from(
                null,
                ""
        ));
        expectIllegalArgument(() -> OmrAssessmentListViewState.from(
                Arrays.asList(assessmentMath(), null),
                ""
        ));
        expectIllegalArgument(() -> OmrAssessmentListViewState.from(
                assessments(),
                null,
                ""
        ));
        expectIllegalArgument(() -> OmrAssessmentListViewState.from(
                assessments(),
                Collections.singletonList(null),
                ""
        ));
    }

    @Test
    public void rejectsRepeatedStructureIdentity() {
        OmrAssessmentStructure structure = scienceStructure();
        expectIllegalArgument(() -> OmrAssessmentListViewState.from(
                assessments(),
                Arrays.asList(structure, structure),
                ""
        ));
    }

    @Test
    public void rejectsMoreThanOneVersionOfSameAssessment() {
        List<OmrAssessmentDefinition> repeated = Arrays.asList(
                assessmentMath(),
                assessment(
                        "assessment-math",
                        2,
                        "Versão anterior",
                        "Matemática",
                        "9º ano",
                        "1º bimestre",
                        2026,
                        "key-math",
                        1
                )
        );

        expectIllegalArgument(() ->
                OmrAssessmentListViewState.from(repeated, "")
        );
    }

    private static OmrAssessmentListViewState state(
            List<OmrAssessmentDefinition> assessments,
            String query
    ) {
        return OmrAssessmentListViewState.from(assessments, query);
    }

    private static List<OmrAssessmentDefinition> assessments() {
        return Arrays.asList(
                assessment(
                        "assessment-2027",
                        1,
                        "Avaliação inicial",
                        "Português",
                        "1ª série",
                        "Diagnóstico",
                        2027,
                        "key-portuguese",
                        1
                ),
                assessmentMath(),
                assessment(
                        "assessment-science",
                        1,
                        "Prova de Ciências",
                        "Ciências",
                        "9º ano",
                        "3º bimestre",
                        2026,
                        "key-science",
                        1
                )
        );
    }

    private static OmrAssessmentDefinition assessmentMath() {
        return assessment(
                "assessment-math",
                3,
                "Avaliação bimestral",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "key-math",
                2
        );
    }

    private static OmrAssessmentStructure scienceStructure() {
        return new OmrAssessmentStructure(
                "assessment-science",
                1,
                4,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                "block-chemistry",
                                1,
                                "Ciências da Natureza",
                                "QUI",
                                "Química",
                                1,
                                2,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        ),
                        new OmrAssessmentBlock(
                                "block-physics",
                                2,
                                "Ciências da Natureza",
                                "FIS",
                                "Física",
                                3,
                                2,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        )
                )
        );
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title,
            String subject,
            String gradeLevel,
            String academicPeriod,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                id,
                version,
                title,
                subject,
                gradeLevel,
                academicPeriod,
                schoolYear,
                answerKeyId,
                answerKeyVersion
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
