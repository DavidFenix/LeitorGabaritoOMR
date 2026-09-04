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
import com.example.leitorgabaritoomr.application.grading.OmrManualAnswerKeyDraft;
import com.example.leitorgabaritoomr.application.layout.OmrPublishedLayoutResolver;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.AvalieCeDevelopmentLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.io.Serializable;
import java.util.UUID;

/**
 * Coordena o cadastro manual de um gabarito oficial.
 *
 * A Activity mantém o rascunho imutável, enquanto o Binder apenas
 * apresenta o estado visual. Ao salvar, o gabarito criado é devolvido
 * ao chamador como um {@link OmrAnswerKeyDefinition} serializável.
 */
public final class OmrManualAnswerKeyActivity
        extends AppCompatActivity {

    public static final String EXTRA_CREATED_ANSWER_KEY =
            "com.example.leitorgabaritoomr.extra.CREATED_ANSWER_KEY";

    private static final String EXTRA_LAYOUT_ID =
            "com.example.leitorgabaritoomr.extra."
                    + "MANUAL_ANSWER_KEY_LAYOUT_ID";

    private static final String EXTRA_LAYOUT_VERSION =
            "com.example.leitorgabaritoomr.extra."
                    + "MANUAL_ANSWER_KEY_LAYOUT_VERSION";

    private static final String EXTRA_QUESTION_COUNT =
            "com.example.leitorgabaritoomr.extra."
                    + "MANUAL_ANSWER_KEY_QUESTION_COUNT";

    private static final String STATE_DRAFT =
            "omr.manual_answer_key.draft";

    private static final String STATE_ANSWER_KEY_NAME =
            "omr.manual_answer_key.name";

    private static final int INITIAL_ANSWER_KEY_VERSION = 1;
    private static final double DEFAULT_QUESTION_WEIGHT = 1.0;

    private OmrLayoutDefinition layoutDefinition;
    private OmrManualAnswerKeyDraft draft;
    private OmrManualAnswerKeyViewBinder viewBinder;

    /**
     * Único ponto de criação do Intent desta tela.
     */
    public static Intent createIntent(
            Context context
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        return new Intent(
                context,
                OmrManualAnswerKeyActivity.class
        );
    }

    /**
     * Abre o editor usando um dos modelos compactos publicados de 1 a 10.
     */
    public static Intent createCompactIntent(
            Context context,
            int questionCount
    ) {
        OmrSheetTemplateSpec spec =
                OmrSheetTemplateCatalog
                        .compactFourOptions(questionCount);

        return createIntent(context)
                .putExtra(
                        EXTRA_LAYOUT_ID,
                        spec.getTemplateId()
                )
                .putExtra(
                        EXTRA_LAYOUT_VERSION,
                        spec.getTemplateVersion()
                )
                .putExtra(
                        EXTRA_QUESTION_COUNT,
                        spec.getQuestionCount()
                );
    }

    /**
     * Extrai com segurança o gabarito devolvido por esta Activity.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrAnswerKeyDefinition extractCreatedAnswerKey(
            @Nullable Intent resultData
    ) {
        if (resultData == null) {
            return null;
        }

        Serializable value =
                resultData.getSerializableExtra(
                        EXTRA_CREATED_ANSWER_KEY
                );

        if (!(value instanceof OmrAnswerKeyDefinition)) {
            return null;
        }

        return (OmrAnswerKeyDefinition) value;
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_omr_manual_answer_key
        );

        setResult(Activity.RESULT_CANCELED);

        try {
            layoutDefinition = resolveRequestedLayout(
                    getIntent()
            );

        } catch (RuntimeException exception) {
            Toast.makeText(
                    this,
                    R.string.omr_manual_key_error_layout,
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        draft = restoreDraft(savedInstanceState);

        if (draft == null
                || !isCompatibleWithCurrentLayout(draft)) {

            draft = OmrManualAnswerKeyDraft.create(
                    layoutDefinition
            );
        }

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder =
                new OmrManualAnswerKeyViewBinder(
                        rootView
                );

        configureBinder();
        restoreAnswerKeyName(savedInstanceState);
        renderCurrentDraft();

    }

    private OmrLayoutDefinition resolveRequestedLayout(
            @Nullable Intent intent
    ) {
        if (intent == null
                || !intent.hasExtra(EXTRA_LAYOUT_ID)) {

            return AvalieCeDevelopmentLayoutFactory.create();
        }

        return new OmrPublishedLayoutResolver().resolve(
                intent.getStringExtra(EXTRA_LAYOUT_ID),
                intent.getIntExtra(
                        EXTRA_LAYOUT_VERSION,
                        0
                ),
                intent.getIntExtra(
                        EXTRA_QUESTION_COUNT,
                        0
                )
        );
    }

    private void configureBinder() {
        viewBinder.setOnAnswerChangedListener(
                new OmrManualAnswerKeyViewBinder.OnAnswerChangedListener() {

                    @Override
                    public void onOptionSelected(
                            String questionId,
                            String optionId
                    ) {
                        draft = draft.withSelection(
                                questionId,
                                optionId
                        );

                        renderCurrentDraft();
                    }

                    @Override
                    public void onSelectionCleared(
                            String questionId
                    ) {
                        draft = draft.withoutSelection(
                                questionId
                        );

                        renderCurrentDraft();
                    }
                }
        );

        viewBinder.setOnCancelClickListener(
                view -> cancelAndFinish()
        );

        viewBinder.setOnSaveClickListener(
                view -> saveAndFinish()
        );
    }

    private void renderCurrentDraft() {
        viewBinder.render(
                OmrManualAnswerKeyViewState.from(
                        draft
                )
        );
    }

    private void cancelAndFinish() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void saveAndFinish() {
        String answerKeyName =
                viewBinder.getAnswerKeyName();

        if (answerKeyName.isEmpty()) {
            viewBinder.showNameRequiredError();
            return;
        }

        viewBinder.clearNameError();

        if (!draft.isComplete()) {
            showIncompleteDraftMessage();
            return;
        }

        OmrAnswerKeyDefinition answerKeyDefinition =
                draft.toAnswerKeyDefinition(
                        layoutDefinition,
                        createAnswerKeyId(),
                        INITIAL_ANSWER_KEY_VERSION,
                        answerKeyName,
                        DEFAULT_QUESTION_WEIGHT
                );

        Intent resultData = new Intent().putExtra(
                EXTRA_CREATED_ANSWER_KEY,
                answerKeyDefinition
        );

        setResult(
                Activity.RESULT_OK,
                resultData
        );

        finish();
    }

    private void showIncompleteDraftMessage() {
        int remainingCount =
                draft.getRemainingCount();

        String message = getResources().getQuantityString(
                R.plurals.omr_manual_key_error_incomplete,
                remainingCount,
                remainingCount
        );

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private String createAnswerKeyId() {
        return "manual-" + UUID.randomUUID();
    }

    private boolean isCompatibleWithCurrentLayout(
            OmrManualAnswerKeyDraft restoredDraft
    ) {
        return restoredDraft.getLayoutId().equals(
                layoutDefinition.getId()
        )
                && restoredDraft.getLayoutVersion()
                == layoutDefinition.getVersion()
                && restoredDraft.getQuestionCount()
                == layoutDefinition.getQuestionCount();
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private OmrManualAnswerKeyDraft restoreDraft(
            @Nullable Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return null;
        }

        Serializable value =
                savedInstanceState.getSerializable(
                        STATE_DRAFT
                );

        if (!(value instanceof OmrManualAnswerKeyDraft)) {
            return null;
        }

        return (OmrManualAnswerKeyDraft) value;
    }

    private void restoreAnswerKeyName(
            @Nullable Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return;
        }

        viewBinder.setAnswerKeyName(
                savedInstanceState.getString(
                        STATE_ANSWER_KEY_NAME,
                        ""
                )
        );
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putSerializable(
                STATE_DRAFT,
                draft
        );

        if (viewBinder != null) {
            outState.putString(
                    STATE_ANSWER_KEY_NAME,
                    viewBinder.getAnswerKeyName()
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

        draft = null;
        layoutDefinition = null;

        super.onDestroy();
    }
}
