package com.example.leitorgabaritoomr.presentation.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
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
import com.example.leitorgabaritoomr.infrastructure.history.OmrSQLiteGradingHistoryRepository;
import com.example.leitorgabaritoomr.presentation.grading.OmrGradingResultActivity;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Protege a integracao da tela de historico com o SQLite real.
 *
 * O banco do processo de teste e apagado antes e depois de cada caso.
 * Nenhum teste inicializa camera, OpenCV ou o pipeline de leitura.
 */
@RunWith(AndroidJUnit4.class)
public final class
OmrStudentHistoryActivityInstrumentedTest {

    private static final String HISTORY_DATABASE_NAME =
            "omr_grading_history.db";

    private Context applicationContext;

    private OmrSQLiteGradingHistoryRepository repository;

    @Before
    public void setUp() {
        applicationContext =
                ApplicationProvider.getApplicationContext();

        applicationContext.deleteDatabase(
                HISTORY_DATABASE_NAME
        );

        repository =
                new OmrSQLiteGradingHistoryRepository(
                        applicationContext
                );
    }

    @After
    public void tearDown() {
        if (repository != null) {
            repository.close();
            repository = null;
        }

        if (applicationContext != null) {
            applicationContext.deleteDatabase(
                    HISTORY_DATABASE_NAME
            );

            applicationContext = null;
        }
    }

    @Test
    public void intentContractCarriesStudentAndRejectsInvalidData() {
        OmrStudentIdentity student = targetStudent();

        Intent intent = OmrStudentHistoryActivity.createIntent(
                applicationContext,
                student
        );

        assertEquals(
                OmrStudentHistoryActivity.class.getName(),
                intent.getComponent().getClassName()
        );

        assertEquals(
                student,
                OmrStudentHistoryActivity
                        .extractStudentIdentity(intent)
        );

        assertNull(
                OmrStudentHistoryActivity
                        .extractStudentIdentity(null)
        );

        Intent wrongPayload = new Intent().putExtra(
                OmrStudentHistoryActivity.EXTRA_STUDENT_IDENTITY,
                "nao e um aluno"
        );

        assertNull(
                OmrStudentHistoryActivity
                        .extractStudentIdentity(wrongPayload)
        );

        expectIllegalArgument(() ->
                OmrStudentHistoryActivity.createIntent(
                        null,
                        student
                )
        );

        expectIllegalArgument(() ->
                OmrStudentHistoryActivity.createIntent(
                        applicationContext,
                        null
                )
        );
    }

    @Test
    public void missingStudentFinishesCanceled() {
        Intent intent = new Intent(
                applicationContext,
                OmrStudentHistoryActivity.class
        );

        try (ActivityScenario<OmrStudentHistoryActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             intent
                     )) {

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );
        }
    }

    @Test
    public void emptyRepositoryShowsStudentAndEmptyState() {
        try (ActivityScenario<OmrStudentHistoryActivity>
                     scenario = ActivityScenario.launch(
                             historyIntent(targetStudent())
                     )) {

            scenario.onActivity(activity -> {
                assertText(
                        activity,
                        R.id.textOmrStudentHistoryName,
                        "Ana Beatriz"
                );

                assertText(
                        activity,
                        R.id.textOmrStudentHistoryIdentity,
                        "Matrícula: 000123 • Turma: 9 A"
                );

                assertText(
                        activity,
                        R.id.textOmrStudentHistorySummary,
                        "0 resultados registrados"
                );

                assertVisibility(
                        activity,
                        R.id.containerOmrStudentHistoryEmpty,
                        View.VISIBLE
                );

                assertVisibility(
                        activity,
                        R.id.listOmrStudentHistory,
                        View.GONE
                );

                assertEquals(
                        0,
                        historyList(activity)
                                .getAdapter()
                                .getCount()
                );
            });
        }
    }

    @Test
    public void loadsOnlyTargetStudentNewestFirst() {
        OmrStudentIdentity target = targetStudent();
        OmrStudentIdentity other = otherStudent();

        saveRecord(
                "history-target-old",
                "reading-target-old",
                target,
                1_800_000_000_100L,
                "answer-key-target-old",
                "Prova antiga"
        );

        saveRecord(
                "history-other-newest",
                "reading-other-newest",
                other,
                1_800_000_000_400L,
                "answer-key-other-newest",
                "Prova de outro aluno"
        );

        saveRecord(
                "history-target-recent",
                "reading-target-recent",
                target,
                1_800_000_000_300L,
                "answer-key-target-recent",
                "Prova recente"
        );

        try (ActivityScenario<OmrStudentHistoryActivity>
                     scenario = ActivityScenario.launch(
                             historyIntent(target)
                     )) {

            scenario.onActivity(activity -> {
                ListAdapter adapter =
                        historyList(activity).getAdapter();

                assertNotNull(adapter);
                assertEquals(2, adapter.getCount());

                assertText(
                        activity,
                        R.id.textOmrStudentHistorySummary,
                        "2 resultados registrados"
                );

                assertRowAnswerKey(
                        historyRow(activity, 0),
                        "Gabarito: Prova recente • versão 1"
                );

                assertRowAnswerKey(
                        historyRow(activity, 1),
                        "Gabarito: Prova antiga • versão 1"
                );
            });
        }
    }

    @Test
    public void recreationReloadsPersistedHistory() {
        OmrStudentIdentity student = targetStudent();

        saveRecord(
                "history-recreation",
                "reading-recreation",
                student,
                1_800_000_000_200L,
                "answer-key-recreation",
                "Prova recriada"
        );

        try (ActivityScenario<OmrStudentHistoryActivity>
                     scenario = ActivityScenario.launch(
                             historyIntent(student)
                     )) {

            scenario.onActivity(activity ->
                    assertSingleRecreatedRecord(activity)
            );

            scenario.recreate();

            scenario.onActivity(activity ->
                    assertSingleRecreatedRecord(activity)
            );
        }
    }

    @Test
    public void backButtonFinishesActivity() {
        try (ActivityScenario<OmrStudentHistoryActivity>
                     scenario =
                     ActivityScenario.launchActivityForResult(
                             historyIntent(targetStudent())
                     )) {

            scenario.onActivity(activity -> {
                Button backButton = activity.findViewById(
                        R.id.buttonOmrStudentHistoryBack
                );

                assertNotNull(backButton);
                assertTrue(backButton.performClick());
            });

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );
        }
    }

    @Test
    public void detailsButtonOpensExactResultInReadOnlyMode() {
        OmrStudentIdentity student = targetStudent();

        OmrGradingHistoryRecord record = saveRecord(
                "history-details",
                "reading-details",
                student,
                1_800_000_000_500L,
                "answer-key-details",
                "Prova para detalhes"
        );

        Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();

        Instrumentation.ActivityMonitor monitor =
                instrumentation.addMonitor(
                        OmrGradingResultActivity.class.getName(),
                        null,
                        false
                );

        Activity openedActivity = null;

        try (ActivityScenario<OmrStudentHistoryActivity>
                     scenario = ActivityScenario.launch(
                             historyIntent(student)
                     )) {

            scenario.onActivity(activity -> {
                View row = historyRow(activity, 0);

                Button detailsButton = row.findViewById(
                        R.id.buttonOmrStudentHistoryDetails
                );

                assertNotNull(detailsButton);
                assertTrue(detailsButton.performClick());
            });

            openedActivity =
                    instrumentation.waitForMonitorWithTimeout(
                            monitor,
                            5_000L
                    );

            assertNotNull(openedActivity);
            assertTrue(
                    openedActivity
                            instanceof OmrGradingResultActivity
            );

            OmrGradingResult received =
                    OmrGradingResultActivity
                            .extractGradingResult(
                                    openedActivity.getIntent()
                            );

            assertEquals(
                    record.getGradingResult(),
                    received
            );

            assertTrue(
                    OmrGradingResultActivity
                            .isReadOnlyIntent(
                                    openedActivity.getIntent()
                            )
            );

        } finally {
            if (openedActivity != null) {
                Activity activityToFinish = openedActivity;

                instrumentation.runOnMainSync(
                        activityToFinish::finish
                );

                instrumentation.waitForIdleSync();
            }

            instrumentation.removeMonitor(monitor);
        }
    }

    private Intent historyIntent(
            OmrStudentIdentity student
    ) {
        return OmrStudentHistoryActivity.createIntent(
                applicationContext,
                student
        );
    }

    private OmrGradingHistoryRecord saveRecord(
            String historyRecordId,
            String readingId,
            OmrStudentIdentity student,
            long storedAtEpochMillis,
            String answerKeyId,
            String answerKeyName
    ) {
        OmrGradingResult gradingResult =
                createMinimalGradingResult(
                        readingId,
                        storedAtEpochMillis - 100L,
                        answerKeyId,
                        answerKeyName
                );

        OmrGradingHistoryRecord record =
                new OmrGradingHistoryRecord(
                        historyRecordId,
                        storedAtEpochMillis,
                        student,
                        gradingResult
                );

        assertTrue(repository.save(record));
        return record;
    }

    private static OmrGradingResult
    createMinimalGradingResult(
            String readingId,
            long capturedAtEpochMillis,
            String answerKeyId,
            String answerKeyName
    ) {
        OmrLayoutDefinition layout =
                AvalieCeDevelopmentLayoutFactory.create();

        OmrQuestionDefinition questionDefinition =
                layout.getAllQuestions().get(0);

        OmrOptionDefinition option =
                questionDefinition.getOptions().get(0);

        OmrAnswerKeyEntry answerKeyEntry =
                OmrAnswerKeyEntry.singleAnswer(
                        questionDefinition.getId(),
                        option.getId(),
                        1.0
                );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        answerKeyId,
                        1,
                        answerKeyName,
                        layout.getId(),
                        layout.getVersion(),
                        Collections.singletonList(
                                answerKeyEntry
                        )
                );

        OmrQuestionResult questionResult =
                new OmrQuestionResult(
                        1,
                        questionDefinition.getId(),
                        OmrQuestionResult.Status.SINGLE_MARK,
                        Collections.singletonList(
                                new OmrQuestionResult.Option(
                                        option.getId(),
                                        option.getLabel()
                                )
                        ),
                        0.97
                );

        OmrReadingResult readingResult =
                new OmrReadingResult(
                        readingId,
                        capturedAtEpochMillis,
                        layout.getId(),
                        layout.getVersion(),
                        layout.getName(),
                        Collections.singletonList(
                                questionResult
                        )
                );

        return new OmrReadingGrader().grade(
                readingResult,
                answerKey
        );
    }

    private static OmrStudentIdentity targetStudent() {
        return new OmrStudentIdentity(
                "manual:000123",
                "000123",
                "Ana Beatriz",
                "9 A"
        );
    }

    private static OmrStudentIdentity otherStudent() {
        return new OmrStudentIdentity(
                "manual:000999",
                "000999",
                "Bruno Lima",
                "9 B"
        );
    }

    private static void assertSingleRecreatedRecord(
            Activity activity
    ) {
        ListAdapter adapter =
                historyList(activity).getAdapter();

        assertNotNull(adapter);
        assertEquals(1, adapter.getCount());

        assertRowAnswerKey(
                historyRow(activity, 0),
                "Gabarito: Prova recriada • versão 1"
        );
    }

    private static ListView historyList(
            Activity activity
    ) {
        ListView listView = activity.findViewById(
                R.id.listOmrStudentHistory
        );

        assertNotNull(listView);
        return listView;
    }

    private static View historyRow(
            Activity activity,
            int position
    ) {
        ListView listView = historyList(activity);
        ListAdapter adapter = listView.getAdapter();

        assertNotNull(adapter);
        assertTrue(position >= 0);
        assertTrue(position < adapter.getCount());

        return adapter.getView(
                position,
                null,
                listView
        );
    }

    private static void assertRowAnswerKey(
            View row,
            String expectedText
    ) {
        TextView answerKeyView = row.findViewById(
                R.id.textOmrStudentHistoryAnswerKey
        );

        assertNotNull(answerKeyView);
        assertEquals(
                expectedText,
                answerKeyView.getText().toString()
        );
    }

    private static void assertText(
            Activity activity,
            int textViewId,
            String expectedText
    ) {
        TextView textView = activity.findViewById(
                textViewId
        );

        assertNotNull(textView);
        assertEquals(
                expectedText,
                textView.getText().toString()
        );
    }

    private static void assertVisibility(
            Activity activity,
            int viewId,
            int expectedVisibility
    ) {
        View view = activity.findViewById(viewId);

        assertNotNull(view);
        assertEquals(
                expectedVisibility,
                view.getVisibility()
        );
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
}
