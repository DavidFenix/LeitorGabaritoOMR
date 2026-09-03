package com.example.leitorgabaritoomr.presentation.history;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;
import com.example.leitorgabaritoomr.infrastructure.history.OmrSQLiteGradingHistoryRepository;
import com.example.leitorgabaritoomr.presentation.grading.OmrGradingResultActivity;

import java.io.Serializable;
import java.util.List;

/**
 * Exibe as correcoes anteriormente confirmadas de um aluno.
 *
 * A Activity apenas coordena Intent, repositorio e apresentacao. Ela nao
 * acessa camera ou OpenCV e nao executa novamente nenhuma correcao.
 */
public final class OmrStudentHistoryActivity
        extends AppCompatActivity
        implements OmrStudentHistoryViewBinder.Listener {

    public static final String EXTRA_STUDENT_IDENTITY =
            "com.example.leitorgabaritoomr.extra."
                    + "STUDENT_HISTORY_STUDENT_IDENTITY";

    private OmrStudentIdentity studentIdentity;

    private OmrSQLiteGradingHistoryRepository
            historyRepository;

    private OmrStudentHistoryViewBinder viewBinder;

    /**
     * Unico ponto de criacao do Intent desta tela.
     */
    public static Intent createIntent(
            Context context,
            OmrStudentIdentity studentIdentity
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto e obrigatorio."
            );
        }

        if (studentIdentity == null) {
            throw new IllegalArgumentException(
                    "O aluno e obrigatorio."
            );
        }

        return new Intent(
                context,
                OmrStudentHistoryActivity.class
        ).putExtra(
                EXTRA_STUDENT_IDENTITY,
                studentIdentity
        );
    }

    /**
     * Recupera com seguranca o aluno recebido pela tela.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrStudentIdentity extractStudentIdentity(
            @Nullable Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        Serializable value =
                intent.getSerializableExtra(
                        EXTRA_STUDENT_IDENTITY
                );

        if (!(value instanceof OmrStudentIdentity)) {
            return null;
        }

        return (OmrStudentIdentity) value;
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        studentIdentity = extractStudentIdentity(
                getIntent()
        );

        if (studentIdentity == null) {
            handleMissingStudent();
            return;
        }

        setContentView(
                R.layout.activity_omr_student_history
        );

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder = new OmrStudentHistoryViewBinder(
                rootView,
                this
        );

        historyRepository =
                new OmrSQLiteGradingHistoryRepository(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (studentIdentity == null
                || historyRepository == null
                || viewBinder == null) {
            return;
        }

        loadAndRenderHistory();
    }

    private void loadAndRenderHistory() {
        try {
            List<OmrGradingHistoryRecord> records =
                    historyRepository.loadByStudentId(
                            studentIdentity.getStudentId()
                    );

            OmrStudentHistoryViewState viewState =
                    OmrStudentHistoryViewState.from(
                            studentIdentity,
                            records
                    );

            viewBinder.bind(viewState);

        } catch (RuntimeException exception) {
            handleHistoryLoadFailure();
        }
    }

    private void handleMissingStudent() {
        Toast.makeText(
                this,
                R.string.omr_student_history_error_missing_student,
                Toast.LENGTH_LONG
        ).show();

        finish();
    }

    private void handleHistoryLoadFailure() {
        Toast.makeText(
                this,
                R.string.omr_student_history_error_load,
                Toast.LENGTH_LONG
        ).show();

        finish();
    }

    @Override
    public void onBackRequested() {
        finish();
    }

    @Override
    public void onHistoryDetailsRequested(
            OmrStudentHistoryViewState.HistoryItem item
    ) {
        if (item == null) {
            return;
        }

        startActivity(
                OmrGradingResultActivity.createReadOnlyIntent(
                        this,
                        item.getGradingResult()
                )
        );
    }

    @Override
    protected void onDestroy() {
        viewBinder = null;

        if (historyRepository != null) {
            historyRepository.close();
            historyRepository = null;
        }

        studentIdentity = null;

        super.onDestroy();
    }
}
