package com.example.leitorgabaritoomr.presentation.result;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;

import java.io.Serializable;

/**
 * Apresenta o resultado final de uma leitura OMR.
 *
 * Esta Activity recebe somente o objeto de domínio transportável.
 * Ela não conhece câmera, OpenCV, pipeline, medições ou regras de
 * interpretação.
 */
public final class OmrReadingResultActivity
        extends AppCompatActivity {

    public static final String EXTRA_READING_RESULT =
            "com.example.leitorgabaritoomr.extra.OMR_READING_RESULT";

    /**
     * Resultado devolvido quando o usuário deseja iniciar uma nova
     * leitura em vez de encerrar o fluxo atual.
     */
    public static final int RESULT_READ_AGAIN =
            Activity.RESULT_FIRST_USER + 401;

    private OmrReadingResultViewBinder viewBinder;
    private OmrReadingResult readingResult;

    /**
     * Único ponto de criação do Intent desta tela.
     */
    public static Intent createIntent(
            Context context,
            OmrReadingResult readingResult
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        if (readingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da leitura é obrigatório."
            );
        }

        return new Intent(
                context,
                OmrReadingResultActivity.class
        ).putExtra(
                EXTRA_READING_RESULT,
                readingResult
        );
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_omr_reading_result
        );

        readingResult = extractReadingResult(
                getIntent()
        );

        if (readingResult == null) {
            handleMissingReadingResult();
            return;
        }

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder =
                new OmrReadingResultViewBinder(
                        rootView
                );

        viewBinder.render(
                OmrReadingResultViewState.from(
                        readingResult
                )
        );

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
    }

    private void handleMissingReadingResult() {
        Toast.makeText(
                this,
                R.string.omr_result_error_missing_result,
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

        if (readingResult != null) {
            resultIntent.putExtra(
                    EXTRA_READING_RESULT,
                    readingResult
            );
        }

        return resultIntent;
    }

    @SuppressWarnings("deprecation")
    private OmrReadingResult extractReadingResult(
            Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        Serializable value =
                intent.getSerializableExtra(
                        EXTRA_READING_RESULT
                );

        if (!(value instanceof OmrReadingResult)) {
            return null;
        }

        return (OmrReadingResult) value;
    }

    @Override
    protected void onDestroy() {
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        readingResult = null;

        super.onDestroy();
    }
}
