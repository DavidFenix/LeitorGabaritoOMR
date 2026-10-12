package com.example.leitorgabaritoomr.presentation.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.leitorgabaritoomr.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class
        OmrAssessmentAnswerKeyOptionLayoutInstrumentedTest {

    private static final String COMPLETE_LABEL =
            "Gabarito com nome extenso para avaliação\n"
                    + "ID: gabarito-identificador-longo-etapa-16-"
                    + "abcdef123456\n"
                    + "Versão 7 • 45 questões";

    @Test
    public void optionLayoutDoesNotEllipsizeOrLimitWrappedLines() {
        runOnMain(() -> {
            TextView option = inflateOption(themedContext());

            assertNull(option.getEllipsize());
            assertEquals(Integer.MAX_VALUE, option.getMaxLines());
            assertTrue(option.getMinimumHeight() > 0);
        });
    }

    @Test
    public void longIdentityRemainsCompleteInPortraitWidth() {
        runOnMain(() -> {
            Context context = themedContext();
            TextView option = inflateOption(context);
            option.setText(COMPLETE_LABEL);

            int width = Math.round(
                    300f * context.getResources()
                            .getDisplayMetrics().density
            );
            option.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            width,
                            View.MeasureSpec.EXACTLY
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                            0,
                            View.MeasureSpec.UNSPECIFIED
                    )
            );
            option.layout(
                    0,
                    0,
                    option.getMeasuredWidth(),
                    option.getMeasuredHeight()
            );

            assertEquals(COMPLETE_LABEL, option.getText().toString());
            assertTrue(option.getLineCount() >= 3);
            assertEquals(
                    COMPLETE_LABEL.length(),
                    option.getLayout().getLineEnd(
                            option.getLineCount() - 1
                    )
            );
        });
    }

    @Test
    public void customLayoutWorksForSelectedAndDialogRows() {
        runOnMain(() -> {
            Context context = themedContext();
            FrameLayout parent = new FrameLayout(context);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context,
                    R.layout.item_omr_assessment_answer_key_option,
                    R.id.textOmrAssessmentAnswerKeyOption,
                    Collections.singletonList(COMPLETE_LABEL)
            );
            adapter.setDropDownViewResource(
                    R.layout.item_omr_assessment_answer_key_option
            );

            TextView selected = (TextView) adapter.getView(
                    0,
                    null,
                    parent
            );
            TextView dialogRow = (TextView) adapter.getDropDownView(
                    0,
                    null,
                    parent
            );

            assertEquals(COMPLETE_LABEL, selected.getText().toString());
            assertEquals(COMPLETE_LABEL, dialogRow.getText().toString());
            assertNull(selected.getEllipsize());
            assertNull(dialogRow.getEllipsize());
        });
    }

    private static Context themedContext() {
        return new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(),
                R.style.Theme_LeitorGabaritoOMR
        );
    }

    private static TextView inflateOption(Context context) {
        FrameLayout parent = new FrameLayout(context);
        return (TextView) LayoutInflater.from(context).inflate(
                R.layout.item_omr_assessment_answer_key_option,
                parent,
                false
        );
    }

    private static void runOnMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(action);
    }
}
