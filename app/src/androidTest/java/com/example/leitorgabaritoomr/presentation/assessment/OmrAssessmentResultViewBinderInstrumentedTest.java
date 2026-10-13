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
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentResultViewBinderInstrumentedTest {

    private View root;
    private OmrAssessmentResultViewBinder binder;
    private int csvClicks;
    private int xlsxClicks;
    private int pdfClicks;
    private int backClicks;
    private String query;

    @Before
    public void setUp() {
        runOnMain(() -> {
            Context context = new ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.Theme_LeitorGabaritoOMR
            );
            FrameLayout parent = new FrameLayout(context);
            root = LayoutInflater.from(context).inflate(
                    R.layout.activity_omr_assessment_result,
                    parent,
                    false
            );
            binder = new OmrAssessmentResultViewBinder(root);
            binder.setOnCsvClickListener(view -> csvClicks++);
            binder.setOnXlsxClickListener(view -> xlsxClicks++);
            binder.setOnPdfClickListener(view -> pdfClicks++);
            binder.setOnBackClickListener(view -> backClicks++);
            binder.setOnSearchQueryChangedListener(value -> query = value);
        });
    }

    @After
    public void tearDown() {
        runOnMain(() -> {
            if (binder != null) {
                binder.release();
            }
            binder = null;
            root = null;
        });
    }

    @Test
    public void rejectsMissingRootOrState() {
        runOnMain(() -> {
            try {
                new OmrAssessmentResultViewBinder(null);
                fail("Era esperada IllegalArgumentException.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().length() > 0);
            }
            try {
                binder.render(null);
                fail("Era esperada IllegalArgumentException.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().length() > 0);
            }
        });
    }

    @Test
    public void rendersSummaryAndFrozenIdentity() {
        runOnMain(() -> {
            binder.render(state(""));
            assertText(R.id.textOmrAssessmentResultTitle, "Avaliação");
            assertText(
                    R.id.textOmrAssessmentResultAssessment,
                    "Matemática • prova v1 • 2026"
            );
            assertText(
                    R.id.textOmrAssessmentResultClass,
                    "Turma: 9º A • aplicação application-1"
            );
            assertText(
                    R.id.textOmrAssessmentResultSummary,
                    "0 de 2 corrigidos • 1 pendente • 1 inconsistência"
            );
            String average = ((TextView) root.findViewById(
                    R.id.textOmrAssessmentResultAverage
            )).getText().toString();
            assertTrue(average.startsWith("Média dos corrigidos: 0"));
            assertTrue(average.endsWith("%"));
        });
    }

    @Test
    public void rendersPendingAndMissingHistoryRows() {
        runOnMain(() -> {
            binder.render(state(""));
            assertEquals(2, list().getAdapter().getCount());
            View pending = rowAt(0);
            assertRowText(pending,
                    R.id.textOmrAssessmentResultItemName, "Ana");
            assertRowText(pending,
                    R.id.textOmrAssessmentResultItemStatus,
                    "Pendente de correção");
            View missing = rowAt(1);
            assertRowText(missing,
                    R.id.textOmrAssessmentResultItemStatus,
                    "Inconsistência: histórico da revisão 1 indisponível");
        });
    }

    @Test
    public void filteredStateShowsCountAndEmptyState() {
        runOnMain(() -> {
            binder.render(state("MAT-001"));
            assertText(
                    R.id.textOmrAssessmentResultCount,
                    "1 de 2 participantes encontrados"
            );
            binder.render(state("ninguém"));
            assertEquals(View.VISIBLE, root.findViewById(
                    R.id.containerOmrAssessmentResultNoResults
            ).getVisibility());
        });
    }

    @Test
    public void searchDispatchesExactQuery() {
        runOnMain(() -> {
            ((TextView) root.findViewById(
                    R.id.editOmrAssessmentResultSearch
            )).setText("Ana");
            assertEquals("Ana", query);
            assertEquals("Ana", binder.getSearchQuery());
        });
    }

    @Test
    public void exportAndBackButtonsDispatchActions() {
        runOnMain(() -> {
            binder.render(state(""));
            button(R.id.buttonOmrAssessmentResultCsv).performClick();
            button(R.id.buttonOmrAssessmentResultXlsx).performClick();
            button(R.id.buttonOmrAssessmentResultPdf).performClick();
            button(R.id.buttonOmrAssessmentResultBack).performClick();
            assertEquals(1, csvClicks);
            assertEquals(1, xlsxClicks);
            assertEquals(1, pdfClicks);
            assertEquals(1, backClicks);
        });
    }

    @Test
    public void busyStateDisablesActionsAndShowsProgress() {
        runOnMain(() -> {
            binder.render(state(""));
            binder.setBusy(true);
            assertFalse(button(R.id.buttonOmrAssessmentResultCsv).isEnabled());
            assertFalse(button(R.id.buttonOmrAssessmentResultXlsx).isEnabled());
            assertFalse(button(R.id.buttonOmrAssessmentResultPdf).isEnabled());
            assertEquals(View.VISIBLE, root.findViewById(
                    R.id.progressOmrAssessmentResult
            ).getVisibility());
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();
            try {
                binder.render(state(""));
                fail("Era esperada IllegalStateException.");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().length() > 0);
            }
        });
    }

    private OmrAssessmentResultViewState state(String query) {
        OmrAssessmentApplication application = new OmrAssessmentApplication(
                "application-1", "assessment-1", 1,
                "class-1", 1_000L
        );
        OmrAssessmentDefinition assessment = new OmrAssessmentDefinition(
                "assessment-1", 1, "Avaliação", "Matemática",
                "9º ano", "1º bimestre", 2026, "key-1", 1
        );
        OmrAssessmentParticipant ana = new OmrAssessmentParticipant(
                "application-1", "student-1", "MAT-001",
                "Ana", "9º A", 1
        );
        OmrAssessmentParticipant bruno = new OmrAssessmentParticipant(
                "application-1", "student-2", "MAT-002",
                "Bruno", "9º A", 2
        );
        OmrAssessmentAttempt missing = new OmrAssessmentAttempt(
                "attempt-1", "application-1", "student-2",
                1, "history-missing", 2_000L
        );
        OmrAssessmentResultReport report = new OmrAssessmentResultReport(
                application,
                assessment,
                "9º A",
                3_000L,
                Arrays.asList(
                        new OmrAssessmentResultReport.Row(ana, null, null),
                        new OmrAssessmentResultReport.Row(
                                bruno, missing, null
                        )
                )
        );
        return OmrAssessmentResultViewState.from(report, query);
    }

    private ListView list() {
        return root.findViewById(R.id.listOmrAssessmentResults);
    }

    private View rowAt(int position) {
        return list().getAdapter().getView(position, null, list());
    }

    private Button button(int id) {
        return root.findViewById(id);
    }

    private void assertText(int id, String expected) {
        assertEquals(expected, ((TextView) root.findViewById(id))
                .getText().toString());
    }

    private static void assertRowText(
            View row,
            int id,
            String expected
    ) {
        assertEquals(expected, ((TextView) row.findViewById(id))
                .getText().toString());
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(action);
    }
}
