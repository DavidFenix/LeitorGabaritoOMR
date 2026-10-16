package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentListViewBinderInstrumentedTest {

    private View rootView;
    private OmrAssessmentListViewBinder binder;
    private RecordingActions actions;

    @Before
    public void setUp() {
        runOnMain(() -> {
            Context context = new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.Theme_LeitorGabaritoOMR
            );
            FrameLayout parent = new FrameLayout(context);
            rootView = LayoutInflater.from(context).inflate(
                    R.layout.activity_omr_assessment_list,
                    parent,
                    false
            );
            binder = new OmrAssessmentListViewBinder(rootView);
            actions = new RecordingActions();
            binder.setOnAssessmentActionListener(actions);
            binder.setOnSearchQueryChangedListener(actions);
            binder.setOnIdentifierCopyListener(actions);
            binder.setOnBackClickListener(
                    view -> actions.backCount++
            );
            binder.setOnCreateClickListener(
                    view -> actions.createCount++
            );
            binder.setOnImportCsvClickListener(
                    view -> actions.importCount++
            );
            binder.setOnImportBlocksCsvClickListener(
                    view -> actions.importBlocksCount++
            );
        });
    }

    @After
    public void tearDown() {
        runOnMain(() -> {
            if (binder != null) {
                binder.release();
            }
            binder = null;
            actions = null;
            rootView = null;
        });
    }

    @Test
    public void rejectsMissingRootOrState() {
        runOnMain(() -> {
            expectIllegalArgument(() ->
                    new OmrAssessmentListViewBinder(null)
            );
            expectIllegalArgument(() -> binder.render(null));
        });
    }

    @Test
    public void emptyCatalogShowsEmptyStateAndCount() {
        runOnMain(() -> {
            binder.render(state(Collections.emptyList(), ""));

            assertText(
                    R.id.textOmrAssessmentListCount,
                    "0 provas cadastradas"
            );
            assertVisibility(
                    R.id.containerOmrAssessmentListEmpty,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrAssessments, View.GONE);
            assertVisibility(
                    R.id.containerOmrAssessmentListNoResults,
                    View.GONE
            );
        });
    }

    @Test
    public void populatedCatalogRendersCompleteRows() {
        runOnMain(() -> {
            binder.render(state(assessments(), ""));

            assertText(
                    R.id.textOmrAssessmentListCount,
                    "2 provas cadastradas"
            );
            assertEquals(2, listView().getAdapter().getCount());
            assertVisibility(R.id.listOmrAssessments, View.VISIBLE);

            View first = rowAt(0);
            assertRowText(
                    first,
                    R.id.textOmrAssessmentItemTitle,
                    "Avaliação bimestral"
            );
            assertRowText(
                    first,
                    R.id.textOmrAssessmentItemMetadata,
                    "Matemática • 9º ano • 2026"
            );
            assertRowText(
                    first,
                    R.id.textOmrAssessmentItemPeriodVersion,
                    "2º bimestre • versão 3"
            );
            assertRowText(
                    first,
                    R.id.textOmrAssessmentItemIdentity,
                    "ID da prova: assessment-math • toque para copiar"
            );
            assertRowText(
                    first,
                    R.id.textOmrAssessmentItemAnswerKey,
                    "Gabarito: key-math • versão 2 • toque para copiar o ID"
            );
            assertRowText(
                    first,
                    R.id.textOmrAssessmentItemStructure,
                    "Blocos curriculares ainda não configurados"
            );
        });
    }

    @Test
    public void identifierLinesDispatchExactAssessmentAndAnswerKeyIds() {
        runOnMain(() -> {
            binder.render(state(assessments(), ""));

            View first = rowAt(0);
            first.findViewById(
                    R.id.textOmrAssessmentItemIdentity
            ).performClick();

            assertEquals(1, actions.copyCount);
            assertEquals("assessment-math", actions.lastCopiedIdentifier);

            first.findViewById(
                    R.id.textOmrAssessmentItemAnswerKey
            ).performClick();

            assertEquals(2, actions.copyCount);
            assertEquals("key-math", actions.lastCopiedIdentifier);
        });
    }

    @Test
    public void filteredStateShowsVisibleAndTotalCounts() {
        runOnMain(() -> {
            binder.render(state(assessments(), "ciências"));

            assertText(
                    R.id.textOmrAssessmentListCount,
                    "1 de 2 provas encontradas"
            );
            assertEquals(1, listView().getAdapter().getCount());
        });
    }

    @Test
    public void unmatchedFilterShowsNoResults() {
        runOnMain(() -> {
            binder.render(state(assessments(), "inexistente"));

            assertVisibility(
                    R.id.containerOmrAssessmentListEmpty,
                    View.GONE
            );
            assertVisibility(
                    R.id.containerOmrAssessmentListNoResults,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrAssessments, View.GONE);
        });
    }

    @Test
    public void searchTextDispatchesExactQuery() {
        runOnMain(() -> {
            searchEditText().setText("2026");

            assertEquals(1, actions.searchCount);
            assertEquals("2026", actions.lastQuery);
            assertEquals("2026", binder.getSearchQuery());
        });
    }

    @Test
    public void footerButtonsDispatchActions() {
        runOnMain(() -> {
            button(R.id.buttonOmrAssessmentListBack).performClick();
            button(R.id.buttonOmrAssessmentListCreate).performClick();
            button(R.id.buttonOmrAssessmentListImportCsv).performClick();
            button(
                    R.id.buttonOmrAssessmentListImportBlocksCsv
            ).performClick();

            assertEquals(1, actions.backCount);
            assertEquals(1, actions.createCount);
            assertEquals(1, actions.importCount);
            assertEquals(1, actions.importBlocksCount);
        });
    }

    @Test
    public void rowActionDispatchesExactAssessmentId() {
        runOnMain(() -> {
            binder.render(state(assessments(), ""));

            Button revision = rowAt(1).findViewById(
                    R.id.buttonOmrAssessmentItemNewVersion
            );
            revision.performClick();

            assertEquals(1, actions.revisionCount);
            assertEquals("assessment-science", actions.lastAssessmentId);

            Button blocks = rowAt(0).findViewById(
                    R.id.buttonOmrAssessmentItemStructure
            );
            blocks.performClick();
            assertEquals(1, actions.structureCount);
            assertEquals("assessment-math", actions.lastAssessmentId);
        });
    }

    @Test
    public void publishedStructureChangesRowSummaryAndAction() {
        runOnMain(() -> {
            binder.render(OmrAssessmentListViewState.from(
                    assessments(),
                    Collections.singletonList(scienceStructure()),
                    ""
            ));

            View science = rowAt(1);
            assertRowText(
                    science,
                    R.id.textOmrAssessmentItemStructure,
                    "2 componentes • 4 questões"
            );
            assertRowText(
                    science,
                    R.id.buttonOmrAssessmentItemStructure,
                    "Ver blocos"
            );
        });
    }

    @Test
    public void busyStateBlocksMutationsAndRestoresContent() {
        runOnMain(() -> {
            binder.render(state(assessments(), ""));
            binder.setBusy(true);

            assertVisibility(
                    R.id.progressOmrAssessmentList,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrAssessments, View.GONE);
            assertFalse(searchEditText().isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAssessmentListCreate
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAssessmentListImportCsv
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAssessmentListImportBlocksCsv
            ).isEnabled());

            binder.setBusy(false);
            assertVisibility(
                    R.id.progressOmrAssessmentList,
                    View.GONE
            );
            assertVisibility(R.id.listOmrAssessments, View.VISIBLE);
            assertTrue(searchEditText().isEnabled());
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();

            expectIllegalState(() ->
                    binder.render(state(assessments(), ""))
            );
            expectIllegalState(() -> binder.setBusy(false));
            expectIllegalState(binder::getSearchQuery);
        });
    }

    private OmrAssessmentListViewState state(
            List<OmrAssessmentDefinition> values,
            String query
    ) {
        return OmrAssessmentListViewState.from(values, query);
    }

    private List<OmrAssessmentDefinition> assessments() {
        return Arrays.asList(
                assessment(
                        "assessment-math",
                        3,
                        "Avaliação bimestral",
                        "Matemática",
                        "9º ano",
                        "2º bimestre",
                        2026,
                        "key-math",
                        2
                ),
                assessment(
                        "assessment-science",
                        1,
                        "Diagnóstico",
                        "Ciências",
                        "8º ano",
                        "Inicial",
                        2025,
                        "key-science",
                        1
                )
        );
    }

    private static OmrAssessmentDefinition assessment(
            String id,
            int version,
            String title,
            String subject,
            String grade,
            String period,
            int year,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                id,
                version,
                title,
                subject,
                grade,
                period,
                year,
                answerKeyId,
                answerKeyVersion
        );
    }

    private static OmrAssessmentStructure scienceStructure() {
        return new OmrAssessmentStructure(
                "assessment-science",
                1,
                4,
                Arrays.asList(
                        new OmrAssessmentBlock(
                                "chemistry",
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
                                "physics",
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

    private ListView listView() {
        return rootView.findViewById(R.id.listOmrAssessments);
    }

    private View rowAt(int position) {
        return listView().getAdapter().getView(
                position,
                null,
                listView()
        );
    }

    private TextInputEditText searchEditText() {
        return rootView.findViewById(R.id.editOmrAssessmentSearch);
    }

    private Button button(int id) {
        return rootView.findViewById(id);
    }

    private void assertText(int id, String expected) {
        TextView text = rootView.findViewById(id);
        assertEquals(expected, text.getText().toString());
    }

    private static void assertRowText(
            View row,
            int id,
            String expected
    ) {
        TextView text = row.findViewById(id);
        assertEquals(expected, text.getText().toString());
    }

    private void assertVisibility(int id, int expected) {
        assertEquals(expected, rootView.findViewById(id).getVisibility());
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(action);
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalStateException.");
        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
    }

    private static final class RecordingActions
            implements OmrAssessmentListViewBinder
            .OnAssessmentActionListener,
            OmrAssessmentListViewBinder.OnSearchQueryChangedListener,
            OmrAssessmentListViewBinder.OnIdentifierCopyListener {

        private int revisionCount;
        private int searchCount;
        private int backCount;
        private int createCount;
        private int importCount;
        private int importBlocksCount;
        private int structureCount;
        private int copyCount;
        private String lastAssessmentId;
        private String lastCopiedIdentifier;
        private String lastQuery;

        @Override
        public void onCreateNewVersion(String assessmentId) {
            revisionCount++;
            lastAssessmentId = assessmentId;
        }

        @Override
        public void onManageStructure(String assessmentId) {
            structureCount++;
            lastAssessmentId = assessmentId;
        }

        @Override
        public void onSearchQueryChanged(String query) {
            searchCount++;
            lastQuery = query;
        }

        @Override
        public void onCopyIdentifier(String identifier) {
            copyCount++;
            lastCopiedIdentifier = identifier;
        }
    }
}
