package com.example.leitorgabaritoomr.presentation.grading;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;

import java.io.Serializable;

/**
 * Apresenta o resultado final da correção de uma leitura OMR.
 *
 * Recebe somente o resultado de domínio já calculado. Não acessa
 * câmera, OpenCV, medições nem executa novamente a correção.
 */
public final class OmrGradingResultActivity
        extends AppCompatActivity {

    public static final String EXTRA_GRADING_RESULT =
            "com.example.leitorgabaritoomr.extra.OMR_GRADING_RESULT";

    /**
     * Resultado devolvido quando o usuário deseja corrigir outra
     * leitura usando o mesmo fluxo.
     */
    public static final int RESULT_READ_AGAIN =
            Activity.RESULT_FIRST_USER + 402;

    private OmrGradingResult gradingResult;
    private OmrGradingResultViewBinder viewBinder;

    /**
     * Único ponto de criação do Intent desta tela.
     */
    public static Intent createIntent(
            Context context,
            OmrGradingResult gradingResult
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        if (gradingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da correção é obrigatório."
            );
        }

        return new Intent(
                context,
                OmrGradingResultActivity.class
        ).putExtra(
                EXTRA_GRADING_RESULT,
                gradingResult
        );
    }

    /**
     * Extrai com segurança o resultado devolvido por esta Activity.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrGradingResult extractGradingResult(
            @Nullable Intent resultData
    ) {
        if (resultData == null) {
            return null;
        }

        Serializable value =
                resultData.getSerializableExtra(
                        EXTRA_GRADING_RESULT
                );

        if (!(value instanceof OmrGradingResult)) {
            return null;
        }

        return (OmrGradingResult) value;
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_omr_grading_result
        );

        gradingResult = extractGradingResult(
                getIntent()
        );

        if (gradingResult == null) {
            handleMissingGradingResult();
            return;
        }

        OmrLayoutDefinition layoutDefinition =
                AvalieCeDevelopmentLayoutFactory.create();

        OmrGradingResultViewState viewState;

        try {
            viewState = OmrGradingResultViewState.from(
                    gradingResult,
                    layoutDefinition
            );

        } catch (IllegalArgumentException exception) {
            handleMissingGradingResult();
            return;
        }

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder = new OmrGradingResultViewBinder(
                rootView
        );

        viewBinder.render(viewState);

        viewBinder.setOnReadAgainClickListener(
                view -> returnResultAndFinish(
                        RESULT_READ_AGAIN
                )
        );

        viewBinder.setOnFinishClickListener(
                view -> returnResultAndFinish(
                        Activity.RESULT_OK
                )
        );

        setResult(Activity.RESULT_CANCELED);
    }

    private void handleMissingGradingResult() {
        Toast.makeText(
                this,
                R.string.omr_grading_error_missing_result,
                Toast.LENGTH_LONG
        ).show();

        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void returnResultAndFinish(
            int resultCode
    ) {
        setResult(
                resultCode,
                createResultIntent()
        );

        finish();
    }

    private Intent createResultIntent() {
        Intent resultIntent = new Intent();

        if (gradingResult != null) {
            resultIntent.putExtra(
                    EXTRA_GRADING_RESULT,
                    gradingResult
            );
        }

        return resultIntent;
    }

    @Override
    protected void onDestroy() {
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        gradingResult = null;

        super.onDestroy();
    }
}
