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
import android.widget.EditText;
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

                        Spinner distributionSpinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionsPerColumn,
                                Spinner.class
                        );

                        assertEquals(
                                0,
                                distributionSpinner
                                        .getSelectedItemPosition()
                        );
                        assertEquals(10, distributionSpinner.getCount());

                        assertSelectionSummary(
                                activity,
                                10,
                                4,
                                true,
                                10,
                                1
                        );

                        assertEquals(
                                "cartao-resposta-010-itens-4-alternativas-010-por-coluna-v4.svg",
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
                int expectedRows = Math.min(questionCount, 18);
                int expectedColumns = (questionCount
                        + expectedRows
                        - 1)
                        / expectedRows;

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
                                    4,
                                    true,
                                    expectedRows,
                                    expectedColumns
                            );

                            assertEquals(
                                    String.format(
                                            "cartao-resposta-%03d-itens-4-alternativas-%03d-por-coluna-v4.svg",
                                            selectedCount,
                                            expectedRows
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

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionsPerColumn,
                            Spinner.class
                    ).setSelection(13)
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

                        Spinner distributionSpinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionsPerColumn,
                                Spinner.class
                        );

                        assertEquals(
                                13,
                                distributionSpinner
                                        .getSelectedItemPosition()
                        );
                        assertEquals(14, distributionSpinner.getCount());

                        assertSelectionSummary(
                                activity,
                                90,
                                5,
                                false,
                                30,
                                3
                        );

                        assertEquals(
                                "cartao-resposta-090-itens-5-alternativas-030-por-coluna-v4.svg",
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
    public void exportUsesSelectedFlexibleV4ModelForEveryCount() {
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

                int expectedRows = Math.min(
                        questionCount,
                        18
                );

                assertEquals(
                        String.format(
                                optionCount == 4
                                        ? "omr-flex-ad-q%03d-r%03d"
                                        : "omr-flex-ae-q%03d-r%03d",
                                questionCount,
                                expectedRows
                        ),
                        spec.getTemplateId()
                );
                assertEquals(4, spec.getTemplateVersion());
                assertEquals(
                        questionCount,
                        spec.getQuestionCount()
                );
                assertEquals(
                        optionCount,
                        spec.getOptionCount()
                );
                assertEquals(
                        expectedRows,
                        spec.getQuestionsPerBlock()
                );
            }
        }

        OmrSheetTemplateSpec manual =
                OmrSheetExportActivity.createExportTemplate(
                        45,
                        5,
                        15
                );

        assertEquals(
                "omr-flex-ae-q045-r015",
                manual.getTemplateId()
        );
        assertEquals(3, manual.getBlockCount());
        assertEquals(720, manual.getCanonicalWidth());
    }

    @Test
    public void manualDistributionUpdatesSummaryAndFileName() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionCount,
                            Spinner.class
                    ).setSelection(44)
            );

            InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync();

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionsPerColumn,
                            Spinner.class
                    ).setSelection(7)
            );

            InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync();

            scenario.onActivity(
                    activity -> {
                        assertSelectionSummary(
                                activity,
                                45,
                                4,
                                false,
                                15,
                                3
                        );

                        assertEquals(
                                "cartao-resposta-045-itens-4-alternativas-015-por-coluna-v4.svg",
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
    public void invalidManualDistributionReturnsToAutomaticAfterCountChange() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(
                             createActivityIntent()
                     )) {

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionCount,
                            Spinner.class
                    ).setSelection(44)
            );

            InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync();

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionsPerColumn,
                            Spinner.class
                    ).setSelection(7)
            );

            InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync();

            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionCount,
                            Spinner.class
                    ).setSelection(89)
            );

            InstrumentationRegistry
                    .getInstrumentation()
                    .waitForIdleSync();

            scenario.onActivity(
                    activity -> {
                        Spinner distributionSpinner = requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionsPerColumn,
                                Spinner.class
                        );

                        assertEquals(
                                0,
                                distributionSpinner
                                        .getSelectedItemPosition()
                        );

                        assertSelectionSummary(
                                activity,
                                90,
                                4,
                                true,
                                18,
                                5
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

    @Test
    public void defaultEditableTextsAreVisible() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                "CARTÃO-RESPOSTA",
                                textOf(
                                        activity,
                                        R.id.editOmrSheetHeaderText
                                )
                        );
                        assertEquals(
                                "Marque apenas uma alternativa",
                                textOf(
                                        activity,
                                        R.id.editOmrSheetInstructionText
                                )
                        );
                        assertEquals(
                                "Mantenha a proporção",
                                textOf(
                                        activity,
                                        R.id.editOmrSheetFooterText
                                )
                        );
                    }
            );
        }
    }

    @Test
    public void defaultTextsTrackColumnCount() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(
                    activity -> requireView(
                            activity,
                            R.id.spinnerOmrSheetQuestionCount,
                            Spinner.class
                    ).setSelection(44)
            );
            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                "Preencha completamente apenas uma alternativa por questão",
                                textOf(
                                        activity,
                                        R.id.editOmrSheetInstructionText
                                )
                        );
                        assertEquals(
                                "Mantenha a proporção original ao redimensionar",
                                textOf(
                                        activity,
                                        R.id.editOmrSheetFooterText
                                )
                        );
                    }
            );
        }
    }

    @Test
    public void customAndBlankTextsSurviveRecreation() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(
                    activity -> {
                        requireView(
                                activity,
                                R.id.editOmrSheetHeaderText,
                                EditText.class
                        ).setText("Escola Ubiratan");
                        requireView(
                                activity,
                                R.id.editOmrSheetInstructionText,
                                EditText.class
                        ).setText("");
                        requireView(
                                activity,
                                R.id.editOmrSheetFooterText,
                                EditText.class
                        ).setText("Boa prova");
                    }
            );

            scenario.recreate();

            scenario.onActivity(
                    activity -> {
                        assertEquals(
                                "Escola Ubiratan",
                                textOf(activity, R.id.editOmrSheetHeaderText)
                        );
                        assertEquals(
                                "",
                                textOf(
                                        activity,
                                        R.id.editOmrSheetInstructionText
                                )
                        );
                        assertEquals(
                                "Boa prova",
                                textOf(activity, R.id.editOmrSheetFooterText)
                        );
                    }
            );
        }
    }

    @Test
    public void blankInstructionRemainsBlankAfterCountChange() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(
                    activity -> {
                        requireView(
                                activity,
                                R.id.editOmrSheetInstructionText,
                                EditText.class
                        ).setText("");
                        requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionCount,
                                Spinner.class
                        ).setSelection(44);
                    }
            );
            scenario.onActivity(
                    activity -> assertEquals(
                            "",
                            textOf(
                                    activity,
                                    R.id.editOmrSheetInstructionText
                            )
                    )
            );
        }
    }

    @Test
    public void restoreButtonUsesDefaultsForCurrentLayout() {
        try (ActivityScenario<OmrSheetExportActivity> scenario =
                     ActivityScenario.launch(createActivityIntent())) {
            scenario.onActivity(
                    activity -> {
                        requireView(
                                activity,
                                R.id.spinnerOmrSheetQuestionCount,
                                Spinner.class
                        ).setSelection(44);
                        requireView(
                                activity,
                                R.id.editOmrSheetInstructionText,
                                EditText.class
                        ).setText("Texto temporário");
                        requireView(
                                activity,
                                R.id.buttonOmrSheetRestoreDefaultTexts,
                                Button.class
                        ).performClick();
                    }
            );
            scenario.onActivity(
                    activity -> assertEquals(
                            "Preencha completamente apenas uma alternativa por questão",
                            textOf(
                                    activity,
                                    R.id.editOmrSheetInstructionText
                            )
                    )
            );
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
            int optionCount,
            boolean automatic,
            int questionsPerColumn,
            int columnCount
    ) {
        String rowText = activity.getResources()
                .getQuantityString(
                        R.plurals
                                .omr_sheet_export_items_per_column,
                        questionsPerColumn,
                        questionsPerColumn
                );

        String distributionText = automatic
                ? activity.getString(
                R.string
                        .omr_sheet_export_distribution_automatic,
                rowText
        )
                : rowText;

        String columnText = activity.getResources()
                .getQuantityString(
                        R.plurals.omr_sheet_export_column_count,
                        columnCount,
                        columnCount
                );

        assertEquals(
                activity.getResources().getQuantityString(
                        R.plurals
                                .omr_sheet_export_selected_summary,
                        questionCount,
                        questionCount,
                        optionCount,
                        distributionText,
                        columnText
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
