package com.example.leitorgabaritoomr.presentation.student;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.IdRes;

import com.example.leitorgabaritoomr.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Aplica {@link OmrStudentIdentificationViewState} ao formulario Android.
 *
 * O binder nao cria a identidade do aluno e nao abre a camera. Ele apenas
 * mantem a tela sincronizada com o estado visual e encaminha suas acoes.
 */
public final class OmrStudentIdentificationViewBinder {

    public interface OnFormChangedListener {

        void onFormChanged(
                OmrStudentIdentificationViewState viewState
        );
    }

    private final Context context;

    private final TextView answerKeyTextView;

    private final TextInputLayout registrationInputLayout;
    private final TextInputEditText registrationEditText;

    private final TextInputLayout nameInputLayout;
    private final TextInputEditText nameEditText;

    private final TextInputLayout classInputLayout;
    private final TextInputEditText classEditText;

    private final Button historyButton;
    private final Button cancelButton;
    private final Button startButton;

    private final TextWatcher formTextWatcher;

    private OmrStudentIdentificationViewState currentViewState;
    private OnFormChangedListener formChangedListener;

    private boolean rendering;
    private boolean released;

    public OmrStudentIdentificationViewBinder(
            View rootView
    ) {
        if (rootView == null) {
            throw new IllegalArgumentException(
                    "A View raiz da identificacao e obrigatoria."
            );
        }

        context = rootView.getContext();

        answerKeyTextView = requireView(
                rootView,
                R.id.textOmrStudentAnswerKey,
                TextView.class
        );

        registrationInputLayout = requireView(
                rootView,
                R.id.inputLayoutOmrStudentRegistration,
                TextInputLayout.class
        );

        registrationEditText = requireView(
                rootView,
                R.id.editOmrStudentRegistration,
                TextInputEditText.class
        );

        nameInputLayout = requireView(
                rootView,
                R.id.inputLayoutOmrStudentName,
                TextInputLayout.class
        );

        nameEditText = requireView(
                rootView,
                R.id.editOmrStudentName,
                TextInputEditText.class
        );

        classInputLayout = requireView(
                rootView,
                R.id.inputLayoutOmrStudentClass,
                TextInputLayout.class
        );

        classEditText = requireView(
                rootView,
                R.id.editOmrStudentClass,
                TextInputEditText.class
        );

        historyButton = requireView(
                rootView,
                R.id.buttonOmrStudentHistory,
                Button.class
        );

        cancelButton = requireView(
                rootView,
                R.id.buttonOmrStudentCancel,
                Button.class
        );

        startButton = requireView(
                rootView,
                R.id.buttonOmrStudentStart,
                Button.class
        );

        currentViewState =
                OmrStudentIdentificationViewState.empty();

        formTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
                // Nenhuma acao necessaria.
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
                // O estado final e lido em afterTextChanged.
            }

