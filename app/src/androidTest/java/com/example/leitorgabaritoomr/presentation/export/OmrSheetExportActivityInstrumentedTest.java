package com.example.leitorgabaritoomr.presentation.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Protege o contrato visual da tela de exportacao sem abrir o seletor externo
 * de documentos. O salvamento real permanece coberto pelo teste manual com o
 * Storage Access Framework e pelos testes puros do gerador SVG.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrSheetExportActivityInstrumentedTest {

    @Test
    public void initialStateShowsTenQuestionModelReadyToSave() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> {
                        Spinner spinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionCount,
                                Spinner.class
                        );

                        assertEquals(
                                9,
                                spinner.getSelectedItemPosition()
                        );

                        assertEquals(
                                90,
                                spinner.getCount()
                        );

                        Spinner optionSpinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetOptionCount,
                                Spinner.class
                        );

                        assertEquals(
                                0,
                                optionSpinner.getSelectedItemPosition()
                        );
                        assertEquals(2, optionSpinner.getCount());

                        assertSelectionSummary(
                                activity,
                                10,
                                4
                        );

                        assertEquals(
                                "cartao-resposta-010-itens-4-alternativas-v2.svg",
                                textOf(
                                        activity,
                                        R.id.textOmrSheetExportFileName
                                )
                        );

                        Button saveButton = requireView(
                                activity,
                                R.id.buttonOmrSheetExportSave,
                                Button.class
                        );

                        ProgressBar progressBar = requireView(
                                activity,
                                R.id.progressOmrSheetExport,
                                ProgressBar.class
                        );

                        assertTrue(saveButton.isEnabled());
                        assertEquals(
                                View.GONE,
                                progressBar.getVisibility()
                        );
                    }
            );
        }
    }

    @Test
    public void everyCountFromOneToNinetyUpdatesSummaryAndFileName() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            for (int questionCount = 1;
                 questionCount <= 90;
                 questionCount++) {

                int selectedCount = questionCount;
                int selectionIndex = questionCount - 1;

                scenario.onActivity(
                        activity -> requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionCount,
                                Spinner.class
                        ).setSelection(selectionIndex)
                );

                InstrumentationRegistry
                        .getInstrumentation()
                        .waitForIdleSync();

                scenario.onActivity(
                        activity -> {
                            Spinner spinner = requireView(
                                    activity,
                                    R.id.spinnerOmrSheetQuestionCount,
                                    Spinner.class
                            );

                            assertEquals(
                                    selectionIndex,
                                    spinner.getSelectedItemPosition()
                            );

                            assertSelectionSummary(
                                    activity,
                                    selectedCount,
                                    4
                            );

                            assertEquals(
                                    String.format(
                                            "cartao-resposta-%03d-itens-4-alternativas-v2.svg",
                                            selectedCount
                                    ),
                                    textOf(
                                            activity,
                                            R.id.textOmrSheetExportFileName
                                    )
                            );
                        }
                );
            }
        }
    }

    @Test
    public void selectedCountsSurviveActivityRecreation() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> {
                        requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionCount,
                                Spinner.class
                        ).setSelection(89);

                        requireView(
                                activity,
                                R.id.spinnerOmrSheetOptionCount,
                                Spinner.class
                        ).setSelection(1);
                    }
            );

            InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync();

            scenario.recreate();

            scenario.onActivity(
                    activity -> {
                        Spinner spinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionCount,
                                Spinner.class
                        );

                        assertEquals(
                                89,
                                spinner.getSelectedItemPosition()
                        );

                        Spinner optionSpinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetOptionCount,
                                Spinner.class
                        );

                        assertEquals(
                                1,
                                optionSpinner.getSelectedItemPosition()
                        );

                        assertSelectionSummary(
                                activity,
                                90,
                                5
                        );

                        assertEquals(
                                "cartao-resposta-090-itens-5-alternativas-v2.svg",
                                textOf(
                                        activity,
                                        R.id.textOmrSheetExportFileName
                                )
                        );
                    }
            );
        }
    }

    @Test
    public void exportUsesSelectedStandardV2ModelForEveryCount() {
        assertEquals(
                4,
                OmrSheetExportActivity
                        .createExportTemplate(10)
                        .getOptionCount()
        );

        for (int questionCount = 1;
             questionCount <= 90;
             questionCount++) {

            for (int optionCount = 4;
                 optionCount <= 5;
                 optionCount++) {

                OmrSheetTemplateSpec spec =
                        OmrSheetExportActivity
                                .createExportTemplate(
                                        questionCount,
                                        optionCount
                                );

                assertEquals(
                        String.format(
                                optionCount == 4
                                        ? "omr-standard-ad-q%03d"
                                        : "omr-standard-ae-q%03d",
                                questionCount
                        ),
                        spec.getTemplateId()
                );
                assertEquals(2, spec.getTemplateVersion());
                assertEquals(
                        questionCount,
                        spec.getQuestionCount()
                );
                assertEquals(
                        optionCount,
                        spec.getOptionCount()
                );
            }
        }
    }

    @Test
    public void backButtonFinishesWithCancelledResult() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launchActivityForResult(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.buttonOmrSheetExportBack,
                            Button.class
                    ).performClick()
            );

            Instrumentation.ActivityResult result =
                    scenario.getResult();

            assertEquals(
                    Activity.RESULT_CANCELED,
                    result.getResultCode()
            );
        }
    }

    @Test
    public void createIntentTargetsExportActivityAndRejectsNullContext() {
        Context context =
                ApplicationProvider.getApplicationContext();

        Intent intent =
                OmrSheetExportActivity.createIntent(context);

        assertNotNull(intent);
        assertNotNull(intent.getComponent());
        assertEquals(
                OmrSheetExportActivity.class.getName(),
                intent.getComponent().getClassName()
        );

        try {
            OmrSheetExportActivity.createIntent(null);
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }

    private Intent createActivityIntent() {
        Context context =
                ApplicationProvider.getApplicationContext();

        return OmrSheetExportActivity.createIntent(
                context
        );
    }

    private void assertSelectionSummary(
            Activity activity,
            int questionCount,
            int optionCount
    ) {
        assertEquals(
                activity.getResources().getQuantityString(
                        R.plurals
                                .omr_sheet_export_selected_summary,
                        questionCount,
                        questionCount,
                        optionCount
                ),
                textOf(
                        activity,
                        R.id.textOmrSheetExportSelected
                )
        );
    }

    private String textOf(
            Activity activity,
            int viewId
    ) {
        TextView textView = requireView(
                activity,
                viewId,
                TextView.class
        );

        return textView.getText().toString();
    }

    private <T extends View> T requireView(
            Activity activity,
            int viewId,
            Class<T> viewClass
    ) {
        View view = activity.findViewById(viewId);

        assertNotNull(view);
        assertTrue(viewClass.isInstance(view));

        return viewClass.cast(view);
    }
}
