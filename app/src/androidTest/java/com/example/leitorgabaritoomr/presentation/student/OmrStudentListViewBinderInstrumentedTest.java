package com.example.leitorgabaritoomr.presentation.student;

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
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class OmrStudentListViewBinderInstrumentedTest {

    private View rootView;
    private OmrStudentListViewBinder binder;
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
                    R.layout.activity_omr_student_list,
                    parent,
                    false
            );
            binder = new OmrStudentListViewBinder(rootView);
            actions = new RecordingActions();
            binder.setOnStudentActionListener(actions);
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
            expectIllegalArgument(() ->
                    new OmrStudentListViewBinder(null)
            );
            expectIllegalArgument(() -> binder.render(null));
        });
    }

    @Test
    public void emptyCatalogShowsEmptyStateAndCount() {
        runOnMain(() -> {
            binder.render(state(Collections.emptyList(), ""));

            assertText(
                    R.id.textOmrStudentListCount,
                    "0 alunos cadastrados"
            );
            assertVisibility(
                    R.id.containerOmrStudentListEmpty,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrStudents, View.GONE);
            assertVisibility(
                    R.id.containerOmrStudentListNoResults,
                    View.GONE
            );
        });
    }

    @Test
    public void populatedCatalogRendersRowsAndCompleteData() {
        runOnMain(() -> {
            binder.render(state(students(), ""));

            assertText(
                    R.id.textOmrStudentListCount,
                    "2 alunos cadastrados"
            );
            assertEquals(2, listView().getAdapter().getCount());
            assertVisibility(R.id.listOmrStudents, View.VISIBLE);

            View firstRow = rowAt(0);
            assertRowText(
                    firstRow,
                    R.id.textOmrStudentItemName,
                    "Ana Beatriz"
            );
            assertRowText(
                    firstRow,
                    R.id.textOmrStudentItemRegistration,
                    "Matrícula: 000123"
            );
        });
    }

    @Test
    public void filteredStateShowsVisibleAndTotalCounts() {
        runOnMain(() -> {
            binder.render(state(students(), "bruno"));

            assertText(
                    R.id.textOmrStudentListCount,
                    "1 de 2 alunos encontrados"
            );
            assertEquals(1, listView().getAdapter().getCount());
            assertVisibility(R.id.listOmrStudents, View.VISIBLE);
        });
    }

    @Test
    public void unmatchedFilterShowsNoResultsInsteadOfEmptyCatalog() {
        runOnMain(() -> {
            binder.render(state(students(), "inexistente"));

            assertVisibility(
                    R.id.containerOmrStudentListEmpty,
                    View.GONE
            );
            assertVisibility(
                    R.id.containerOmrStudentListNoResults,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrStudents, View.GONE);
        });
    }

    @Test
    public void searchTextDispatchesExactQuery() {
        runOnMain(() -> {
            searchEditText().setText("000123");

            assertEquals(1, actions.searchCount);
            assertEquals("000123", actions.lastQuery);
            assertEquals("000123", binder.getSearchQuery());
        });
    }

    @Test
    public void footerButtonsDispatchTheirActions() {
        runOnMain(() -> {
            button(R.id.buttonOmrStudentListBack).performClick();
            button(R.id.buttonOmrStudentListCreate).performClick();
            button(R.id.buttonOmrStudentListImportCsv).performClick();

            assertEquals(1, actions.backCount);
            assertEquals(1, actions.createCount);
            assertEquals(1, actions.importCount);
        });
    }

    @Test
    public void rowEditDispatchesExactStudentId() {
        runOnMain(() -> {
            binder.render(state(students(), ""));

            Button edit = rowAt(1).findViewById(
                    R.id.buttonOmrStudentItemEdit
            );
            edit.performClick();

            assertEquals(1, actions.editCount);
            assertEquals("student-2", actions.lastStudentId);
        });
    }

    @Test
    public void busyStateBlocksMutationsAndRestoresContent() {
        runOnMain(() -> {
            binder.render(state(students(), ""));
            binder.setBusy(true);

            assertVisibility(
                    R.id.progressOmrStudentList,
                    View.VISIBLE
            );
            assertVisibility(R.id.listOmrStudents, View.GONE);
            assertFalse(searchEditText().isEnabled());
            assertFalse(button(
                    R.id.buttonOmrStudentListCreate
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrStudentListImportCsv
            ).isEnabled());

            binder.setBusy(false);

            assertVisibility(
                    R.id.progressOmrStudentList,
                    View.GONE
            );
            assertVisibility(R.id.listOmrStudents, View.VISIBLE);
            assertTrue(searchEditText().isEnabled());
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();

            expectIllegalState(() ->
                    binder.render(state(students(), ""))
            );
            expectIllegalState(() -> binder.setBusy(false));
            expectIllegalState(binder::getSearchQuery);
        });
    }

    private OmrStudentListViewState state(
            java.util.List<OmrStudentProfile> students,
            String query
    ) {
        return OmrStudentListViewState.from(students, query);
    }

    private java.util.List<OmrStudentProfile> students() {
        return Arrays.asList(
                new OmrStudentProfile(
                        "student-1",
                        "000123",
                        "Ana Beatriz"
                ),
                new OmrStudentProfile(
                        "student-2",
                        "000456",
                        "Bruno Lima"
                )
        );
    }

    private View rowAt(int position) {
        return listView().getAdapter().getView(
                position,
                null,
                listView()
        );
    }

    private ListView listView() {
        return rootView.findViewById(R.id.listOmrStudents);
    }

    private TextInputEditText searchEditText() {
        return rootView.findViewById(R.id.editOmrStudentSearch);
    }

    private Button button(int viewId) {
        return rootView.findViewById(viewId);
    }

    private void assertText(int viewId, String expected) {
        TextView textView = rootView.findViewById(viewId);
        assertEquals(expected, textView.getText().toString());
    }

    private void assertRowText(
            View row,
            int viewId,
            String expected
    ) {
        TextView textView = row.findViewById(viewId);
        assertEquals(expected, textView.getText().toString());
    }

    private void assertVisibility(int viewId, int expected) {
        View view = rootView.findViewById(viewId);
        assertEquals(expected, view.getVisibility());
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(action);
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
            implements OmrStudentListViewBinder.OnStudentActionListener,
            OmrStudentListViewBinder.OnSearchQueryChangedListener {

        private int backCount;
        private int createCount;
        private int importCount;
        private int editCount;
        private int searchCount;
        private String lastStudentId;
        private String lastQuery;

        @Override
        public void onEditStudent(String studentId) {
            editCount++;
            lastStudentId = studentId;
        }

        @Override
        public void onSearchQueryChanged(String query) {
            searchCount++;
            lastQuery = query;
        }
    }
}
