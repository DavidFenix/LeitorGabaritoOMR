package com.example.leitorgabaritoomr.presentation.classroom;

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
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public final class OmrClassListViewBinderInstrumentedTest {

    private View rootView;
    private OmrClassListViewBinder binder;
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
                    R.layout.activity_omr_class_list,
                    parent,
                    false
            );
            binder = new OmrClassListViewBinder(rootView);
            actions = new RecordingActions();
            binder.setOnClassActionListener(actions);
            binder.setOnSearchQueryChangedListener(actions);
            binder.setOnBackClickListener(
                    view -> actions.backCount++
            );
            binder.setOnCreateClickListener(
                    view -> actions.createCount++
            );
            binder.setOnImportCsvClickListener(
                    view -> actions.importCount++
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
            expectIllegalArgument(() -> new OmrClassListViewBinder(null));
            expectIllegalArgument(() -> binder.render(null));
        });
    }

    @Test
    public void emptyCatalogShowsEmptyStateAndCount() {
        runOnMain(() -> {
            binder.render(state(Collections.emptyList(), ""));

            assertText(R.id.textOmrClassListCount, "0 turmas cadastradas");
            assertVisibility(
                    R.id.containerOmrClassListEmpty, View.VISIBLE
            );
            assertVisibility(R.id.listOmrClasses, View.GONE);
            assertVisibility(
                    R.id.containerOmrClassListNoResults, View.GONE
            );
        });
    }

    @Test
    public void populatedCatalogRendersCompleteRows() {
        runOnMain(() -> {
            binder.render(state(classes(), ""));

            assertText(R.id.textOmrClassListCount, "2 turmas cadastradas");
            assertEquals(2, listView().getAdapter().getCount());
            View firstRow = rowAt(0);
            assertRowText(firstRow, R.id.textOmrClassItemName, "9º ano A");
            assertRowText(
                    firstRow,
                    R.id.textOmrClassItemMetadata,
                    "Ensino Fundamental • 2026"
            );
            assertRowText(
                    firstRow,
                    R.id.textOmrClassItemStudentCount,
                    "36 alunos na lista"
            );
        });
    }

    @Test
    public void singularStudentCountUsesSingularResource() {
        runOnMain(() -> {
            binder.render(state(classes(), "8º"));
            assertRowText(
                    rowAt(0),
                    R.id.textOmrClassItemStudentCount,
                    "1 aluno na lista"
            );
        });
    }

    @Test
    public void filteredStateShowsVisibleAndTotalCounts() {
        runOnMain(() -> {
            binder.render(state(classes(), "8º"));
            assertText(
                    R.id.textOmrClassListCount,
                    "1 de 2 turmas encontradas"
            );
            assertEquals(1, listView().getAdapter().getCount());
        });
    }

    @Test
    public void unmatchedFilterShowsNoResults() {
        runOnMain(() -> {
            binder.render(state(classes(), "inexistente"));
            assertVisibility(
                    R.id.containerOmrClassListEmpty, View.GONE
            );
            assertVisibility(
                    R.id.containerOmrClassListNoResults, View.VISIBLE
            );
            assertVisibility(R.id.listOmrClasses, View.GONE);
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
            button(R.id.buttonOmrClassListBack).performClick();
            button(R.id.buttonOmrClassListCreate).performClick();
            button(R.id.buttonOmrClassListImportCsv).performClick();

            assertEquals(1, actions.backCount);
            assertEquals(1, actions.createCount);
            assertEquals(1, actions.importCount);
        });
    }

    @Test
    public void rowButtonsDispatchExactClassId() {
        runOnMain(() -> {
            binder.render(state(classes(), ""));
            View secondRow = rowAt(1);
            Button roster = secondRow.findViewById(
                    R.id.buttonOmrClassItemRoster
            );
            Button edit = secondRow.findViewById(
                    R.id.buttonOmrClassItemEdit
            );
            roster.performClick();
            edit.performClick();

            assertEquals(1, actions.rosterCount);
            assertEquals(1, actions.editCount);
            assertEquals("class-8b", actions.lastRosterClassId);
            assertEquals("class-8b", actions.lastEditClassId);
        });
    }

    @Test
    public void busyStateBlocksActionsAndRestoresContent() {
        runOnMain(() -> {
            binder.render(state(classes(), ""));
            binder.setBusy(true);

            assertVisibility(R.id.progressOmrClassList, View.VISIBLE);
            assertVisibility(R.id.listOmrClasses, View.GONE);
            assertFalse(searchEditText().isEnabled());
            assertFalse(button(R.id.buttonOmrClassListCreate).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrClassListImportCsv
            ).isEnabled());

            binder.setBusy(false);
            assertVisibility(R.id.progressOmrClassList, View.GONE);
            assertVisibility(R.id.listOmrClasses, View.VISIBLE);
            assertTrue(searchEditText().isEnabled());
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();
            expectIllegalState(() -> binder.render(state(classes(), "")));
            expectIllegalState(() -> binder.setBusy(false));
            expectIllegalState(binder::getSearchQuery);
        });
    }

    private OmrClassListViewState state(
            List<OmrClassGroup> classes,
            String query
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OmrClassGroup classGroup : classes) {
            counts.put(
                    classGroup.getClassGroupId(),
                    classGroup.getClassGroupId().equals("class-9a")
                            ? 36
                            : 1
            );
        }
        return OmrClassListViewState.from(classes, counts, query);
    }

    private List<OmrClassGroup> classes() {
        return Arrays.asList(
                new OmrClassGroup(
                        "class-9a", "9º ano A", "Ensino Fundamental", 2026
                ),
                new OmrClassGroup(
                        "class-8b", "8º ano B", "Ensino Fundamental", 2026
                )
        );
    }

    private View rowAt(int position) {
        return listView().getAdapter().getView(
                position, null, listView()
        );
    }

    private ListView listView() {
        return rootView.findViewById(R.id.listOmrClasses);
    }

    private TextInputEditText searchEditText() {
        return rootView.findViewById(R.id.editOmrClassSearch);
    }

    private Button button(int viewId) {
        return rootView.findViewById(viewId);
    }

    private void assertText(int viewId, String expected) {
        TextView view = rootView.findViewById(viewId);
        assertEquals(expected, view.getText().toString());
    }

    private void assertRowText(View row, int viewId, String expected) {
        TextView view = row.findViewById(viewId);
        assertEquals(expected, view.getText().toString());
    }

    private void assertVisibility(int viewId, int expected) {
        assertEquals(expected, rootView.findViewById(viewId).getVisibility());
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalStateException.");
        } catch (IllegalStateException expected) {
            // Resultado esperado.
        }
    }

    private static final class RecordingActions
            implements OmrClassListViewBinder.OnClassActionListener,
            OmrClassListViewBinder.OnSearchQueryChangedListener {
        private int backCount;
        private int createCount;
        private int importCount;
        private int rosterCount;
        private int editCount;
        private int searchCount;
        private String lastRosterClassId;
        private String lastEditClassId;
        private String lastQuery;

        @Override
        public void onOpenRoster(String classGroupId) {
            rosterCount++;
            lastRosterClassId = classGroupId;
        }

        @Override
        public void onEditClass(String classGroupId) {
            editCount++;
            lastEditClassId = classGroupId;
        }

        @Override
        public void onSearchQueryChanged(String query) {
            searchCount++;
            lastQuery = query;
        }
    }
}
