package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.infrastructure.history.OmrSQLiteGradingHistoryRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentResultActivityInstrumentedTest {

    private Context context;
    private String databaseName;
    private String historyDatabaseName;
    private OmrSQLiteAssessmentApplicationRepository applications;
    private OmrSQLiteAssessmentRepository assessments;
    private OmrSQLiteClassGroupRepository classes;
    private OmrSQLiteAssessmentParticipantRepository participants;
    private OmrSQLiteAssessmentAttemptRepository attempts;
    private OmrSQLiteGradingHistoryRepository histories;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        String suffix = UUID.randomUUID().toString();
        databaseName = "omr_result_activity_" + suffix + ".db";
        historyDatabaseName = "omr_result_history_" + suffix + ".db";
        context.deleteDatabase(databaseName);
        context.deleteDatabase(historyDatabaseName);
        applications = new OmrSQLiteAssessmentApplicationRepository(
                context, databaseName
        );
        assessments = new OmrSQLiteAssessmentRepository(
                context, databaseName
        );
        classes = new OmrSQLiteClassGroupRepository(
                context, databaseName
        );
        participants = new OmrSQLiteAssessmentParticipantRepository(
                context, databaseName
        );
        attempts = new OmrSQLiteAssessmentAttemptRepository(
                context, databaseName
        );
        histories = new OmrSQLiteGradingHistoryRepository(
                context, historyDatabaseName
        );
        seedReport();
    }

    @After
    public void tearDown() {
        if (histories != null) {
            histories.close();
        }
        if (attempts != null) {
            attempts.close();
        }
        if (participants != null) {
            participants.close();
        }
        if (assessments != null) {
            assessments.close();
        }
        if (classes != null) {
            classes.close();
        }
        if (applications != null) {
            applications.close();
        }
        context.deleteDatabase(databaseName);
        context.deleteDatabase(historyDatabaseName);
    }

    @Test
    public void intentFactoriesValidateAndExtractIdentity() {
        expectIllegalArgument(() ->
                OmrAssessmentResultActivity.createIntent(
                        null, "application-1"
                ));
        expectIllegalArgument(() ->
                OmrAssessmentResultActivity.createIntent(context, " "));
        expectIllegalArgument(() ->
                OmrAssessmentResultActivity.createIsolatedStorageIntent(
                        context, "application-1", " "
                ));
        expectIllegalArgument(() ->
                OmrAssessmentResultActivity.createIsolatedStorageIntent(
                        context, "application-1", databaseName, " "
                ));
        Intent intent = OmrAssessmentResultActivity.createIntent(
                context, " application-1 "
        );
        assertNotNull(intent);
        assertEquals("application-1",
                OmrAssessmentResultActivity.extractApplicationId(intent));
        assertTrue(OmrAssessmentResultActivity
                .extractApplicationId(null) == null);
    }

    @Test
    public void loadsFrozenRosterAndSummary() {
        try (ActivityScenario<OmrAssessmentResultActivity> scenario =
                     ActivityScenario.launch(createIntent())) {
            awaitListCount(scenario, 2);
            scenario.onActivity(activity -> {
                assertText(activity,
                        R.id.textOmrAssessmentResultTitle,
                        "Avaliação bimestral");
                assertText(activity,
                        R.id.textOmrAssessmentResultSummary,
                        "0 de 2 corrigidos • 1 pendente • 1 inconsistência");
                assertEquals(2, list(activity).getAdapter().getCount());
            });
        }
    }

    @Test
    public void missingHistoryIsVisibleInsteadOfSilentlyPending() {
        try (ActivityScenario<OmrAssessmentResultActivity> scenario =
                     ActivityScenario.launch(createIntent())) {
            awaitListCount(scenario, 2);
            scenario.onActivity(activity -> {
                View second = list(activity).getAdapter().getView(
                        1, null, list(activity)
                );
                TextView status = second.findViewById(
                        R.id.textOmrAssessmentResultItemStatus
                );
                assertEquals(
                        "Inconsistência: histórico da revisão 1 indisponível",
                        status.getText().toString()
                );
            });
        }
    }

    @Test
    public void exportActionsAndSearchAreAvailableAfterLoad() {
        try (ActivityScenario<OmrAssessmentResultActivity> scenario =
                     ActivityScenario.launch(createIntent())) {
            awaitListCount(scenario, 2);
            scenario.onActivity(activity -> {
                assertTrue(button(activity,
                        R.id.buttonOmrAssessmentResultCsv).isEnabled());
                assertTrue(button(activity,
                        R.id.buttonOmrAssessmentResultXlsx).isEnabled());
                assertTrue(button(activity,
                        R.id.buttonOmrAssessmentResultPdf).isEnabled());
                TextView search = activity.findViewById(
                        R.id.editOmrAssessmentResultSearch
                );
                search.setText("MAT-001");
                assertEquals(1, list(activity).getAdapter().getCount());
            });
        }
    }

    private void seedReport() {
        assertTrue(assessments.save(new OmrAssessmentDefinition(
                "assessment-1", 2, "Avaliação bimestral", "Matemática",
                "9º ano", "2º bimestre", 2026, "key-1", 3
        )));
        classes.save(new OmrClassGroup(
                "class-1", "9º A", "9º ano", 2026
        ));
        assertTrue(applications.save(new OmrAssessmentApplication(
                "application-1", "assessment-1", 2,
                "class-1", 1_000L
        )));
        assertTrue(participants.saveRoster(
                "application-1",
                Arrays.asList(
                        new OmrAssessmentParticipant(
                                "application-1", "student-1", "MAT-001",
                                "Ana", "9º A", 1
                        ),
                        new OmrAssessmentParticipant(
                                "application-1", "student-2", "MAT-002",
                                "Bruno", "9º A", 2
                        )
                )
        ));
        assertTrue(attempts.save(new OmrAssessmentAttempt(
                "attempt-1", "application-1", "student-2",
                1, "history-missing", 2_000L
        )));
    }

    private Intent createIntent() {
        return OmrAssessmentResultActivity.createIsolatedStorageIntent(
                context,
                "application-1",
                databaseName,
                historyDatabaseName
        );
    }

    private static void awaitListCount(
            ActivityScenario<OmrAssessmentResultActivity> scenario,
            int expected
    ) {
        long deadline = SystemClock.uptimeMillis() + 5_000L;
        AtomicInteger count = new AtomicInteger(-1);
        do {
            scenario.onActivity(activity -> count.set(
                    list(activity).getAdapter().getCount()
            ));
            if (count.get() == expected) {
                return;
            }
            SystemClock.sleep(40L);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals(expected, count.get());
    }

    private static ListView list(OmrAssessmentResultActivity activity) {
        return activity.findViewById(R.id.listOmrAssessmentResults);
    }

    private static Button button(
            OmrAssessmentResultActivity activity,
            int id
    ) {
        return activity.findViewById(id);
    }

    private static void assertText(
            OmrAssessmentResultActivity activity,
            int id,
            String expected
    ) {
        TextView view = activity.findViewById(id);
        assertEquals(expected, view.getText().toString());
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}
