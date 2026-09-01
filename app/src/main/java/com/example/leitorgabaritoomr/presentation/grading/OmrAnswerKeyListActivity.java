package com.example.leitorgabaritoomr.presentation.grading;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Coordena a listagem, seleção e exclusão dos gabaritos oficiais armazenados
 * no dispositivo.
 *
 * O repositório continua sendo a fonte da verdade. A Activity apenas cria uma
 * fotografia visual, recebe as ações do Binder e renderiza novamente após cada
 * alteração persistida.
 */
public final class OmrAnswerKeyListActivity
        extends AppCompatActivity {

    public static final String EXTRA_REPOSITORY_CHANGED =
            "com.example.leitorgabaritoomr.extra."
                    + "ANSWER_KEY_REPOSITORY_CHANGED";

    public static final String EXTRA_ACTIVE_ANSWER_KEY =
            "com.example.leitorgabaritoomr.extra."
                    + "ACTIVE_ANSWER_KEY";

    private static final String STATE_REPOSITORY_CHANGED =
            "omr.answer_key_list.repository_changed";

    private static final String TAG =
            "OmrAnswerKeyList";

    private OmrAnswerKeyRepository repository;
    private OmrAnswerKeyListViewBinder viewBinder;
    private boolean repositoryChanged;

    private final ActivityResultLauncher<Intent>
            manualAnswerKeyLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    this::handleManualAnswerKeyResult
            );

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
                OmrAnswerKeyListActivity.class
        );
    }

    public static boolean didRepositoryChange(
            @Nullable Intent resultData
    ) {
        return resultData != null
                && resultData.getBooleanExtra(
                EXTRA_REPOSITORY_CHANGED,
                false
        );
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrAnswerKeyDefinition extractActiveAnswerKey(
            @Nullable Intent resultData
    ) {
        if (resultData == null) {
            return null;
        }

        Serializable value =
                resultData.getSerializableExtra(
                        EXTRA_ACTIVE_ANSWER_KEY
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
                R.layout.activity_omr_answer_key_list
        );

        repository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        this
                );

        View rootView = findViewById(
                android.R.id.content
        );

        viewBinder =
                new OmrAnswerKeyListViewBinder(
                        rootView
                );

        repositoryChanged = savedInstanceState != null
                && savedInstanceState.getBoolean(
                STATE_REPOSITORY_CHANGED,
                false
        );

        configureBinder();

        if (repositoryChanged) {
            updateActivityResult();
        } else {
            setResult(Activity.RESULT_CANCELED);
        }

        renderRepository();
    }

    private void configureBinder() {
        viewBinder.setOnAnswerKeyActionListener(
                new OmrAnswerKeyListViewBinder
                        .OnAnswerKeyActionListener() {

                    @Override
                    public void onSelectAnswerKey(
                            String answerKeyId,
                            int answerKeyVersion
                    ) {
                        selectAnswerKey(
                                answerKeyId,
                                answerKeyVersion
                        );
                    }

                    @Override
                    public void onDeleteAnswerKey(
                            String answerKeyId,
                            int answerKeyVersion,
                            String answerKeyName,
                            boolean active
                    ) {
                        confirmDeleteAnswerKey(
                                answerKeyId,
                                answerKeyVersion,
                                answerKeyName,
                                active
                        );
                    }
                }
        );

        viewBinder.setOnCreateClickListener(
                view -> manualAnswerKeyLauncher.launch(
                        OmrManualAnswerKeyActivity.createIntent(
                                this
                        )
                )
        );

        viewBinder.setOnBackClickListener(
                view -> finish()
        );
    }

    private void handleManualAnswerKeyResult(
            ActivityResult activityResult
    ) {
        if (activityResult.getResultCode()
                != Activity.RESULT_OK) {
            return;
        }

        OmrAnswerKeyDefinition createdAnswerKey =
                OmrManualAnswerKeyActivity
                        .extractCreatedAnswerKey(
                                activityResult.getData()
                        );

        if (createdAnswerKey == null) {
            Log.e(
                    TAG,
                    "O cadastro retornou sem gabarito válido."
            );

            showMessage(
                    R.string.omr_answer_key_list_create_error
            );

            return;
        }

        try {
            repository.saveActive(createdAnswerKey);

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível salvar o novo gabarito.",
                    exception
            );

            showMessage(
                    R.string.omr_answer_key_list_save_error
            );

            return;
        }

        markRepositoryChanged();
        renderRepository();

        Toast.makeText(
                this,
                getString(
                        R.string
                        .omr_answer_key_list_created_message,
                        createdAnswerKey.getName()
                ),
                Toast.LENGTH_LONG
        ).show();

        Log.i(
                TAG,
                "Gabarito cadastrado e selecionado"
                        + " | id="
                        + createdAnswerKey.getId()
                        + " | versão="
                        + createdAnswerKey.getVersion()
        );
    }

    private void selectAnswerKey(
            String answerKeyId,
            int answerKeyVersion
    ) {
        OmrAnswerKeyDefinition selectedAnswerKey;

        try {
            repository.selectActive(
                    answerKeyId,
                    answerKeyVersion
            );

            selectedAnswerKey = repository.findOrNull(
                    answerKeyId,
                    answerKeyVersion
            );

            if (selectedAnswerKey == null) {
                throw new IllegalStateException(
                        "O gabarito selecionado não foi recuperado."
                );
            }

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível selecionar o gabarito."
                            + " | id="
                            + answerKeyId
                            + " | versão="
                            + answerKeyVersion,
                    exception
            );

            showMessage(
                    R.string.omr_answer_key_list_select_error
            );

            return;
        }

        markRepositoryChanged();
        renderRepository();

        Toast.makeText(
                this,
                getString(
                        R.string
                        .omr_answer_key_list_selected_message,
                        selectedAnswerKey.getName()
                ),
                Toast.LENGTH_SHORT
        ).show();

        Log.i(
                TAG,
                "Gabarito ativo alterado"
                        + " | id="
                        + selectedAnswerKey.getId()
                        + " | versão="
                        + selectedAnswerKey.getVersion()
        );
    }

    private void confirmDeleteAnswerKey(
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName,
            boolean active
    ) {
        int messageResource = active
                ? R.string
                .omr_answer_key_list_delete_active_message
                : R.string
                .omr_answer_key_list_delete_message;

        new AlertDialog.Builder(this)
                .setTitle(
                        R.string.omr_answer_key_list_delete_title
                )
                .setMessage(
                        getString(
                                messageResource,
                                answerKeyName
                        )
                )
                .setNegativeButton(
                        R.string
                        .omr_answer_key_list_delete_cancel,
                        null
                )
                .setPositiveButton(
                        R.string
                        .omr_answer_key_list_delete_confirm,
                        (dialog, which) -> deleteAnswerKey(
                                answerKeyId,
                                answerKeyVersion,
                                answerKeyName
                        )
                )
                .show();
    }

    private void deleteAnswerKey(
            String answerKeyId,
            int answerKeyVersion,
            String answerKeyName
    ) {
        boolean deleted;

        try {
            deleted = repository.delete(
                    answerKeyId,
                    answerKeyVersion
            );

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível excluir o gabarito."
                            + " | id="
                            + answerKeyId
                            + " | versão="
                            + answerKeyVersion,
                    exception
            );

            showMessage(
                    R.string.omr_answer_key_list_delete_error
            );

            return;
        }

        if (!deleted) {
            showMessage(
                    R.string.omr_answer_key_list_delete_error
            );

            renderRepository();
            return;
        }

        markRepositoryChanged();
        renderRepository();

        Toast.makeText(
                this,
                getString(
                        R.string
                        .omr_answer_key_list_deleted_message,
                        answerKeyName
                ),
                Toast.LENGTH_SHORT
        ).show();

        Log.i(
                TAG,
                "Gabarito excluído"
                        + " | id="
                        + answerKeyId
                        + " | versão="
                        + answerKeyVersion
        );
    }

    private void renderRepository() {
        try {
            List<OmrAnswerKeyDefinition> answerKeys =
                    repository.loadAll();

            OmrAnswerKeyDefinition activeAnswerKey =
                    repository.loadActiveOrNull();

            viewBinder.render(
                    OmrAnswerKeyListViewState.from(
                            answerKeys,
                            activeAnswerKey
                    )
            );

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível apresentar o repositório.",
                    exception
            );

            viewBinder.render(
                    OmrAnswerKeyListViewState.from(
                            Collections
                                    .<OmrAnswerKeyDefinition>emptyList(),
                            null
                    )
            );

            showMessage(
                    R.string.omr_answer_key_list_load_error
            );
        }
    }

    private void markRepositoryChanged() {
        repositoryChanged = true;
        updateActivityResult();
    }

    private void updateActivityResult() {
        Intent resultData = new Intent().putExtra(
                EXTRA_REPOSITORY_CHANGED,
                true
        );

        try {
            OmrAnswerKeyDefinition activeAnswerKey =
                    repository.loadActiveOrNull();

            if (activeAnswerKey != null) {
                resultData.putExtra(
                        EXTRA_ACTIVE_ANSWER_KEY,
                        activeAnswerKey
                );
            }

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Não foi possível anexar o gabarito ativo"
                            + " ao resultado da tela.",
                    exception
            );
        }

        setResult(
                Activity.RESULT_OK,
                resultData
        );
    }

    private void showMessage(
            int messageResource
    ) {
        Toast.makeText(
                this,
                messageResource,
                Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {
        outState.putBoolean(
                STATE_REPOSITORY_CHANGED,
                repositoryChanged
        );

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        repository = null;

        super.onDestroy();
    }
}
