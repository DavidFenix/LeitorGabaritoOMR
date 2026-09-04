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

                        assertSelectionSummary(
                                activity,
                                10
                        );

                        assertEquals(
                                "cartao-resposta-010-itens-v1.svg",
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
    public void everyCountFromOneToTenUpdatesSummaryAndFileName() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            for (int questionCount = 1;
                 questionCount <= 10;
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
                                    selectedCount
                            );

                            assertEquals(
                                    String.format(
                                            "cartao-resposta-%03d-itens-v1.svg",
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
    public void selectedCountSurvivesActivityRecreation() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionCount,
                            Spinner.class
                    ).setSelection(6)
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
                                6,
                                spinner.getSelectedItemPosition()
                        );

                        assertSelectionSummary(
                                activity,
                                7
                        );

                        assertEquals(
                                "cartao-resposta-007-itens-v1.svg",
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
            int questionCount
    ) {
        assertEquals(
                activity.getResources().getQuantityString(
                        R.plurals
                                .omr_sheet_export_selected_summary,
                        questionCount,
                        questionCount
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