            @Override
            public void afterTextChanged(
                    Editable editable
            ) {
                handleTextChanged();
            }
        };

        registrationEditText.addTextChangedListener(
                formTextWatcher
        );
        nameEditText.addTextChangedListener(formTextWatcher);
        classEditText.addTextChangedListener(formTextWatcher);

        classEditText.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId != EditorInfo.IME_ACTION_DONE) {
                        return false;
                    }

                    if (!currentViewState.canContinue()) {
                        return false;
                    }

                    return startButton.performClick();
                }
        );

        applyActions(currentViewState);
    }

    public void renderAnswerKey(
            String answerKeyName,
            int answerKeyVersion,
            int questionCount
    ) {
        ensureNotReleased();

        if (answerKeyName == null
                || answerKeyName.trim().isEmpty()
                || answerKeyVersion <= 0
                || questionCount <= 0) {

            throw new IllegalArgumentException(
                    "Os dados do gabarito sao invalidos."
            );
        }

        answerKeyTextView.setText(
                context.getString(
                        R.string
                        .omr_student_identification_answer_key_format,
                        answerKeyName.trim(),
                        answerKeyVersion,
                        questionCount
                )
        );
    }

    public void render(
            OmrStudentIdentificationViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual do aluno e obrigatorio."
            );
        }

        rendering = true;

        try {
            setTextIfDifferent(
                    registrationEditText,
                    viewState.getRegistration()
            );
            setTextIfDifferent(
                    nameEditText,
                    viewState.getName()
            );
            setTextIfDifferent(
                    classEditText,
                    viewState.getClassName()
            );

            currentViewState = viewState;
            applyActions(viewState);
            clearResolvedErrors(viewState);

        } finally {
            rendering = false;
        }
    }

    public OmrStudentIdentificationViewState
    getCurrentViewState() {
        ensureNotReleased();
        return readViewState();
    }

    public void showValidationErrors(
            OmrStudentIdentificationViewState viewState
    ) {
        ensureNotReleased();

        if (viewState == null) {
            throw new IllegalArgumentException(
                    "O estado visual do aluno e obrigatorio."
            );
        }

        registrationInputLayout.setError(
                viewState.isRegistrationValid()
                        ? null
                        : context.getString(
                        R.string
                        .omr_student_identification_error_registration
                )
        );

        nameInputLayout.setError(
                viewState.isNameValid()
                        ? null
                        : context.getString(
                        R.string
                        .omr_student_identification_error_name
                )
        );

        classInputLayout.setError(
                viewState.isClassNameValid()
                        ? null
                        : context.getString(
                        R.string
                        .omr_student_identification_error_class
                )
        );

        requestFirstInvalidFocus(
                viewState.getFirstValidationError()
        );
    }

    public void clearValidationErrors() {
        ensureNotReleased();

        registrationInputLayout.setError(null);
        nameInputLayout.setError(null);
        classInputLayout.setError(null);
    }

    public void setOnFormChangedListener(
            OnFormChangedListener listener
    ) {
        ensureNotReleased();
        formChangedListener = listener;
    }

    public void setOnCancelClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        cancelButton.setOnClickListener(listener);
    }

    public void setOnHistoryClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        historyButton.setOnClickListener(listener);
    }

    public void setOnStartClickListener(
            View.OnClickListener listener
    ) {
        ensureNotReleased();
        startButton.setOnClickListener(listener);
    }

    public void release() {
        if (released) {
            return;
        }

        released = true;
        formChangedListener = null;

        registrationEditText.removeTextChangedListener(
                formTextWatcher
        );
        nameEditText.removeTextChangedListener(formTextWatcher);
        classEditText.removeTextChangedListener(formTextWatcher);

        classEditText.setOnEditorActionListener(null);
        historyButton.setOnClickListener(null);
        cancelButton.setOnClickListener(null);
        startButton.setOnClickListener(null);
    }

    private void handleTextChanged() {
        if (rendering || released) {
            return;
        }

        currentViewState = readViewState();

        applyActions(currentViewState);
        clearResolvedErrors(currentViewState);

        OnFormChangedListener listener =
                formChangedListener;

        if (listener != null) {
            listener.onFormChanged(currentViewState);
        }
    }

    private OmrStudentIdentificationViewState readViewState() {
        return OmrStudentIdentificationViewState.from(
                textOf(registrationEditText),
                textOf(nameEditText),
                textOf(classEditText)
        );
    }

    private void applyActions(
            OmrStudentIdentificationViewState viewState
    ) {
        boolean canContinue = viewState.canContinue();

        historyButton.setEnabled(canContinue);
        historyButton.setAlpha(canContinue ? 1.0f : 0.55f);

        startButton.setEnabled(canContinue);
        startButton.setAlpha(canContinue ? 1.0f : 0.55f);
    }

    private void clearResolvedErrors(
            OmrStudentIdentificationViewState viewState
    ) {
        if (viewState.isRegistrationValid()) {
            registrationInputLayout.setError(null);
        }

        if (viewState.isNameValid()) {
            nameInputLayout.setError(null);
        }

        if (viewState.isClassNameValid()) {
            classInputLayout.setError(null);
        }
    }

    private void requestFirstInvalidFocus(
            OmrStudentIdentificationViewState.ValidationError error
    ) {
        switch (error) {
            case REGISTRATION_REQUIRED:
                registrationEditText.requestFocus();
                break;

            case NAME_REQUIRED:
                nameEditText.requestFocus();
                break;

            case CLASS_NAME_REQUIRED:
                classEditText.requestFocus();
                break;

            case NONE:
                break;

            default:
                throw new IllegalStateException(
                        "Erro de validacao nao reconhecido: "
                                + error
                );
        }
    }

    private static String textOf(
            TextInputEditText editText
    ) {
        Editable editable = editText.getText();

        return editable == null
                ? ""
                : editable.toString();
    }

    private static void setTextIfDifferent(
            TextInputEditText editText,
            String value
    ) {
        String currentValue = textOf(editText);

        if (currentValue.equals(value)) {
            return;
        }

        editText.setText(value);
        editText.setSelection(editText.length());
    }

    private void ensureNotReleased() {
        if (released) {
            throw new IllegalStateException(
                    "O binder da identificacao ja foi liberado."
            );
        }
    }

    private static <T extends View> T requireView(
            View rootView,
            @IdRes int viewId,
            Class<T> expectedType
    ) {
        View view = rootView.findViewById(viewId);

        if (!expectedType.isInstance(view)) {
            throw new IllegalStateException(
                    "View obrigatoria ausente ou com tipo incorreto: "
                            + viewId
            );
        }

        return expectedType.cast(view);
    }
}
