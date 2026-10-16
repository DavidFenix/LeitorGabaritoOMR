package com.example.leitorgabaritoomr.presentation.grading;

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

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class OmrAnswerKeyListViewBinderInstrumentedTest {

    private View rootView;
    private OmrAnswerKeyListViewBinder binder;
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
                    R.layout.activity_omr_answer_key_list,
                    parent,
                    false
            );

            binder = new OmrAnswerKeyListViewBinder(rootView);
            actions = new RecordingActions();

            binder.setOnAnswerKeyActionListener(actions);
            binder.setOnBackClickListener(
                    view -> actions.backCount++
            );
            binder.setOnCreateClickListener(
                    view -> actions.createCount++
            );
            binder.setOnImportCsvClickListener(
                    view -> actions.importCount++
            );
            binder.setOnExportCsvClickListener(
                    view -> actions.exportCount++
            );
            binder.setOnCsvTemplateClickListener(
                    view -> actions.templateCount++
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
    public void emptyCatalogKeepsImportAndTemplateButDisablesExport() {
        runOnMain(() -> {
            binder.render(OmrAnswerKeyListViewState.from(
                    Collections.emptyList(),
                    null
            ));

            assertTrue(button(
                    R.id.buttonOmrAnswerKeyListImportCsv
            ).isEnabled());

            assertTrue(button(
                    R.id.buttonOmrAnswerKeyListCsvTemplate
            ).isEnabled());

            assertFalse(button(
                    R.id.buttonOmrAnswerKeyListExportCsv
            ).isEnabled());
        });
    }

    @Test
    public void populatedCatalogEnablesExport() {
        runOnMain(() -> {
            OmrAnswerKeyDefinition answerKey = createAnswerKey();

            binder.render(OmrAnswerKeyListViewState.from(
                    Collections.singletonList(answerKey),
                    answerKey
            ));

            assertTrue(button(
                    R.id.buttonOmrAnswerKeyListExportCsv
            ).isEnabled());
        });
    }

    @Test
    public void footerButtonsDispatchTheirExactActions() {
        runOnMain(() -> {
            binder.render(OmrAnswerKeyListViewState.from(
                    Collections.singletonList(createAnswerKey()),
                    null
            ));

            button(R.id.buttonOmrAnswerKeyListBack).performClick();
            button(R.id.buttonOmrAnswerKeyListCreate).performClick();
            button(R.id.buttonOmrAnswerKeyListImportCsv).performClick();
            button(R.id.buttonOmrAnswerKeyListExportCsv).performClick();
            button(R.id.buttonOmrAnswerKeyListCsvTemplate).performClick();

            assertEquals(1, actions.backCount);
            assertEquals(1, actions.createCount);
            assertEquals(1, actions.importCount);
            assertEquals(1, actions.exportCount);
            assertEquals(1, actions.templateCount);
        });
    }

    @Test
    public void busyStateBlocksAllActionsAndRestoresContent() {
        runOnMain(() -> {
            binder.render(OmrAnswerKeyListViewState.from(
                    Collections.singletonList(createAnswerKey()),
                    null
            ));

            binder.setBusy(true);

            assertVisibility(
                    R.id.progressOmrAnswerKeyList,
                    View.VISIBLE
            );
            assertVisibility(
                    R.id.listOmrAnswerKeys,
                    View.GONE
            );
            assertFalse(button(
                    R.id.buttonOmrAnswerKeyListBack
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAnswerKeyListCreate
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAnswerKeyListImportCsv
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAnswerKeyListExportCsv
            ).isEnabled());
            assertFalse(button(
                    R.id.buttonOmrAnswerKeyListCsvTemplate
            ).isEnabled());

            binder.setBusy(false);

            assertVisibility(
                    R.id.progressOmrAnswerKeyList,
                    View.GONE
            );
            assertVisibility(
                    R.id.listOmrAnswerKeys,
                    View.VISIBLE
            );
            assertTrue(button(
                    R.id.buttonOmrAnswerKeyListExportCsv
            ).isEnabled());
        });
    }

    @Test
    public void releaseIsIdempotentAndRejectsFurtherUse() {
        runOnMain(() -> {
            binder.release();
            binder.release();

            expectIllegalState(() -> binder.setBusy(false));
            expectIllegalState(() -> binder.render(
                    OmrAnswerKeyListViewState.from(
                            Collections.emptyList(),
                            null
                    )
            ));
        });
    }

    private Button button(int viewId) {
        return rootView.findViewById(viewId);
    }

    private void assertVisibility(int viewId, int expected) {
        assertEquals(
                expected,
                rootView.findViewById(viewId).getVisibility()
        );
    }

    private static OmrAnswerKeyDefinition createAnswerKey() {
        return new OmrAnswerKeyDefinition(
                "binder-csv-key",
                1,
                "Gabarito CSV",
                "layout-test",
                1,
                Arrays.asList(
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q01",
                                "Q01-A",
                                1.0
                        ),
                        OmrAnswerKeyEntry.singleAnswer(
                                "Q02",
                                "Q02-B",
                                1.0
                        )
                )
        );
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(action);
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
            implements OmrAnswerKeyListViewBinder
            .OnAnswerKeyActionListener {

        private int backCount;
        private int createCount;
        private int importCount;
        private int exportCount;
        private int templateCount;

        @Override
        public void onSelectAnswerKey(
                String answerKeyId,
                int answerKeyVersion
        ) {
        }

        @Override
        public void onDeleteAnswerKey(
                String answerKeyId,
                int answerKeyVersion,
                String answerKeyName,
                boolean active
        ) {
        }
    }
}
