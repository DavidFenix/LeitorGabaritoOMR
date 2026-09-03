package com.example.leitorgabaritoomr.presentation.student;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.student.OmrManualStudentIdentityFactory;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;
import com.example.leitorgabaritoomr.presentation.history.OmrStudentHistoryActivity;

import java.io.Serializable;

/**
 * Coordena a identificacao manual do aluno antes da captura OMR.
 *
 * A Activity recebe o gabarito oficial apenas para contextualizar a tela e
 * devolve uma {@link OmrStudentIdentity} ao chamador. Ela nao abre a camera,
 * nao corrige leituras e nao persiste historico.
 */
public final class OmrStudentIdentificationActivity
        extends AppCompatActivity {

    public static final String EXTRA_ANSWER_KEY =
            "com.example.leitorgabaritoomr.extra."
                    + "STUDENT_IDENTIFICATION_ANSWER_KEY";

    public static final String EXTRA_STUDENT_IDENTITY =
            "com.example.leitorgabaritoomr.extra."
                    + "STUDENT_IDENTITY";

    private static final String STATE_REGISTRATION =
            "omr.student_identification.registration";

    private static final String STATE_NAME =
            "omr.student_identification.name";

    private static final String STATE_CLASS_NAME =
            "omr.student_identification.class_name";

    private OmrAnswerKeyDefinition answerKeyDefinition;
    private OmrStudentIdentificationViewState viewState;
    private OmrStudentIdentificationViewBinder viewBinder;
    private OmrManualStudentIdentityFactory identityFactory;

    /**
     * Unico ponto de criacao do Intent desta tela.
     */
    public static Intent createIntent(
            Context context,
            OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto e obrigatorio."
            );
        }

        if (answerKeyDefinition == null) {
            throw new IllegalArgumentException(
                    "O gabarito oficial e obrigatorio."
            );
        }

        return new Intent(
                context,
                OmrStudentIdentificationActivity.class
        ).putExtra(
                EXTRA_ANSWER_KEY,
                answerKeyDefinition
        );
    }

    /**
     * Extrai com seguranca o aluno devolvido por esta Activity.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrStudentIdentity extractStudentIdentity(
            @Nullable Intent resultData
    ) {
        if (resultData == null) {
            return null;
        }

        Serializable value =
                resultData.getSerializableExtra(
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

        setResult(Activity.RESULT_CANCELED);

        answerKeyDefinition = extractAnswerKey(
                getIntent()
        );

        if (answerKeyDefinition == null) {
            finish();
            return;
        }

        setContentView(
                R.layout.activity_omr_student_identification
        );

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder =
                new OmrStudentIdentificationViewBinder(
                        rootView
                );

        identityFactory =
                new OmrManualStudentIdentityFactory();

        viewState = restoreViewState(
                savedInstanceState
        );

        configureBinder();

        viewBinder.renderAnswerKey(
                answerKeyDefinition.getName(),
                answerKeyDefinition.getVersion(),
                answerKeyDefinition.getQuestionCount()
        );

        viewBinder.render(viewState);
    }

    private void configureBinder() {
        viewBinder.setOnFormChangedListener(
                changedViewState ->
                        viewState = changedViewState
        );

        viewBinder.setOnCancelClickListener(
                view -> cancelAndFinish()
        );

        viewBinder.setOnHistoryClickListener(
                view -> openStudentHistory()
        );

        viewBinder.setOnStartClickListener(
                view -> returnStudentAndFinish()
        );
    }

    private void cancelAndFinish() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void openStudentHistory() {
        OmrStudentIdentity studentIdentity =
                createValidatedStudentIdentityOrNull();

        if (studentIdentity == null) {
            return;
        }

        startActivity(
                OmrStudentHistoryActivity.createIntent(
                        this,
                        studentIdentity
                )
        );
    }

    private void returnStudentAndFinish() {
        OmrStudentIdentity studentIdentity =
                createValidatedStudentIdentityOrNull();

        if (studentIdentity == null) {
            return;
        }

        Intent resultData = new Intent().putExtra(
                EXTRA_STUDENT_IDENTITY,
                studentIdentity
        );

        setResult(
                Activity.RESULT_OK,
                resultData
        );

        finish();
    }

    @Nullable
    private OmrStudentIdentity
    createValidatedStudentIdentityOrNull() {
        viewState = viewBinder.getCurrentViewState();

        if (!viewState.canContinue()) {
            viewBinder.showValidationErrors(viewState);
            return null;
        }

        return identityFactory.create(
                viewState.getNormalizedRegistration(),
                viewState.getNormalizedName(),
                viewState.getNormalizedClassName()
        );
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static OmrAnswerKeyDefinition extractAnswerKey(
            @Nullable Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        Serializable value =
                intent.getSerializableExtra(
                        EXTRA_ANSWER_KEY
                );

        if (!(value instanceof OmrAnswerKeyDefinition)) {
            return null;
        }

        return (OmrAnswerKeyDefinition) value;
    }

    private static OmrStudentIdentificationViewState
    restoreViewState(
            @Nullable Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return OmrStudentIdentificationViewState.empty();
        }

        return OmrStudentIdentificationViewState.from(
                savedInstanceState.getString(
                        STATE_REGISTRATION,
                        ""
                ),
                savedInstanceState.getString(
                        STATE_NAME,
                        ""
                ),
                savedInstanceState.getString(
                        STATE_CLASS_NAME,
                        ""
                )
        );
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        if (viewBinder != null) {
            viewState = viewBinder.getCurrentViewState();
        }

        if (viewState != null) {
            outState.putString(
                    STATE_REGISTRATION,
                    viewState.getRegistration()
            );

            outState.putString(
                    STATE_NAME,
                    viewState.getName()
            );

            outState.putString(
                    STATE_CLASS_NAME,
                    viewState.getClassName()
            );
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        identityFactory = null;
        viewState = null;
        answerKeyDefinition = null;

        super.onDestroy();
    }
}
