package com.example.leitorgabaritoomr.presentation.student;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.student.OmrStudentCsvParser;
import com.example.leitorgabaritoomr.application.student.OmrStudentRegistrationService;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cadastro local de alunos com entrada manual e importação CSV confirmada.
 */
public final class OmrStudentListActivity extends AppCompatActivity {

    private static final String TAG = "OmrStudentList";
    private static final int MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PREVIEW_ROWS = 10;
    private static final int MAX_PREVIEW_ISSUES = 10;

    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra."
                    + "STUDENT_DATABASE_NAME";

    private static final String[] CSV_MIME_TYPES = {
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel"
    };

    private OmrSQLiteStudentRepository repository;
    private OmrStudentRegistrationService registrationService;
    private OmrStudentCsvParser csvParser;
    private OmrStudentListViewBinder viewBinder;
    private ExecutorService backgroundExecutor;
    private List<OmrStudentProfile> studentCatalog =
            Collections.emptyList();

    private AlertDialog registrationDialog;
    private AlertDialog csvPreviewDialog;
    private volatile boolean released;

    private final ActivityResultLauncher<String[]> csvDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::handleCsvDocument
            );

    public static Intent createIntent(Context context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        return new Intent(context, OmrStudentListActivity.class);
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String databaseName
    ) {
        if (databaseName == null
                || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco isolado é obrigatório."
            );
        }

        return createIntent(context).putExtra(
                EXTRA_DATABASE_NAME,
                databaseName.trim()
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_omr_student_list);

        repository = createRepository();
        registrationService =
                new OmrStudentRegistrationService(repository);
        csvParser = new OmrStudentCsvParser();
        backgroundExecutor = Executors.newSingleThreadExecutor();

        viewBinder = new OmrStudentListViewBinder(
                findViewById(android.R.id.content)
        );

        configureBinder();
        reloadRepository();
    }

    private OmrSQLiteStudentRepository createRepository() {
        String databaseName = getIntent().getStringExtra(
                EXTRA_DATABASE_NAME
        );

        if (databaseName == null
                || databaseName.trim().isEmpty()) {
            return new OmrSQLiteStudentRepository(this);
        }

        return new OmrSQLiteStudentRepository(
                this,
                databaseName.trim()
        );
    }

    private void configureBinder() {
        viewBinder.setOnSearchQueryChangedListener(
                this::renderQuery
        );
        viewBinder.setOnStudentActionListener(
                this::showEditStudentDialog
        );
        viewBinder.setOnBackClickListener(
                view -> finish()
        );
        viewBinder.setOnCreateClickListener(
                view -> showRegistrationDialog(null)
        );
        viewBinder.setOnImportCsvClickListener(
                view -> csvDocumentLauncher.launch(
                        CSV_MIME_TYPES
                )
        );
    }

    private void reloadRepository() {
        try {
            studentCatalog = new ArrayList<>(repository.loadAll());
            renderQuery(viewBinder.getSearchQuery());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar alunos.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_student_list_load_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderQuery(String query) {
        viewBinder.render(OmrStudentListViewState.from(
                studentCatalog,
                query
        ));
    }

    private void showEditStudentDialog(String studentId) {
        OmrStudentProfile student =
                repository.findByIdOrNull(studentId);

        if (student == null) {
            Toast.makeText(
                    this,
                    R.string.omr_student_registration_missing,
                    Toast.LENGTH_LONG
            ).show();
            reloadRepository();
            return;
        }

        showRegistrationDialog(student);
    }

    private void showRegistrationDialog(
            @Nullable OmrStudentProfile existingStudent
    ) {
        if (registrationDialog != null
                && registrationDialog.isShowing()) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_omr_student_registration,
                null,
                false
        );

        TextInputLayout registrationLayout = dialogView.findViewById(
                R.id.inputLayoutOmrStudentRegistrationDialog
        );
        TextInputEditText registrationEditText = dialogView.findViewById(
                R.id.editOmrStudentRegistrationDialog
        );
        TextInputLayout nameLayout = dialogView.findViewById(
                R.id.inputLayoutOmrStudentNameDialog
        );
        TextInputEditText nameEditText = dialogView.findViewById(
                R.id.editOmrStudentNameDialog
        );

        boolean editing = existingStudent != null;

        if (editing) {
            registrationEditText.setText(
                    existingStudent.getRegistration()
            );
            registrationEditText.setEnabled(false);
            nameEditText.setText(existingStudent.getName());
            nameEditText.setSelection(nameEditText.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing
                        ? R.string.omr_student_registration_edit_title
                        : R.string.omr_student_registration_create_title)
                .setView(dialogView)
                .setNegativeButton(
                        R.string.omr_student_registration_action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_student_registration_action_save,
                        null
                )
                .create();

        registrationDialog = dialog;

        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );
            saveButton.setOnClickListener(view -> saveStudentFromDialog(
                    dialog,
                    registrationLayout,
                    registrationEditText,
                    nameLayout,
                    nameEditText
            ));

            nameEditText.setOnEditorActionListener(
                    (view, actionId, event) -> {
                        if (actionId != EditorInfo.IME_ACTION_DONE) {
                            return false;
                        }
                        return saveButton.performClick();
                    }
            );

            if (!editing) {
                registrationEditText.requestFocus();
            }
        });

        dialog.setOnDismissListener(ignored -> {
            nameEditText.setOnEditorActionListener(null);
            if (registrationDialog == dialog) {
                registrationDialog = null;
            }
        });

        dialog.show();
    }

    private void saveStudentFromDialog(
            AlertDialog dialog,
            TextInputLayout registrationLayout,
            TextInputEditText registrationEditText,
            TextInputLayout nameLayout,
            TextInputEditText nameEditText
    ) {
        String registration = textOf(registrationEditText);
        String name = textOf(nameEditText);

        boolean registrationValid = !registration.trim().isEmpty();
        boolean nameValid = !name.trim().isEmpty();

        registrationLayout.setError(registrationValid
                ? null
                : getString(
                R.string.omr_student_registration_error_registration
        ));
        nameLayout.setError(nameValid
                ? null
                : getString(
                R.string.omr_student_registration_error_name
        ));

        if (!registrationValid || !nameValid) {
            if (!registrationValid) {
                registrationEditText.requestFocus();
            } else {
                nameEditText.requestFocus();
            }
            return;
        }

        boolean updating = repository.findByRegistrationOrNull(
                registration
        ) != null;

        try {
            registrationService.registerOrUpdate(
                    registration,
                    name
            );
            dialog.dismiss();
            reloadRepository();
            Toast.makeText(
                    this,
                    updating
                            ? R.string.omr_student_registration_updated
                            : R.string.omr_student_registration_created,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao salvar aluno.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_student_registration_save_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void handleCsvDocument(@Nullable Uri documentUri) {
        if (documentUri == null || released) {
            return;
        }

        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try {
                byte[] bytes = readCsvBytes(documentUri);
                OmrStudentCsvParser.Preview preview =
                        csvParser.parse(bytes);
                postToUi(() -> showCsvPreview(preview));
            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_student_csv_too_large
                ));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao ler CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_student_csv_read_error
                ));
            }
        });
    }

    private byte[] readCsvBytes(Uri documentUri)
            throws IOException, CsvTooLargeException {
        try (InputStream input = getContentResolver()
                .openInputStream(documentUri)) {
            if (input == null) {
                throw new IOException(
                        "O provedor não abriu o arquivo selecionado."
                );
            }

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_CSV_BYTES) {
                    throw new CsvTooLargeException();
                }
                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    void showCsvPreview(OmrStudentCsvParser.Preview preview) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A prévia do CSV é obrigatória."
            );
        }

        viewBinder.setBusy(false);
        dismissCsvPreviewDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(preview.isReadyToImport()
                        ? R.string.omr_student_csv_preview_title
                        : R.string.omr_student_csv_preview_error_title)
                .setMessage(formatCsvPreview(preview));

        if (preview.isReadyToImport()) {
            builder.setNegativeButton(
                    R.string.omr_student_csv_action_cancel,
                    null
            );
            builder.setPositiveButton(
                    R.string.omr_student_csv_action_import,
                    (dialog, which) -> importCsvPreview(preview)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_student_csv_action_close,
                    null
            );
        }

        AlertDialog dialog = builder.create();
        csvPreviewDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (csvPreviewDialog == dialog) {
                csvPreviewDialog = null;
            }
        });
        dialog.show();
    }

    private String formatCsvPreview(
            OmrStudentCsvParser.Preview preview
    ) {
        String delimiter = String.valueOf(preview.getDelimiter());

        if (preview.isReadyToImport()) {
            int rowCount = preview.getRows().size();
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                    R.plurals.omr_student_csv_preview_valid_rows,
                    rowCount,
                    rowCount
            ));

            int visibleRowCount = Math.min(
                    rowCount,
                    MAX_PREVIEW_ROWS
            );

            for (int index = 0; index < visibleRowCount; index++) {
                OmrStudentCsvParser.Row row =
                        preview.getRows().get(index);
                message.append("\n\n• ")
                        .append(row.getRegistration())
                        .append(" — ")
                        .append(row.getName());
            }

            int hiddenRowCount = rowCount - visibleRowCount;
            if (hiddenRowCount > 0) {
                message.append("\n\n")
                        .append(getResources().getQuantityString(
                                R.plurals.omr_student_csv_preview_more_rows,
                                hiddenRowCount,
                                hiddenRowCount
                        ));
            }

            message.append("\n\n")
                    .append(getString(
                    R.string.omr_student_csv_preview_update_note
            ))
                    .append("\n\n")
                    .append(getString(
                    R.string.omr_student_csv_preview_technical,
                    preview.getEncoding(),
                    delimiter
            ));

            return message.toString();
        }

        List<OmrStudentCsvParser.Issue> issues =
                preview.getIssues();
        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals.omr_student_csv_preview_error_summary,
                        issues.size(),
                        issues.size()
                )
        );

        int visibleIssueCount = Math.min(
                issues.size(),
                MAX_PREVIEW_ISSUES
        );

        for (int index = 0; index < visibleIssueCount; index++) {
            message.append("\n\n• ")
                    .append(issues.get(index));
        }

        int hiddenIssueCount = issues.size() - visibleIssueCount;
        if (hiddenIssueCount > 0) {
            message.append("\n\n")
                    .append(getResources().getQuantityString(
                            R.plurals.omr_student_csv_preview_more_errors,
                            hiddenIssueCount,
                            hiddenIssueCount
                    ));
        }

        return message.toString();
    }

    private void importCsvPreview(
            OmrStudentCsvParser.Preview preview
    ) {
        viewBinder.setBusy(true);

        backgroundExecutor.execute(() -> {
            try {
                OmrStudentRegistrationService.ImportSummary summary =
                        registrationService.importPreview(preview);

                postToUi(() -> {
                    viewBinder.setBusy(false);
                    reloadRepository();
                    Toast.makeText(
                            this,
                            getString(
                                    R.string.omr_student_csv_import_success,
                                    summary.getCreatedCount(),
                                    summary.getUpdatedCount()
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao importar CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_student_csv_import_error
                ));
            }
        });
    }

    private void showBackgroundError(int messageResource) {
        viewBinder.setBusy(false);
        Toast.makeText(
                this,
                messageResource,
                Toast.LENGTH_LONG
        ).show();
    }

    private void postToUi(Runnable action) {
        runOnUiThread(() -> {
            if (!released) {
                action.run();
            }
        });
    }

    private static String textOf(
            TextInputEditText editText
    ) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString();
    }

    private void dismissCsvPreviewDialog() {
        if (csvPreviewDialog != null) {
            csvPreviewDialog.dismiss();
            csvPreviewDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        released = true;

        if (registrationDialog != null) {
            registrationDialog.dismiss();
            registrationDialog = null;
        }

        dismissCsvPreviewDialog();

        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }

        if (backgroundExecutor != null) {
            backgroundExecutor.shutdownNow();
            backgroundExecutor = null;
        }

        if (repository != null) {
            repository.close();
            repository = null;
        }

        registrationService = null;
        csvParser = null;
        studentCatalog = Collections.emptyList();

        super.onDestroy();
    }

    private static final class CsvTooLargeException
            extends Exception {
    }
}
