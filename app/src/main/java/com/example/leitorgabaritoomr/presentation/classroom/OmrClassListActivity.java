package com.example.leitorgabaritoomr.presentation.classroom;

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
import com.example.leitorgabaritoomr.application.classroom.OmrClassRosterCsvParser;
import com.example.leitorgabaritoomr.application.classroom.OmrClassRosterRegistrationService;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassEnrollmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassRosterBatchRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gerencia turmas locais com cadastro manual e importacao CSV. */
public final class OmrClassListActivity extends AppCompatActivity {

    private static final String TAG = "OmrClassList";
    private static final int MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PREVIEW_ROWS = 10;
    private static final int MAX_PREVIEW_ISSUES = 10;

    static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra.CLASS_DATABASE_NAME";

    private static final String[] CSV_MIME_TYPES = {
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel"
    };

    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrSQLiteClassRosterBatchRepository batchRepository;
    private OmrClassRosterRegistrationService registrationService;
    private OmrClassRosterCsvParser csvParser;
    private OmrClassListViewBinder viewBinder;
    private ExecutorService backgroundExecutor;
    private List<OmrClassGroup> classCatalog = Collections.emptyList();
    private Map<String, Integer> studentCounts = Collections.emptyMap();

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
                    "O contexto e obrigatorio."
            );
        }
        return new Intent(context, OmrClassListActivity.class);
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String databaseName
    ) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco isolado e obrigatorio."
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
        setContentView(R.layout.activity_omr_class_list);

        createDependencies();
        backgroundExecutor = Executors.newSingleThreadExecutor();
        viewBinder = new OmrClassListViewBinder(
                findViewById(android.R.id.content)
        );
        configureBinder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!released && viewBinder != null) {
            reloadRepository();
        }
    }

    private void createDependencies() {
        String databaseName = getIntent().getStringExtra(
                EXTRA_DATABASE_NAME
        );
        if (databaseName == null || databaseName.trim().isEmpty()) {
            classRepository = new OmrSQLiteClassGroupRepository(this);
            enrollmentRepository =
                    new OmrSQLiteClassEnrollmentRepository(this);
            studentRepository = new OmrSQLiteStudentRepository(this);
            batchRepository =
                    new OmrSQLiteClassRosterBatchRepository(this);
        } else {
            String normalized = databaseName.trim();
            classRepository = new OmrSQLiteClassGroupRepository(
                    this, normalized
            );
            enrollmentRepository =
                    new OmrSQLiteClassEnrollmentRepository(
                            this, normalized
                    );
            studentRepository = new OmrSQLiteStudentRepository(
                    this, normalized
            );
            batchRepository =
                    new OmrSQLiteClassRosterBatchRepository(
                            this, normalized
                    );
        }

        registrationService = new OmrClassRosterRegistrationService(
                classRepository,
                enrollmentRepository,
                studentRepository,
                batchRepository
        );
        csvParser = new OmrClassRosterCsvParser();
    }

    private void configureBinder() {
        viewBinder.setOnSearchQueryChangedListener(this::renderQuery);
        viewBinder.setOnClassActionListener(
                new OmrClassListViewBinder.OnClassActionListener() {
                    @Override
                    public void onOpenRoster(String classGroupId) {
                        openRoster(classGroupId);
                    }

                    @Override
                    public void onEditClass(String classGroupId) {
                        showEditClassDialog(classGroupId);
                    }
                }
        );
        viewBinder.setOnBackClickListener(view -> finish());
        viewBinder.setOnCreateClickListener(
                view -> showRegistrationDialog(null)
        );
        viewBinder.setOnImportCsvClickListener(
                view -> csvDocumentLauncher.launch(CSV_MIME_TYPES)
        );
    }

    private void reloadRepository() {
        try {
            classCatalog = new ArrayList<>(classRepository.loadAll());
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (OmrClassGroup classGroup : classCatalog) {
                counts.put(
                        classGroup.getClassGroupId(),
                        enrollmentRepository.loadForClassGroup(
                                classGroup.getClassGroupId()
                        ).size()
                );
            }
            studentCounts = counts;
            renderQuery(viewBinder.getSearchQuery());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar turmas.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_class_list_load_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderQuery(String query) {
        viewBinder.render(OmrClassListViewState.from(
                classCatalog, studentCounts, query
        ));
    }

    private void openRoster(String classGroupId) {
        OmrClassGroup classGroup = classRepository.findByIdOrNull(
                classGroupId
        );
        if (classGroup == null) {
            Toast.makeText(
                    this,
                    R.string.omr_class_registration_missing,
                    Toast.LENGTH_LONG
            ).show();
            reloadRepository();
            return;
        }

        String databaseName = getIntent().getStringExtra(
                EXTRA_DATABASE_NAME
        );
        Intent intent = databaseName == null
                || databaseName.trim().isEmpty()
                ? OmrClassRosterActivity.createIntent(
                this, classGroupId
        )
                : OmrClassRosterActivity.createIsolatedStorageIntent(
                this, classGroupId, databaseName
        );
        startActivity(intent);
    }

    private void showEditClassDialog(String classGroupId) {
        OmrClassGroup classGroup = classRepository.findByIdOrNull(
                classGroupId
        );
        if (classGroup == null) {
            Toast.makeText(
                    this,
                    R.string.omr_class_registration_missing,
                    Toast.LENGTH_LONG
            ).show();
            reloadRepository();
            return;
        }
        showRegistrationDialog(classGroup);
    }

    private void showRegistrationDialog(
            @Nullable OmrClassGroup existingClass
    ) {
        if (registrationDialog != null
                && registrationDialog.isShowing()) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_omr_class_registration,
                null,
                false
        );
        TextInputLayout nameLayout = dialogView.findViewById(
                R.id.inputLayoutOmrClassNameDialog
        );
        TextInputEditText nameEditText = dialogView.findViewById(
                R.id.editOmrClassNameDialog
        );
        TextInputLayout gradeLayout = dialogView.findViewById(
                R.id.inputLayoutOmrClassGradeDialog
        );
        TextInputEditText gradeEditText = dialogView.findViewById(
                R.id.editOmrClassGradeDialog
        );
        TextInputLayout yearLayout = dialogView.findViewById(
                R.id.inputLayoutOmrClassYearDialog
        );
        TextInputEditText yearEditText = dialogView.findViewById(
                R.id.editOmrClassYearDialog
        );

        boolean editing = existingClass != null;
        if (editing) {
            nameEditText.setText(existingClass.getName());
            gradeEditText.setText(existingClass.getGradeLevel());
            yearEditText.setText(Integer.toString(
                    existingClass.getSchoolYear()
            ));
            yearEditText.setEnabled(false);
            nameEditText.setSelection(nameEditText.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing
                        ? R.string.omr_class_registration_edit_title
                        : R.string.omr_class_registration_create_title)
                .setView(dialogView)
                .setNegativeButton(
                        R.string.omr_class_registration_action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_class_registration_action_save,
                        null
                )
                .create();
        registrationDialog = dialog;

        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );
            saveButton.setOnClickListener(view -> saveClassFromDialog(
                    dialog,
                    existingClass,
                    nameLayout,
                    nameEditText,
                    gradeLayout,
                    gradeEditText,
                    yearLayout,
                    yearEditText
            ));
            yearEditText.setOnEditorActionListener(
                    (view, actionId, event) -> {
                        if (actionId != EditorInfo.IME_ACTION_DONE) {
                            return false;
                        }
                        return saveButton.performClick();
                    }
            );
            nameEditText.requestFocus();
        });
        dialog.setOnDismissListener(ignored -> {
            yearEditText.setOnEditorActionListener(null);
            if (registrationDialog == dialog) {
                registrationDialog = null;
            }
        });
        dialog.show();
    }

    private void saveClassFromDialog(
            AlertDialog dialog,
            @Nullable OmrClassGroup existingClass,
            TextInputLayout nameLayout,
            TextInputEditText nameEditText,
            TextInputLayout gradeLayout,
            TextInputEditText gradeEditText,
            TextInputLayout yearLayout,
            TextInputEditText yearEditText
    ) {
        String name = textOf(nameEditText);
        String grade = textOf(gradeEditText);
        Integer year = positiveIntegerOrNull(textOf(yearEditText));
        boolean nameValid = !name.trim().isEmpty();
        boolean gradeValid = !grade.trim().isEmpty();

        nameLayout.setError(nameValid
                ? null
                : getString(R.string.omr_class_registration_error_name));
        gradeLayout.setError(gradeValid
                ? null
                : getString(R.string.omr_class_registration_error_grade));
        yearLayout.setError(year != null
                ? null
                : getString(R.string.omr_class_registration_error_year));

        if (!nameValid || !gradeValid || year == null) {
            if (!nameValid) {
                nameEditText.requestFocus();
            } else if (!gradeValid) {
                gradeEditText.requestFocus();
            } else {
                yearEditText.requestFocus();
            }
            return;
        }

        try {
            registrationService.registerOrUpdateClass(
                    existingClass == null
                            ? null
                            : existingClass.getClassGroupId(),
                    name,
                    grade,
                    year
            );
            dialog.dismiss();
            reloadRepository();
            Toast.makeText(
                    this,
                    existingClass == null
                            ? R.string.omr_class_registration_created
                            : R.string.omr_class_registration_updated,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao salvar turma.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_class_registration_save_error,
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
                OmrClassRosterCsvParser.Preview preview =
                        csvParser.parse(bytes);
                postToUi(() -> showCsvPreview(preview));
            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_class_csv_too_large
                ));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao ler CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_class_csv_read_error
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
                        "O provedor nao abriu o arquivo selecionado."
                );
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
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

    void showCsvPreview(OmrClassRosterCsvParser.Preview preview) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A previa do CSV e obrigatoria."
            );
        }
        viewBinder.setBusy(false);
        dismissCsvPreviewDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(preview.isReadyToImport()
                        ? R.string.omr_class_csv_preview_title
                        : R.string.omr_class_csv_preview_error_title)
                .setMessage(formatCsvPreview(preview));
        if (preview.isReadyToImport()) {
            builder.setNegativeButton(
                    R.string.omr_class_csv_action_cancel, null
            );
            builder.setPositiveButton(
                    R.string.omr_class_csv_action_import,
                    (dialog, which) -> importCsvPreview(preview)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_class_csv_action_close, null
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
            OmrClassRosterCsvParser.Preview preview
    ) {
        if (preview.isReadyToImport()) {
            int rowCount = preview.getRows().size();
            Set<String> classKeys = new LinkedHashSet<>();
            for (OmrClassRosterCsvParser.Row row : preview.getRows()) {
                classKeys.add(row.getClassName().toLowerCase()
                        + "\u0000" + row.getSchoolYear());
            }

            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals.omr_class_csv_preview_valid_rows,
                            rowCount,
                            rowCount
                    )
            );
            message.append("\n")
                    .append(getResources().getQuantityString(
                            R.plurals.omr_class_csv_preview_class_count,
                            classKeys.size(),
                            classKeys.size()
                    ));

            int visibleCount = Math.min(rowCount, MAX_PREVIEW_ROWS);
            for (int index = 0; index < visibleCount; index++) {
                OmrClassRosterCsvParser.Row row =
                        preview.getRows().get(index);
                message.append("\n\n• ").append(getString(
                        R.string.omr_class_csv_preview_row,
                        row.getClassName(),
                        row.getRosterPosition(),
                        row.getRegistration(),
                        row.getStudentName()
                ));
            }
            int hiddenCount = rowCount - visibleCount;
            if (hiddenCount > 0) {
                message.append("\n\n").append(
                        getResources().getQuantityString(
                                R.plurals.omr_class_csv_preview_more_rows,
                                hiddenCount,
                                hiddenCount
                        )
                );
            }
            message.append("\n\n")
                    .append(getString(
                            R.string.omr_class_csv_preview_replace_note
                    ))
                    .append("\n\n")
                    .append(getString(
                            R.string.omr_class_csv_preview_technical,
                            preview.getEncoding(),
                            String.valueOf(preview.getDelimiter())
                    ));
            return message.toString();
        }

        List<OmrClassRosterCsvParser.Issue> issues = preview.getIssues();
        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals.omr_class_csv_preview_error_summary,
                        issues.size(),
                        issues.size()
                )
        );
        int visibleCount = Math.min(issues.size(), MAX_PREVIEW_ISSUES);
        for (int index = 0; index < visibleCount; index++) {
            message.append("\n\n• ").append(issues.get(index));
        }
        int hiddenCount = issues.size() - visibleCount;
        if (hiddenCount > 0) {
            message.append("\n\n").append(
                    getResources().getQuantityString(
                            R.plurals.omr_class_csv_preview_more_errors,
                            hiddenCount,
                            hiddenCount
                    )
            );
        }
        return message.toString();
    }

    private void importCsvPreview(
            OmrClassRosterCsvParser.Preview preview
    ) {
        viewBinder.setBusy(true);
        backgroundExecutor.execute(() -> {
            try {
                OmrClassRosterRegistrationService.ImportSummary summary =
                        registrationService.importPreview(preview);
                postToUi(() -> {
                    viewBinder.setBusy(false);
                    reloadRepository();
                    Toast.makeText(
                            this,
                            getString(
                                    R.string.omr_class_csv_import_success,
                                    summary.getProcessedClassCount(),
                                    summary.getProcessedStudentCount(),
                                    summary.getEnrollmentCount()
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao importar CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_class_csv_import_error
                ));
            }
        });
    }

    private void showBackgroundError(int messageResource) {
        viewBinder.setBusy(false);
        Toast.makeText(this, messageResource, Toast.LENGTH_LONG).show();
    }

    private void postToUi(Runnable action) {
        runOnUiThread(() -> {
            if (!released) {
                action.run();
            }
        });
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString();
    }

    private static Integer positiveIntegerOrNull(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
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
        if (batchRepository != null) {
            batchRepository.close();
            batchRepository = null;
        }
        if (studentRepository != null) {
            studentRepository.close();
            studentRepository = null;
        }
        if (enrollmentRepository != null) {
            enrollmentRepository.close();
            enrollmentRepository = null;
        }
        if (classRepository != null) {
            classRepository.close();
            classRepository = null;
        }
        registrationService = null;
        csvParser = null;
        classCatalog = Collections.emptyList();
        studentCounts = Collections.emptyMap();
        super.onDestroy();
    }

    private static final class CsvTooLargeException extends Exception {
    }
}
