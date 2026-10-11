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
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class OmrClassRosterViewBinderInstrumentedTest {

    private View rootView;
    private OmrClassRosterViewBinder binder;
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
                    R.layout.activity_omr_class_roster,
                    parent,
                    false
            );
            binder = new OmrClassRosterViewBinder(rootView);
            actions = new RecordingActions();
            binder.setOnEnrollmentActionListener(actions);
            binder.setOnSearchQueryChangedListener(actions);
            binder.setOnBackClickListener(
                    view -> actions.backCount++
            );
            binder.setOnEnrollClickListener(
                    view -> actions.enrollCount++
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
            expectIllegalArgument(() -> new OmrClassRosterViewBinder(null));
            expectIllegalArgument(() -> binder.render(null));
        });
    }

    @Test
    public void emptyRosterShowsMetadataEmptyStateAndCount() {
        runOnMain(() -> {
            binder.render(state(Collections.emptyList(), ""));

            assertText(R.id.textOmrClassRosterTitle, "9º ano A");
            assertText(
                    R.id.textOmrClassRosterMetadata,
                    "Ensino Fundamental • 2026"
            );
            assertText(
                    R.id.textOmrClassRosterCount,
                    "0 alunos matriculados"
            );
            assertVisibility(
                    R.id.containerOmrClassRosterEmpty, View.VISIBLE
            );
            assertVisibility(R.id.listOmrClassRoster, View.GONE);
        });
    }

    @Test
    public void populatedRosterRendersCompleteRows() {
        runOnMain(() -> {
            binder.render(state(enrollments(), ""));

            assertText(
                    R.id.textOmrClassRosterCount,
                    "2 alunos matriculados"
            );
            assertEquals(2, listView().getAdapter().getCount());
            View firstRow = rowAt(0);
            assertRowText(
                    firstRow,
                    R.id.textOmrClassEnrollmentPosition,
                    "1"
            );
            assertRowText(
                    firstRow,
                    R.id.textOmrClassEnrollmentName,
                    "Ana Beatriz"
            );
            assertRowText(
                    firstRow,
                    R.id.textOmrClassEnrollmentRegistration,
                    "Matrícula: 000001"
            );
        });
    }

    @Test
    public void singularRosterCountUsesSingularResource() {
        runOnMain(() -> {
            binder.render(state(
                    Collections.singletonList(enrollments().get(0)),
                    ""
            ));
            assertText(
                    R.id.textOmrClassRosterCount,
                    "1 aluno matriculado"
            );
        });
    }

    @Test
    public void filteredStateShowsVisibleAndTotalCounts() {
        runOnMain(() -> {
            binder.render(state(enrollments(), "Bruno"));
            assertText(
                    R.id.textOmrClassRosterCount,
                    "1 de 2 alunos encontrados"
            );
            assertEquals(1, listView().getAdapter().getCount());
        });
    }

    @Test
    public void unmatchedFilterShowsNoResults() {
        runOnMain(() -> {
            binder.render(state(enrollments(), "inexistente"));
            assertVisibility(
                    R.id.containerOmrClassRosterEmpty, View.GONE
            );
            assertVisibility(
                    R.id.containerOmrClassRosterNoResults, View.VISIBLE
            );
            assertVisibility(R.id.listOmrClassRoster, View.GONE);
        });
    }

    @Test
    public void searchTextDispatchesExactQuery() {
        runOnMain(() -> {
            searchEditText().setText("000001");
            assertEquals(1, actions.searchCount);
            assertEquals("000001", actions.lastQuery);
            assertEquals("000001", binder.getSearchQuery());
        });
    }

    @Test
    public void footerButtonsDispatchActions() {
        runOnMain(() -> {
            button(R.id.buttonOmrClassRosterBack).performClick();
            button(R.id.buttonOmrClassRosterEnroll).performClick();
            assertEquals(1, actions.backCount);
            assertEquals(1, actions.enrollCount);
        });
    }

    @Test
    public void rowEditDispatchesExactStudentId() {
        runOnMain(() -> {
            binder.render(state(enrollments(), ""));
            Button edit = rowAt(1).findViewById(
                    R.id.buttonOmrClassEnrollmentEdit
            );
            edit.performClick();
            assertEquals(1, actions.editCount);
            assertEquals("student-2", actions.lastStudentId);
        });
    }

    @Test
    public void busyStateBlocksActionsAndRestoresContent() {
        runOnMain(() -> {
            binder.render(state(enrollments(), ""));
            binder.setBusy(true);

            assertVisibility(R.id.progressOmrClassRoster, View.VISIBLE);
            assertVisibility(R.id.listOmrClassRoster, View.GONE);
            assertFalse(searchEditText().isEnabled());
            assertFalse(button(
                    R.id.buttonOmrClassRosterEnroll
            ).isEnabled());

            binder.setBusy(false);
            assertVisibility(R.id.progressOmrClassRoster, View.GONE);
            assertVisibility(R.id.listOmrClassRoster, View.VISIBLE);
            assertTrue(searchEditText().isEnabled());
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();
            expectIllegalState(() -> binder.render(
                    state(enrollments(), "")
            ));
            expectIllegalState(() -> binder.setBusy(false));
            expectIllegalState(binder::getSearchQuery);
        });
    }

    private OmrClassRosterViewState state(
            List<OmrClassEnrollment> enrollments,
            String query
    ) {
        return OmrClassRosterViewState.from(
                classGroup(), enrollments, students(), query
        );
    }

    private OmrClassGroup classGroup() {
        return new OmrClassGroup(
                "class-9a", "9º ano A", "Ensino Fundamental", 2026
        );
    }

    private List<OmrClassEnrollment> enrollments() {
        return Arrays.asList(
                new OmrClassEnrollment("class-9a", "student-1", 1),
                new OmrClassEnrollment("class-9a", "student-2", 2)
        );
    }

    private List<OmrStudentProfile> students() {
        return Arrays.asList(
                new OmrStudentProfile(
                        "student-1", "000001", "Ana Beatriz"
                ),
                new OmrStudentProfile(
                        "student-2", "000002", "Bruno Lima"
                )
        );
    }

    private View rowAt(int position) {
        return listView().getAdapter().getView(
                position, null, listView()
        );
    }

    private ListView listView() {
        return rootView.findViewById(R.id.listOmrClassRoster);
    }

    private TextInputEditText searchEditText() {
        return rootView.findViewById(R.id.editOmrClassRosterSearch);
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
            implements OmrClassRosterViewBinder.OnEnrollmentActionListener,
            OmrClassRosterViewBinder.OnSearchQueryChangedListener {
        private int backCount;
        private int enrollCount;
        private int editCount;
        private int searchCount;
        private String lastStudentId;
        private String lastQuery;

        @Override
        public void onEditEnrollment(String studentId) {
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
