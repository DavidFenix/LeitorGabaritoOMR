package com.example.leitorgabaritoomr.presentation.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testa o contrato Android do binder do historico.
 *
 * Nao abre Activity, banco, camera ou OpenCV. O layout e inflado em memoria
 * e todas as operacoes de View sao executadas na thread principal.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrStudentHistoryViewBinderInstrumentedTest {

    private View rootView;
    private RecordingListener listener;
    private OmrStudentHistoryViewBinder binder;

    @Before
    public void setUp() {
        runOnMain(() -> {
            Context context =
                    ApplicationProvider.getApplicationContext();

            FrameLayout parent = new FrameLayout(context);

            rootView = LayoutInflater
                    .from(context)
                    .inflate(
                            R.layout.activity_omr_student_history,
                            parent,
                            false
                    );

            listener = new RecordingListener();

            SimpleDateFormat dateFormat =
                    new SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            new Locale("pt", "BR")
                    );

            dateFormat.setTimeZone(
                    TimeZone.getTimeZone("UTC")
            );

            binder = new OmrStudentHistoryViewBinder(
                    rootView,
                    listener,
                    dateFormat
            );
        });
    }

    @After
    public void tearDown() {
        runOnMain(() -> {
            rootView = null;
            listener = null;
            binder = null;
        });
    }

    @Test
    public void rejectsMissingDependencies() {
        runOnMain(() -> {
            expectIllegalArgument(() ->
                    new OmrStudentHistoryViewBinder(
                            null,
                            listener
                    )
            );

            expectIllegalArgument(() ->
                    new OmrStudentHistoryViewBinder(
                            rootView,
                            null
                    )
            );

            expectIllegalArgument(() ->
                    new OmrStudentHistoryViewBinder(
                            rootView,
                            listener,
                            null
                    )
            );

            expectIllegalArgument(() -> binder.bind(null));
        });
    }

    @Test
    public void emptyStateRendersStudentAndZeroCounts() {
        runOnMain(() -> {
            binder.bind(emptyState());

            assertText(
                    R.id.textOmrStudentHistoryName,
                    "Ana Beatriz"
            );

            assertText(
                    R.id.textOmrStudentHistoryIdentity,
                    "Matrícula: 000123 • Turma: 9 A"
            );

            assertText(
                    R.id.textOmrStudentHistorySummary,
                    "0 resultados registrados"
            );

            assertText(
                    R.id.textOmrStudentHistoryFinalCount,
                    "Concluídos\n0"
            );

            assertText(
                    R.id.textOmrStudentHistoryReviewCount,
                    "Revisar\n0"
            );

            assertText(
                    R.id.textOmrStudentHistoryPendingCount,
                    "Pendentes\n0"
            );

            assertEquals(
                    View.GONE,
                    historyList().getVisibility()
            );

            assertEquals(
                    View.VISIBLE,
                    view(R.id.containerOmrStudentHistoryEmpty)
                            .getVisibility()
            );

            assertEquals(
                    0,
                    historyList().getAdapter().getCount()
            );
        });
    }

    @Test
    public void populatedStateRendersHeaderCountersAndList() {
        runOnMain(() -> {
            OmrStudentHistoryViewState state =
                    threeStatusState();

            binder.bind(state);

            assertText(
                    R.id.textOmrStudentHistorySummary,
                    "3 resultados registrados"
            );

            assertText(
                    R.id.textOmrStudentHistoryFinalCount,
                    "Concluídos\n1"
            );

            assertText(
                    R.id.textOmrStudentHistoryReviewCount,
                    "Revisar\n1"
            );

            assertText(
                    R.id.textOmrStudentHistoryPendingCount,
                    "Pendentes\n1"
            );

            assertEquals(
                    View.VISIBLE,
                    historyList().getVisibility()
            );

            assertEquals(
                    View.GONE,
                    view(R.id.containerOmrStudentHistoryEmpty)
                            .getVisibility()
            );

            assertEquals(
                    3,
                    historyList().getAdapter().getCount()
            );
        });
    }

    @Test
    public void rowRendersAnswerKeyDatePercentageAndPoints() {
        runOnMain(() -> {
            OmrStudentIdentity student = student();

            OmrStudentHistoryViewState state =
                    OmrStudentHistoryViewState.from(
                            student,
                            Collections.singletonList(
                                    halfScoreRecord(student)
                            )
                    );

            binder.bind(state);

            View row = rowAt(0, null);

            assertRowText(
                    row,
                    R.id.textOmrStudentHistoryAnswerKey,
                    "Gabarito: Avaliacao de Matematica • versão 4"
            );

            assertRowText(
                    row,
                    R.id.textOmrStudentHistoryStoredAt,
                    "Registrada em 15/01/2027 08:00"
            );

            assertRowText(
                    row,
                    R.id.textOmrStudentHistoryPercentage,
                    "50,00%"
            );

            assertRowText(
                    row,
                    R.id.textOmrStudentHistoryPoints,
                    "1 de 2 pontos"
            );
        });
    }

    @Test
    public void rowsRenderFinalReviewAndPendingPalettes() {
        runOnMain(() -> {
            binder.bind(threeStatusState());

            assertStatus(
                    rowAt(0, null),
                    "CONCLUÍDO",
                    0xFF16A34A,
                    0xFFDCFCE7,
                    0xFF166534
            );

            assertStatus(
                    rowAt(1, null),
                    "REVISAR",
                    0xFFD97706,
                    0xFFFEF3C7,
                    0xFF92400E
            );

            assertStatus(
                    rowAt(2, null),
                    "PENDENTE",
                    0xFFDC2626,
                    0xFFFEE2E2,
                    0xFF991B1B
            );
        });
    }

    @Test
    public void backButtonDispatchesCallback() {
        runOnMain(() -> {
            assertEquals(0, listener.backRequestCount);

            button(R.id.buttonOmrStudentHistoryBack)
                    .performClick();

            assertEquals(1, listener.backRequestCount);
        });
    }

    @Test
    public void detailsButtonDispatchesExactHistoryItem() {
        runOnMain(() -> {
            OmrStudentHistoryViewState state =
                    threeStatusState();

            binder.bind(state);

            View row = rowAt(1, null);

            Button detailsButton = row.findViewById(
                    R.id.buttonOmrStudentHistoryDetails
            );

            detailsButton.performClick();

            assertEquals(1, listener.detailsRequestCount);
            assertSame(
                    state.getHistoryItems().get(1),
                    listener.lastDetailsItem
            );
        });
    }

    @Test
    public void rebindingEmptyStateRemovesPreviousRows() {
        runOnMain(() -> {
            binder.bind(threeStatusState());

            assertEquals(
                    3,
                    historyList().getAdapter().getCount()
            );

            binder.bind(emptyState());

            assertEquals(
                    0,
                    historyList().getAdapter().getCount()
            );

            assertEquals(
                    View.GONE,
                    historyList().getVisibility()
            );

            assertEquals(
                    View.VISIBLE,
                    view(R.id.containerOmrStudentHistoryEmpty)
                            .getVisibility()
            );

            assertText(
                    R.id.textOmrStudentHistorySummary,
                    "0 resultados registrados"
            );
        });
    }

    @Test
    public void recycledRowRebindsPaletteTextAndCallback() {
        runOnMain(() -> {
            OmrStudentIdentity student = student();

            OmrStudentHistoryViewState finalState =
                    stateWithOneRecord(
                            record(
                                    "history-final-recycled",
                                    "reading-final-recycled",
                                    student,
                                    3_000L,
                                    OmrQuestionResult.Status.SINGLE_MARK
                            )
                    );

            binder.bind(finalState);

            View recycledRow = rowAt(0, null);

            assertStatus(
                    recycledRow,
                    "CONCLUÍDO",
                    0xFF16A34A,
                    0xFFDCFCE7,
                    0xFF166534
            );

            OmrStudentHistoryViewState reviewState =
                    stateWithOneRecord(
                            record(
                                    "history-review-recycled",
                                    "reading-review-recycled",
                                    student,
                                    4_000L,
                                    OmrQuestionResult.Status.AMBIGUOUS
                            )
                    );

            binder.bind(reviewState);

            View reboundRow = rowAt(0, recycledRow);

            assertSame(recycledRow, reboundRow);

            assertStatus(
                    reboundRow,
                    "REVISAR",
                    0xFFD97706,
                    0xFFFEF3C7,
                    0xFF92400E
            );

            Button detailsButton = reboundRow.findViewById(
                    R.id.buttonOmrStudentHistoryDetails
            );

            detailsButton.performClick();

            assertSame(
                    reviewState.getHistoryItems().get(0),
                    listener.lastDetailsItem
            );

            assertEquals(1, listener.detailsRequestCount);
        });
    }

    private OmrStudentHistoryViewState emptyState() {
        return OmrStudentHistoryViewState.from(
                student(),
                Collections.emptyList()
        );
    }

    private OmrStudentHistoryViewState stateWithOneRecord(
            OmrGradingHistoryRecord record
    ) {
        return OmrStudentHistoryViewState.from(
                student(),
                Collections.singletonList(record)
        );
    }

    private OmrStudentHistoryViewState threeStatusState() {
        OmrStudentIdentity student = student();

        return OmrStudentHistoryViewState.from(
                student,
                Arrays.asList(
                        record(
                                "history-final",
                                "reading-final",
                                student,
                                3_000L,
                                OmrQuestionResult.Status.SINGLE_MARK
                        ),
                        record(
                                "history-review",
                                "reading-review",
                                student,
                                2_000L,
                                OmrQuestionResult.Status.AMBIGUOUS
                        ),
                        record(
                                "history-pending",
                                "reading-pending",
                                student,
                                1_000L,
                                OmrQuestionResult.Status.NOT_READY
                        )
                )
        );
    }

    private static OmrStudentIdentity student() {
        return new OmrStudentIdentity(
                "manual:000123",
                "000123",
                "Ana Beatriz",
                "9 A"
        );
    }

    private static OmrGradingHistoryRecord record(
            String historyRecordId,
            String readingId,
            OmrStudentIdentity student,
            long storedAtEpochMillis,
            OmrQuestionResult.Status status
    ) {
        OmrQuestionResult question = question(
                1,
                "Q01",
                status,
                "A"
        );

        OmrReadingResult reading = new OmrReadingResult(
                readingId,
                storedAtEpochMillis - 100L,
                "layout-history",
                1,
                "Layout do historico",
                Collections.singletonList(question)
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-history",
                        4,
                        "Avaliacao de Matematica",
                        "layout-history",
                        1,
                        Collections.singletonList(
                                OmrAnswerKeyEntry.singleAnswer(
                                        "Q01",
                                        "A",
                                        1.0
                                )
                        )
                );

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        reading,
                        answerKey
                );

        return new OmrGradingHistoryRecord(
                historyRecordId,
                storedAtEpochMillis,
                student,
                gradingResult
        );
    }

    private static OmrGradingHistoryRecord halfScoreRecord(
            OmrStudentIdentity student
    ) {
        long storedAtEpochMillis = 1_800_000_000_000L;

        OmrQuestionResult firstQuestion = question(
                1,
                "Q01",
                OmrQuestionResult.Status.SINGLE_MARK,
                "A"
        );

        OmrQuestionResult secondQuestion = question(
                2,
                "Q02",
                OmrQuestionResult.Status.SINGLE_MARK,
                "B"
        );

        OmrReadingResult reading = new OmrReadingResult(
                "reading-half-score",
                storedAtEpochMillis - 100L,
                "layout-history",
                1,
                "Layout do historico",
                Arrays.asList(
                        firstQuestion,
                        secondQuestion
                )
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-history",
                        4,
                        "Avaliacao de Matematica",
                        "layout-history",
                        1,
                        Arrays.asList(
                                OmrAnswerKeyEntry.singleAnswer(
                                        "Q01",
                                        "A",
                                        1.0
                                ),
                                OmrAnswerKeyEntry.singleAnswer(
                                        "Q02",
                                        "A",
                                        1.0
                                )
                        )
                );

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        reading,
                        answerKey
                );

        return new OmrGradingHistoryRecord(
                "history-half-score",
                storedAtEpochMillis,
                student,
                gradingResult
        );
    }

    private static OmrQuestionResult question(
            int position,
            String questionId,
            OmrQuestionResult.Status status,
            String markedOptionId
    ) {
        if (status == OmrQuestionResult.Status.NOT_READY) {
            return new OmrQuestionResult(
                    position,
                    questionId,
                    status,
                    Collections.emptyList(),
                    0.0
            );
        }

        double confidence =
                status == OmrQuestionResult.Status.AMBIGUOUS
                        ? 0.52
                        : 0.97;

        return new OmrQuestionResult(
                position,
                questionId,
                status,
                Collections.singletonList(
                        new OmrQuestionResult.Option(
                                markedOptionId,
                                markedOptionId
                        )
                ),
                confidence
        );
    }

    private View rowAt(
            int position,
            View convertView
    ) {
        ListAdapter adapter = historyList().getAdapter();

        return adapter.getView(
                position,
                convertView,
                historyList()
        );
    }

    private ListView historyList() {
        return rootView.findViewById(
                R.id.listOmrStudentHistory
        );
    }

    private Button button(int viewId) {
        return rootView.findViewById(viewId);
    }

    private View view(int viewId) {
        return rootView.findViewById(viewId);
    }

    private void assertText(
            int textViewId,
            String expectedText
    ) {
        TextView textView = rootView.findViewById(
                textViewId
        );

        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }

    private static void assertRowText(
            View row,
            int textViewId,
            String expectedText
    ) {
        TextView textView = row.findViewById(textViewId);

        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }

    private static void assertStatus(
            View row,
            String expectedStatus,
            int expectedIndicatorColor,
            int expectedBackgroundColor,
            int expectedTextColor
    ) {
        View indicator = row.findViewById(
                R.id.viewOmrStudentHistoryStatusIndicator
        );

        TextView statusView = row.findViewById(
                R.id.textOmrStudentHistoryStatus
        );

        TextView percentageView = row.findViewById(
                R.id.textOmrStudentHistoryPercentage
        );

        assertEquals(
                expectedStatus,
                statusView.getText().toString()
        );

        assertEquals(
                expectedStatus,
                indicator.getContentDescription().toString()
        );

        assertEquals(
                expectedIndicatorColor,
                backgroundColor(indicator)
        );

        assertEquals(
                expectedBackgroundColor,
                backgroundColor(statusView)
        );

        assertEquals(
                expectedTextColor,
                statusView.getCurrentTextColor()
        );

        assertEquals(
                expectedTextColor,
                percentageView.getCurrentTextColor()
        );
    }

    private static int backgroundColor(View view) {
        assertTrue(
                view.getBackground() instanceof ColorDrawable
        );

        return ((ColorDrawable) view.getBackground())
                .getColor();
    }

    private static void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private static void runOnMain(Runnable action) {
        AtomicReference<Throwable> failure =
                new AtomicReference<>();

        InstrumentationRegistry
                .getInstrumentation()
                .runOnMainSync(() -> {
                    try {
                        action.run();
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    }
                });

        Throwable throwable = failure.get();

        if (throwable == null) {
            return;
        }

        if (throwable instanceof AssertionError) {
            throw (AssertionError) throwable;
        }

        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }

        throw new RuntimeException(throwable);
    }

    private static final class RecordingListener
            implements OmrStudentHistoryViewBinder.Listener {

        private int backRequestCount;
        private int detailsRequestCount;

        private OmrStudentHistoryViewState.HistoryItem
                lastDetailsItem;

        @Override
        public void onBackRequested() {
            backRequestCount++;
        }

        @Override
        public void onHistoryDetailsRequested(
                OmrStudentHistoryViewState.HistoryItem item
        ) {
            detailsRequestCount++;
            lastDetailsItem = item;
        }
    }
}
