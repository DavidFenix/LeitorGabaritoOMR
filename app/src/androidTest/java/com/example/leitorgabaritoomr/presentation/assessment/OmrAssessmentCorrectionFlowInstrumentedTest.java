package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionContext;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.presentation.capture.OmrCaptureActivity;
import com.example.leitorgabaritoomr.presentation.grading.OmrGradingResultActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class OmrAssessmentCorrectionFlowInstrumentedTest {

    @Test
    public void captureIntentRequiresContextAndValidIsolatedStorage() {
        Context androidContext = ApplicationProvider.getApplicationContext();
        OmrAssessmentCorrectionContext correctionContext = context(3);

        expectIllegalArgument(() ->
                OmrCaptureActivity.createAssessmentIntent(
                        null,
                        correctionContext
                )
        );
        expectIllegalArgument(() ->
                OmrCaptureActivity.createAssessmentIntent(
                        androidContext,
                        null
                )
        );
        expectIllegalArgument(() ->
                OmrCaptureActivity.createAssessmentIntent(
                        androidContext,
                        correctionContext,
                        " "
                )
        );
    }

    @Test
    public void captureIntentCarriesOnlyValidatedCorrectionContext() {
        Context androidContext = ApplicationProvider.getApplicationContext();
        OmrAssessmentCorrectionContext correctionContext = context(3);

        Intent intent = OmrCaptureActivity.createAssessmentIntent(
                androidContext,
                correctionContext,
                " school-test.db "
        );

        assertSame(
                correctionContext,
                OmrCaptureActivity.extractAssessmentCorrectionContext(intent)
        );
        assertFalse(intent.hasExtra(OmrCaptureActivity.EXTRA_ANSWER_KEY));
        assertFalse(intent.hasExtra(
                OmrCaptureActivity.EXTRA_STUDENT_IDENTITY
        ));
        assertNull(OmrCaptureActivity.extractAssessmentCorrectionContext(null));

        Intent invalid = new Intent().putExtra(
                OmrCaptureActivity.EXTRA_ASSESSMENT_CORRECTION_CONTEXT,
                "valor-incorreto"
        );
        assertNull(OmrCaptureActivity.extractAssessmentCorrectionContext(
                invalid
        ));
    }

    @Test
    public void gradingIntentRequiresCorrectionContext() {
        Context androidContext = ApplicationProvider.getApplicationContext();

        expectIllegalArgument(() -> OmrGradingResultActivity.createIntent(
                androidContext,
                gradingResult(3),
                null
        ));
    }

    @Test
    public void gradingIntentCarriesExactCorrectionContext() {
        Context androidContext = ApplicationProvider.getApplicationContext();
        OmrAssessmentCorrectionContext correctionContext = context(3);

        Intent intent = OmrGradingResultActivity.createIntent(
                androidContext,
                gradingResult(3),
                correctionContext
        );

        assertSame(
                correctionContext,
                OmrGradingResultActivity
                        .extractAssessmentCorrectionContext(intent)
        );
        assertNull(OmrGradingResultActivity
                .extractAssessmentCorrectionContext(null));
    }

    @Test
    public void matchingResultRendersFrozenContextAndReturnsOk() {
        Context androidContext = ApplicationProvider.getApplicationContext();
        OmrGradingResult original = gradingResult(3);
        Intent intent = OmrGradingResultActivity.createIntent(
                androidContext,
                original,
                context(3)
        );

        try (ActivityScenario<OmrGradingResultActivity> scenario =
                     ActivityScenario.launchActivityForResult(intent)) {
            scenario.onActivity(activity -> {
                TextView contextView = activity.findViewById(
                        R.id.textOmrGradingAssessmentContext
                );
                Button finish = activity.findViewById(
                        R.id.buttonOmrGradingFinish
                );

                assertNotNull(contextView);
                assertNotNull(finish);
                assertEquals(View.VISIBLE, contextView.getVisibility());
                assertEquals(
                        activity.getString(
                                R.string.omr_grading_assessment_context,
                                1,
                                "Ana congelada",
                                "MAT-001",
                                "Avaliação bimestral",
                                2
                        ),
                        contextView.getText().toString()
                );
                assertEquals(
                        activity.getString(
                                R.string.omr_grading_action_save_assessment
                        ),
                        finish.getText().toString()
                );
                assertTrue(finish.performClick());
            });

            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_OK, result.getResultCode());
            assertEquals(
                    original,
                    OmrGradingResultActivity.extractGradingResult(
                            result.getResultData()
                    )
            );
        }
    }

    @Test
    public void mismatchedAnswerKeyVersionFinishesCanceled() {
        Context androidContext = ApplicationProvider.getApplicationContext();
        Intent intent = OmrGradingResultActivity.createIntent(
                androidContext,
                gradingResult(3),
                context(4)
        );

        try (ActivityScenario<OmrGradingResultActivity> scenario =
                     ActivityScenario.launchActivityForResult(intent)) {
            Instrumentation.ActivityResult result = scenario.getResult();
            assertEquals(Activity.RESULT_CANCELED, result.getResultCode());
        }
    }

    @Test
    public void legacyResultKeepsContextHiddenAndConcludeAction() {
        Context androidContext = ApplicationProvider.getApplicationContext();
        Intent intent = OmrGradingResultActivity.createIntent(
                androidContext,
                gradingResult(3)
        );

        try (ActivityScenario<OmrGradingResultActivity> scenario =
                     ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                TextView contextView = activity.findViewById(
                        R.id.textOmrGradingAssessmentContext
                );
                Button finish = activity.findViewById(
                        R.id.buttonOmrGradingFinish
                );

                assertEquals(View.GONE, contextView.getVisibility());
                assertEquals(
                        activity.getString(R.string.omr_grading_action_finish),
                        finish.getText().toString()
                );
            });
        }
    }

    private static OmrAssessmentCorrectionContext context(int keyVersion) {
        OmrAnswerKeyDefinition answerKey = answerKey(keyVersion);
        return new OmrAssessmentCorrectionContext(
                new OmrAssessmentApplication(
                        "application-1",
                        "assessment-1",
                        2,
                        "class-1",
                        1_000L
                ),
                new OmrAssessmentDefinition(
                        "assessment-1",
                        2,
                        "Avaliação bimestral",
                        "Matemática",
                        "9º ano",
                        "2º bimestre",
                        2026,
                        "key-1",
                        keyVersion
                ),
                new OmrAssessmentParticipant(
                        "application-1",
                        "student-1",
                        "MAT-001",
                        "Ana congelada",
                        "9º A congelada",
                        1
                ),
                answerKey
        );
    }

    private static OmrGradingResult gradingResult(int keyVersion) {
        OmrQuestionResult question = new OmrQuestionResult(
                1,
                "question-001",
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        new OmrQuestionResult.Option(
                                "question-001-option-01",
                                "A"
                        )
                ),
                0.99
        );
        OmrReadingResult reading = new OmrReadingResult(
                "reading-1",
                2_000L,
                "omr-flex-ad-q001-r001",
                3,
                "Cartão flexível",
                Collections.singletonList(question)
        );
        return new OmrReadingGrader().grade(
                reading,
                answerKey(keyVersion)
        );
    }

    private static OmrAnswerKeyDefinition answerKey(int version) {
        return new OmrAnswerKeyDefinition(
                "key-1",
                version,
                "Gabarito exato",
                "omr-flex-ad-q001-r001",
                3,
                Collections.singletonList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "question-001",
                                "question-001-option-01",
                                1.0
                        )
                )
        );
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
