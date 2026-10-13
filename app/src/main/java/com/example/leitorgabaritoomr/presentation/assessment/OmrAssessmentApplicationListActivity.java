package com.example.leitorgabaritoomr.presentation.assessment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationRegistrationService;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentApplicationCsvParser;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationBatchRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassEnrollmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lista, cria e importa aplicações de prova com chamada congelada. */
public final class OmrAssessmentApplicationListActivity
        extends AppCompatActivity {

    private static final String TAG = "OmrApplicationList";
    private static final int MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PREVIEW_ROWS = 10;
    private static final int MAX_PREVIEW_ISSUES = 10;
    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra."
                    + "APPLICATION_DATABASE_NAME";
    private static final String EXTRA_ANSWER_KEY_NAMESPACE =
            "com.example.leitorgabaritoomr.extra."
                    + "APPLICATION_ANSWER_KEY_NAMESPACE";
    private static final String[] CSV_MIME_TYPES = {
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel"
    };

    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrSQLiteAssessmentApplicationRepository applicationRepository;
    private OmrSQLiteAssessmentParticipantRepository participantRepository;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrSQLiteAssessmentApplicationBatchRepository batchRepository;
    private OmrAssessmentApplicationRegistrationService registrationService;
    private OmrAssessmentApplicationCsvParser csvParser;
    private OmrAssessmentApplicationListViewBinder viewBinder;
    private ExecutorService backgroundExecutor;

    private List<OmrAssessmentApplication> applicationCatalog =
            Collections.emptyList();
    private List<OmrAssessmentDefinition> assessmentCatalog =
            Collections.emptyList();
    private List<OmrClassGroup> classCatalog = Collections.emptyList();
    private List<OmrAssessmentParticipant> participantCatalog =
            Collections.emptyList();
    private List<OmrAssessmentAttempt> attemptCatalog =
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
        return new Intent(
                context,
                OmrAssessmentApplicationListActivity.class
        );
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String databaseName
    ) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco isolado é obrigatório."
            );
        }
        return createIntent(context).putExtra(
                EXTRA_DATABASE_NAME,
                databaseName.trim()
        );
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String databaseName,
            String answerKeyNamespace
    ) {
        if (answerKeyNamespace == null
                || answerKeyNamespace.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O namespace isolado dos gabaritos é obrigatório."
            );
        }

        return createIsolatedStorageIntent(
                context,
                databaseName
        ).putExtra(
                EXTRA_ANSWER_KEY_NAMESPACE,
                answerKeyNamespace.trim()
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(
                R.layout.activity_omr_assessment_application_list
        );

        String databaseName = normalizedDatabaseName();
        if (databaseName == null) {
            assessmentRepository = new OmrSQLiteAssessmentRepository(this);
            classRepository = new OmrSQLiteClassGroupRepository(this);
            enrollmentRepository =
                    new OmrSQLiteClassEnrollmentRepository(this);
            studentRepository = new OmrSQLiteStudentRepository(this);
            applicationRepository =
                    new OmrSQLiteAssessmentApplicationRepository(this);
            participantRepository =
                    new OmrSQLiteAssessmentParticipantRepository(this);
            attemptRepository =
                    new OmrSQLiteAssessmentAttemptRepository(this);
            batchRepository =
                    new OmrSQLiteAssessmentApplicationBatchRepository(this);
        } else {
            assessmentRepository = new OmrSQLiteAssessmentRepository(
                    this, databaseName
            );
            classRepository = new OmrSQLiteClassGroupRepository(
                    this, databaseName
            );
            enrollmentRepository =
                    new OmrSQLiteClassEnrollmentRepository(
                            this, databaseName
                    );
            studentRepository = new OmrSQLiteStudentRepository(
                    this, databaseName
            );
            applicationRepository =
                    new OmrSQLiteAssessmentApplicationRepository(
                            this, databaseName
                    );
            participantRepository =
                    new OmrSQLiteAssessmentParticipantRepository(
                            this, databaseName
                    );
            attemptRepository =
                    new OmrSQLiteAssessmentAttemptRepository(
                            this, databaseName
                    );
            batchRepository =
                    new OmrSQLiteAssessmentApplicationBatchRepository(
                            this, databaseName
                    );
        }

        registrationService =
                new OmrAssessmentApplicationRegistrationService(
                        assessmentRepository,
                        classRepository,
                        enrollmentRepository,
                        studentRepository,
                        applicationRepository,
                        participantRepository,
                        batchRepository
                );
        csvParser = new OmrAssessmentApplicationCsvParser();
        backgroundExecutor = Executors.newSingleThreadExecutor();
        viewBinder = new OmrAssessmentApplicationListViewBinder(
                findViewById(android.R.id.content)
        );
        configureBinder();
    }

    private void configureBinder() {
        viewBinder.setOnSearchQueryChangedListener(this::renderQuery);
        viewBinder.setOnApplicationActionListener(this::openFrozenRoster);
        viewBinder.setOnResultActionListener(this::openResults);
        viewBinder.setOnBackClickListener(view -> finish());
        viewBinder.setOnCreateClickListener(
                view -> showRegistrationDialog()
        );
        viewBinder.setOnImportCsvClickListener(
                view -> csvDocumentLauncher.launch(CSV_MIME_TYPES)
        );
    }

    private void reloadRepositories() {
        try {
            applicationCatalog = new ArrayList<>(
                    applicationRepository.loadAll()
            );
            assessmentCatalog = new ArrayList<>(
                    assessmentRepository.loadAll()
            );
            classCatalog = new ArrayList<>(classRepository.loadAll());
            List<OmrAssessmentParticipant> participants =
                    new ArrayList<>();
            List<OmrAssessmentAttempt> attempts = new ArrayList<>();
            for (OmrAssessmentApplication application
                    : applicationCatalog) {
                participants.addAll(participantRepository
                        .loadForApplication(application.getApplicationId()));
                attempts.addAll(attemptRepository
                        .loadForApplication(application.getApplicationId()));
            }
            participantCatalog = participants;
            attemptCatalog = attempts;
            renderQuery(viewBinder.getSearchQuery());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar aplicações.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_assessment_application_list_load_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderQuery(String query) {
        viewBinder.render(OmrAssessmentApplicationListViewState.from(
                applicationCatalog,
                assessmentCatalog,
                participantCatalog,
                attemptCatalog,
                query
        ));
    }

    private void openFrozenRoster(String applicationId) {
        Intent intent;
        String databaseName = normalizedDatabaseName();
        if (databaseName == null) {
            intent = OmrAssessmentApplicationRosterActivity.createIntent(
                    this, applicationId
            );
        } else {
            String answerKeyNamespace =
                    normalizedAnswerKeyNamespace();
            intent = answerKeyNamespace == null
                    ? OmrAssessmentApplicationRosterActivity
                    .createIsolatedStorageIntent(
                            this,
                            applicationId,
                            databaseName
                    )
                    : OmrAssessmentApplicationRosterActivity
                    .createIsolatedStorageIntent(
                            this,
                            applicationId,
                            databaseName,
                            answerKeyNamespace
                    );
        }
        startActivity(intent);
    }

    private void openResults(String applicationId) {
        Intent intent;
        String databaseName = normalizedDatabaseName();
        if (databaseName == null) {
            intent = OmrAssessmentResultActivity.createIntent(
                    this, applicationId
            );
        } else {
            intent = OmrAssessmentResultActivity
                    .createIsolatedStorageIntent(
                            this,
                            applicationId,
                            databaseName
                    );
        }
        startActivity(intent);
    }

    private void showRegistrationDialog() {
        if (registrationDialog != null
                && registrationDialog.isShowing()) {
            return;
        }
        if (assessmentCatalog.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string
                            .omr_assessment_application_registration_no_assessments,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Map<String, Integer> enrollmentCountByClass = new HashMap<>();
        for (OmrClassGroup classGroup : classCatalog) {
            enrollmentCountByClass.put(
                    classGroup.getClassGroupId(),
                    enrollmentRepository.loadForClassGroup(
                            classGroup.getClassGroupId()
                    ).size()
            );
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_omr_assessment_application_registration,
                null,
                false
        );
        Spinner assessmentSpinner = dialogView.findViewById(
                R.id.spinnerOmrAssessmentApplicationAssessment
        );
        Spinner classSpinner = dialogView.findViewById(
                R.id.spinnerOmrAssessmentApplicationClass
        );

        List<AssessmentOption> assessmentOptions = new ArrayList<>();
        for (OmrAssessmentDefinition assessment : assessmentCatalog) {
            assessmentOptions.add(new AssessmentOption(
                    assessment,
                    getString(
                            R.string
                                    .omr_assessment_application_registration_assessment_option,
                            assessment.getTitle(),
                            assessment.getSubject(),
                            assessment.getVersion(),
                            assessment.getGradeLevel(),
                            assessment.getSchoolYear()
                    )
            ));
        }
        ArrayAdapter<AssessmentOption> assessmentAdapter =
                optionAdapter(assessmentOptions);
        assessmentSpinner.setAdapter(assessmentAdapter);

        List<ClassOption> classOptions = new ArrayList<>();
        ArrayAdapter<ClassOption> classAdapter = optionAdapter(classOptions);
        classSpinner.setAdapter(classAdapter);

        Runnable updateClasses = () -> {
            AssessmentOption selected = (AssessmentOption)
                    assessmentSpinner.getSelectedItem();
            classOptions.clear();
            if (selected != null) {
                for (OmrClassGroup classGroup : classCatalog) {
                    int enrollmentCount = enrollmentCountByClass.get(
                            classGroup.getClassGroupId()
                    );
                    if (classGroup.getSchoolYear()
                            == selected.assessment.getSchoolYear()
                            && enrollmentCount > 0) {
                        classOptions.add(new ClassOption(
                                classGroup,
                                getString(
                                        R.string
                                                .omr_assessment_application_registration_class_option,
                                        classGroup.getName(),
                                        classGroup.getGradeLevel(),
                                        classGroup.getSchoolYear(),
                                        enrollmentCount
                                )
                        ));
                    }
                }
            }
            classAdapter.notifyDataSetChanged();
            classSpinner.setEnabled(!classOptions.isEmpty());
        };
        assessmentSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        updateClasses.run();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        updateClasses.run();
                    }
                }
        );
        updateClasses.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        R.string.omr_assessment_application_registration_title
                )
                .setView(dialogView)
                .setNegativeButton(
                        R.string
                                .omr_assessment_application_registration_action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string
                                .omr_assessment_application_registration_action_save,
                        null
                )
                .create();
        registrationDialog = dialog;
        dialog.setOnShowListener(ignored -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> saveFromDialog(
                    dialog,
                    assessmentSpinner,
                    classSpinner
            ));
        });
        dialog.setOnDismissListener(ignored -> {
            assessmentSpinner.setOnItemSelectedListener(null);
            if (registrationDialog == dialog) {
                registrationDialog = null;
            }
        });
        dialog.show();
    }

    private <T> ArrayAdapter<T> optionAdapter(List<T> options) {
        ArrayAdapter<T> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_omr_assessment_application_option,
                R.id.textOmrAssessmentApplicationOption,
                options
        );
        adapter.setDropDownViewResource(
                R.layout.item_omr_assessment_application_option
        );
        return adapter;
    }

    private void saveFromDialog(
            AlertDialog dialog,
            Spinner assessmentSpinner,
            Spinner classSpinner
    ) {
        AssessmentOption assessmentOption = (AssessmentOption)
                assessmentSpinner.getSelectedItem();
        ClassOption classOption = (ClassOption)
                classSpinner.getSelectedItem();
        if (assessmentOption == null) {
            Toast.makeText(
                    this,
                    R.string
                            .omr_assessment_application_registration_no_assessments,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        if (classOption == null) {
            Toast.makeText(
                    this,
                    R.string
                            .omr_assessment_application_registration_no_classes,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
            OmrAssessmentApplicationRegistrationService.RegistrationResult
                    result = registrationService.registerNew(
                    assessmentOption.assessment.getAssessmentId(),
                    assessmentOption.assessment.getVersion(),
                    classOption.classGroup.getClassGroupId()
            );
            dialog.dismiss();
            reloadRepositories();
            Toast.makeText(
                    this,
                    getString(
                            R.string
                                    .omr_assessment_application_registration_created,
                            result.getParticipants().size()
                    ),
                    Toast.LENGTH_LONG
            ).show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao criar aplicação.", exception);
            Toast.makeText(
                    this,
                    R.string
                            .omr_assessment_application_registration_save_error,
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
                OmrAssessmentApplicationCsvParser.Preview preview =
                        csvParser.parse(bytes);
                postToUi(() -> showCsvPreview(preview));
            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_application_csv_too_large
                ));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao ler CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_application_csv_read_error
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

    void showCsvPreview(
            OmrAssessmentApplicationCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A prévia do CSV é obrigatória."
            );
        }
        viewBinder.setBusy(false);
        dismissCsvPreviewDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(preview.isReadyToImport()
                        ? R.string
                        .omr_assessment_application_csv_preview_title
                        : R.string
                        .omr_assessment_application_csv_preview_error_title)
                .setMessage(formatCsvPreview(preview));
        if (preview.isReadyToImport()) {
            builder.setNegativeButton(
                    R.string.omr_assessment_application_csv_action_cancel,
                    null
            );
            builder.setPositiveButton(
                    R.string.omr_assessment_application_csv_action_import,
                    (dialog, which) -> importCsvPreview(preview)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_assessment_application_csv_action_close,
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
            OmrAssessmentApplicationCsvParser.Preview preview
    ) {
        if (preview.isReadyToImport()) {
            int rowCount = preview.getRows().size();
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_assessment_application_csv_preview_valid_rows,
                            rowCount,
                            rowCount
                    )
            );
            int visibleCount = Math.min(rowCount, MAX_PREVIEW_ROWS);
            for (int index = 0; index < visibleCount; index++) {
                OmrAssessmentApplicationCsvParser.Row row =
                        preview.getRows().get(index);
                message.append("\n\n• ").append(getString(
                        R.string
                                .omr_assessment_application_csv_preview_row,
                        row.getApplicationCode(),
                        row.getAssessmentReference(),
                        row.getAssessmentVersion(),
                        row.getClassReference()
                ));
            }
            int hiddenCount = rowCount - visibleCount;
            if (hiddenCount > 0) {
                message.append("\n\n").append(
                        getResources().getQuantityString(
                                R.plurals
                                        .omr_assessment_application_csv_preview_more_rows,
                                hiddenCount,
                                hiddenCount
                        )
                );
            }
            message.append("\n\n")
                    .append(getString(
                            R.string
                                    .omr_assessment_application_csv_preview_safety_note
                    ))
                    .append("\n\n")
                    .append(getString(
                            R.string
                                    .omr_assessment_application_csv_preview_technical,
                            preview.getEncoding(),
                            String.valueOf(preview.getDelimiter())
                    ));
            return message.toString();
        }

        List<OmrAssessmentApplicationCsvParser.Issue> issues =
                preview.getIssues();
        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals
                                .omr_assessment_application_csv_preview_error_summary,
                        issues.size(),
                        issues.size()
                )
        );
        int visibleCount = Math.min(
                issues.size(), MAX_PREVIEW_ISSUES
        );
        for (int index = 0; index < visibleCount; index++) {
            message.append("\n\n• ").append(issues.get(index));
        }
        int hiddenCount = issues.size() - visibleCount;
        if (hiddenCount > 0) {
            message.append("\n\n").append(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_assessment_application_csv_preview_more_errors,
                            hiddenCount,
                            hiddenCount
                    )
            );
        }
        return message.toString();
    }

    private void importCsvPreview(
            OmrAssessmentApplicationCsvParser.Preview preview
    ) {
        viewBinder.setBusy(true);
        backgroundExecutor.execute(() -> {
            try {
                OmrAssessmentApplicationRegistrationService.ImportSummary
                        summary = registrationService.importPreview(preview);
                postToUi(() -> {
                    viewBinder.setBusy(false);
                    reloadRepositories();
                    Toast.makeText(
                            this,
                            getString(
                                    R.string
                                            .omr_assessment_application_csv_import_success,
                                    summary.getCreatedApplicationCount(),
                                    summary.getUnchangedApplicationCount(),
                                    summary.getFrozenParticipantCount()
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao importar CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string
                                .omr_assessment_application_csv_import_error
                ));
            }
        });
    }

    private void showBackgroundError(int messageResource) {
        viewBinder.setBusy(false);
        Toast.makeText(
                this, messageResource, Toast.LENGTH_LONG
        ).show();
    }

    private void postToUi(Runnable action) {
        runOnUiThread(() -> {
            if (!released) {
                action.run();
            }
        });
    }

    @Nullable
    private String normalizedDatabaseName() {
        String value = getIntent().getStringExtra(EXTRA_DATABASE_NAME);
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }

    @Nullable
    private String normalizedAnswerKeyNamespace() {
        String value = getIntent().getStringExtra(
                EXTRA_ANSWER_KEY_NAMESPACE
        );
        return value == null || value.trim().isEmpty()
                ? null
                : value.trim();
    }

    private void dismissCsvPreviewDialog() {
        if (csvPreviewDialog != null) {
            csvPreviewDialog.dismiss();
            csvPreviewDialog = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewBinder != null && !released) {
            reloadRepositories();
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
        closeRepositories();
        registrationService = null;
        csvParser = null;
        applicationCatalog = Collections.emptyList();
        assessmentCatalog = Collections.emptyList();
        classCatalog = Collections.emptyList();
        participantCatalog = Collections.emptyList();
        attemptCatalog = Collections.emptyList();
        super.onDestroy();
    }

    private void closeRepositories() {
        if (batchRepository != null) {
            batchRepository.close();
            batchRepository = null;
        }
        if (attemptRepository != null) {
            attemptRepository.close();
            attemptRepository = null;
        }
        if (participantRepository != null) {
            participantRepository.close();
            participantRepository = null;
        }
        if (applicationRepository != null) {
            applicationRepository.close();
            applicationRepository = null;
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
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
    }

    private static final class AssessmentOption {
        private final OmrAssessmentDefinition assessment;
        private final String label;

        private AssessmentOption(
                OmrAssessmentDefinition assessment,
                String label
        ) {
            this.assessment = assessment;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class ClassOption {
        private final OmrClassGroup classGroup;
        private final String label;

        private ClassOption(
                OmrClassGroup classGroup,
                String label
        ) {
            this.classGroup = classGroup;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class CsvTooLargeException extends Exception {
    }
}
