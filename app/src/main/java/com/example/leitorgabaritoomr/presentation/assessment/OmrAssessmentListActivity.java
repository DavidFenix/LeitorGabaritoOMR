package com.example.leitorgabaritoomr.presentation.assessment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCsvParser;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentRegistrationService;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentStructureCsvParser;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentStructureRegistrationService;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyRepository;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentBatchRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentStructureRepository;
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

/** Cadastro local e versionado de provas, manualmente ou por CSV. */
public final class OmrAssessmentListActivity extends AppCompatActivity {

    private static final String TAG = "OmrAssessmentList";
    private static final int MAX_CSV_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PREVIEW_ROWS = 10;
    private static final int MAX_PREVIEW_ISSUES = 10;

    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra."
                    + "ASSESSMENT_DATABASE_NAME";
    private static final String EXTRA_ANSWER_KEY_NAMESPACE =
            "com.example.leitorgabaritoomr.extra."
                    + "ASSESSMENT_ANSWER_KEY_NAMESPACE";

    private static final String[] CSV_MIME_TYPES = {
            "text/csv",
            "text/comma-separated-values",
            "application/csv",
            "text/plain",
            "application/vnd.ms-excel"
    };

    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteAssessmentBatchRepository batchRepository;
    private OmrSQLiteAssessmentStructureRepository structureRepository;
    private OmrAnswerKeyRepository answerKeyRepository;
    private OmrAssessmentRegistrationService registrationService;
    private OmrAssessmentCsvParser csvParser;
    private OmrAssessmentStructureCsvParser structureCsvParser;
    private OmrAssessmentStructureRegistrationService
            structureRegistrationService;
    private OmrAssessmentListViewBinder viewBinder;
    private ExecutorService backgroundExecutor;
    private List<OmrAssessmentDefinition> assessmentCatalog =
            Collections.emptyList();
    private List<OmrAssessmentStructure> structureCatalog =
            Collections.emptyList();

    private AlertDialog registrationDialog;
    private AlertDialog noAnswerKeysDialog;
    private AlertDialog csvPreviewDialog;
    private AlertDialog structureCsvPreviewDialog;
    private AlertDialog structureDialog;
    private volatile boolean released;

    private final ActivityResultLauncher<String[]> csvDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::handleCsvDocument
            );

    private final ActivityResultLauncher<String[]> blocksCsvDocumentLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::handleBlocksCsvDocument
            );

    public static Intent createIntent(Context context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        return new Intent(context, OmrAssessmentListActivity.class);
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String databaseName,
            String answerKeyNamespace
    ) {
        if (databaseName == null
                || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco isolado é obrigatório."
            );
        }
        if (answerKeyNamespace == null
                || answerKeyNamespace.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O namespace isolado dos gabaritos é obrigatório."
            );
        }

        return createIntent(context)
                .putExtra(EXTRA_DATABASE_NAME, databaseName.trim())
                .putExtra(
                        EXTRA_ANSWER_KEY_NAMESPACE,
                        answerKeyNamespace.trim()
                );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_omr_assessment_list);

        String databaseName = getIntent().getStringExtra(
                EXTRA_DATABASE_NAME
        );
        if (databaseName == null || databaseName.trim().isEmpty()) {
            assessmentRepository =
                    new OmrSQLiteAssessmentRepository(this);
            batchRepository =
                    new OmrSQLiteAssessmentBatchRepository(this);
            structureRepository =
                    new OmrSQLiteAssessmentStructureRepository(this);
        } else {
            assessmentRepository = new OmrSQLiteAssessmentRepository(
                    this,
                    databaseName.trim()
            );
            batchRepository = new OmrSQLiteAssessmentBatchRepository(
                    this,
                    databaseName.trim()
            );
            structureRepository =
                    new OmrSQLiteAssessmentStructureRepository(
                            this,
                            databaseName.trim()
                    );
        }

        answerKeyRepository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        createAnswerKeyRepositoryContext()
                );
        registrationService = new OmrAssessmentRegistrationService(
                assessmentRepository,
                batchRepository,
                answerKeyRepository
        );
        structureRegistrationService =
                new OmrAssessmentStructureRegistrationService(
                        assessmentRepository,
                        structureRepository,
                        structureRepository,
                        answerKeyRepository
                );
        csvParser = new OmrAssessmentCsvParser();
        structureCsvParser = new OmrAssessmentStructureCsvParser();
        backgroundExecutor = Executors.newSingleThreadExecutor();
        viewBinder = new OmrAssessmentListViewBinder(
                findViewById(android.R.id.content)
        );

        configureBinder();
        reloadRepository();
    }

    private Context createAnswerKeyRepositoryContext() {
        String namespace = getIntent().getStringExtra(
                EXTRA_ANSWER_KEY_NAMESPACE
        );

        if (namespace == null || namespace.trim().isEmpty()) {
            return this;
        }

        Context baseContext = this;
        String suffix = "." + namespace.trim();

        return new ContextWrapper(baseContext) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(
                    String name,
                    int mode
            ) {
                return baseContext.getSharedPreferences(
                        name + suffix,
                        mode
                );
            }
        };
    }

    private void configureBinder() {
        viewBinder.setOnSearchQueryChangedListener(this::renderQuery);
        viewBinder.setOnIdentifierCopyListener(this::copyIdentifier);
        viewBinder.setOnAssessmentActionListener(
                new OmrAssessmentListViewBinder
                        .OnAssessmentActionListener() {
                    @Override
                    public void onCreateNewVersion(
                            String assessmentId
                    ) {
                        showRevisionDialog(assessmentId);
                    }

                    @Override
                    public void onManageStructure(
                            String assessmentId
                    ) {
                        showStructureDialog(assessmentId);
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
        viewBinder.setOnImportBlocksCsvClickListener(
                view -> blocksCsvDocumentLauncher.launch(CSV_MIME_TYPES)
        );
    }

    private void copyIdentifier(String identifier) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE
        );
        if (clipboard == null) {
            Toast.makeText(
                    this,
                    R.string.omr_assessment_identifier_copy_error,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText(
                "ID OMR",
                identifier
        ));
        Toast.makeText(
                this,
                R.string.omr_assessment_identifier_copied,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void reloadRepository() {
        try {
            assessmentCatalog = new ArrayList<>(
                    assessmentRepository.loadLatestVersions()
            );
            List<OmrAssessmentStructure> loadedStructures =
                    new ArrayList<>();
            for (OmrAssessmentDefinition assessment : assessmentCatalog) {
                OmrAssessmentStructure structure = structureRepository
                        .findStoredOrNull(
                                assessment.getAssessmentId(),
                                assessment.getVersion()
                        );
                if (structure != null) {
                    loadedStructures.add(structure);
                }
            }
            structureCatalog = loadedStructures;
            renderQuery(viewBinder.getSearchQuery());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar provas.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_assessment_list_load_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderQuery(String query) {
        viewBinder.render(OmrAssessmentListViewState.from(
                assessmentCatalog,
                structureCatalog,
                query
        ));
    }

    private void showRevisionDialog(String assessmentId) {
        OmrAssessmentDefinition current = assessmentRepository
                .findLatestVersionOrNull(assessmentId);

        if (current == null) {
            Toast.makeText(
                    this,
                    R.string.omr_assessment_registration_missing,
                    Toast.LENGTH_LONG
            ).show();
            reloadRepository();
            return;
        }

        showRegistrationDialog(current);
    }

    private void showRegistrationDialog(
            @Nullable OmrAssessmentDefinition current
    ) {
        if (registrationDialog != null
                && registrationDialog.isShowing()) {
            return;
        }

        List<OmrAnswerKeyDefinition> answerKeys;
        try {
            answerKeys = new ArrayList<>(answerKeyRepository.loadAll());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar gabaritos.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_assessment_registration_save_error,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (answerKeys.isEmpty()) {
            showNoAnswerKeysDialog();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_omr_assessment_registration,
                null,
                false
        );

        TextView versionText = dialogView.findViewById(
                R.id.textOmrAssessmentVersionDialog
        );
        TextInputLayout titleLayout = dialogView.findViewById(
                R.id.inputLayoutOmrAssessmentTitleDialog
        );
        TextInputEditText titleEdit = dialogView.findViewById(
                R.id.editOmrAssessmentTitleDialog
        );
        TextInputLayout subjectLayout = dialogView.findViewById(
                R.id.inputLayoutOmrAssessmentSubjectDialog
        );
        TextInputEditText subjectEdit = dialogView.findViewById(
                R.id.editOmrAssessmentSubjectDialog
        );
        TextInputLayout gradeLayout = dialogView.findViewById(
                R.id.inputLayoutOmrAssessmentGradeDialog
        );
        TextInputEditText gradeEdit = dialogView.findViewById(
                R.id.editOmrAssessmentGradeDialog
        );
        TextInputLayout periodLayout = dialogView.findViewById(
                R.id.inputLayoutOmrAssessmentPeriodDialog
        );
        TextInputEditText periodEdit = dialogView.findViewById(
                R.id.editOmrAssessmentPeriodDialog
        );
        TextInputLayout yearLayout = dialogView.findViewById(
                R.id.inputLayoutOmrAssessmentYearDialog
        );
        TextInputEditText yearEdit = dialogView.findViewById(
                R.id.editOmrAssessmentYearDialog
        );
        Spinner answerKeySpinner = dialogView.findViewById(
                R.id.spinnerOmrAssessmentAnswerKeyDialog
        );

        List<AnswerKeyOption> options = new ArrayList<>();
        for (OmrAnswerKeyDefinition answerKey : answerKeys) {
            options.add(new AnswerKeyOption(
                    answerKey,
                    getString(
                            R.string
                                    .omr_assessment_registration_answer_key_item,
                            answerKey.getName(),
                            answerKey.getId(),
                            answerKey.getVersion(),
                            answerKey.getQuestionCount()
                    )
            ));
        }

        ArrayAdapter<AnswerKeyOption> spinnerAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.item_omr_assessment_answer_key_option,
                        R.id.textOmrAssessmentAnswerKeyOption,
                        options
                );
        spinnerAdapter.setDropDownViewResource(
                R.layout.item_omr_assessment_answer_key_option
        );
        answerKeySpinner.setAdapter(spinnerAdapter);

        boolean revising = current != null;
        if (revising) {
            versionText.setText(getString(
                    R.string.omr_assessment_registration_next_version,
                    current.getVersion() + 1
            ));
            titleEdit.setText(current.getTitle());
            subjectEdit.setText(current.getSubject());
            gradeEdit.setText(current.getGradeLevel());
            periodEdit.setText(current.getAcademicPeriod());
            yearEdit.setText(Integer.toString(current.getSchoolYear()));

            for (int index = 0; index < options.size(); index++) {
                OmrAnswerKeyDefinition candidate =
                        options.get(index).answerKey;
                if (candidate.getId().equals(
                        current.getAnswerKeyId()
                ) && candidate.getVersion()
                        == current.getAnswerKeyVersion()) {
                    answerKeySpinner.setSelection(index);
                    break;
                }
            }
        } else {
            versionText.setText(
                    R.string.omr_assessment_registration_version_one
            );
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(revising
                        ? R.string.omr_assessment_registration_revision_title
                        : R.string.omr_assessment_registration_create_title)
                .setView(dialogView)
                .setNegativeButton(
                        R.string.omr_assessment_registration_action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_assessment_registration_action_save,
                        null
                )
                .create();

        registrationDialog = dialog;
        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );
            saveButton.setOnClickListener(view -> saveFromDialog(
                    dialog,
                    current,
                    titleLayout,
                    titleEdit,
                    subjectLayout,
                    subjectEdit,
                    gradeLayout,
                    gradeEdit,
                    periodLayout,
                    periodEdit,
                    yearLayout,
                    yearEdit,
                    answerKeySpinner
            ));
            yearEdit.setOnEditorActionListener(
                    (view, actionId, event) -> {
                        if (actionId != EditorInfo.IME_ACTION_DONE) {
                            return false;
                        }
                        return saveButton.performClick();
                    }
            );
            titleEdit.requestFocus();
            titleEdit.setSelection(titleEdit.length());
        });
        dialog.setOnDismissListener(ignored -> {
            yearEdit.setOnEditorActionListener(null);
            if (registrationDialog == dialog) {
                registrationDialog = null;
            }
        });
        dialog.show();
    }

    private void showNoAnswerKeysDialog() {
        if (noAnswerKeysDialog != null
                && noAnswerKeysDialog.isShowing()) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        R.string
                                .omr_assessment_registration_no_answer_keys_title
                )
                .setMessage(
                        R.string
                                .omr_assessment_registration_no_answer_keys_message
                )
                .setPositiveButton(
                        R.string
                                .omr_assessment_registration_no_answer_keys_close,
                        null
                )
                .create();
        noAnswerKeysDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (noAnswerKeysDialog == dialog) {
                noAnswerKeysDialog = null;
            }
        });
        dialog.show();
    }

    private void saveFromDialog(
            AlertDialog dialog,
            @Nullable OmrAssessmentDefinition current,
            TextInputLayout titleLayout,
            TextInputEditText titleEdit,
            TextInputLayout subjectLayout,
            TextInputEditText subjectEdit,
            TextInputLayout gradeLayout,
            TextInputEditText gradeEdit,
            TextInputLayout periodLayout,
            TextInputEditText periodEdit,
            TextInputLayout yearLayout,
            TextInputEditText yearEdit,
            Spinner answerKeySpinner
    ) {
        String title = textOf(titleEdit);
        String subject = textOf(subjectEdit);
        String grade = textOf(gradeEdit);
        String period = textOf(periodEdit);
        Integer year = positiveIntegerOrNull(textOf(yearEdit));

        boolean titleValid = !title.trim().isEmpty();
        boolean subjectValid = !subject.trim().isEmpty();
        boolean gradeValid = !grade.trim().isEmpty();
        boolean periodValid = !period.trim().isEmpty();

        titleLayout.setError(titleValid
                ? null
                : getString(
                R.string.omr_assessment_registration_error_title
        ));
        subjectLayout.setError(subjectValid
                ? null
                : getString(
                R.string.omr_assessment_registration_error_subject
        ));
        gradeLayout.setError(gradeValid
                ? null
                : getString(
                R.string.omr_assessment_registration_error_grade
        ));
        periodLayout.setError(periodValid
                ? null
                : getString(
                R.string.omr_assessment_registration_error_period
        ));
        yearLayout.setError(year != null
                ? null
                : getString(
                R.string.omr_assessment_registration_error_year
        ));

        if (!titleValid || !subjectValid || !gradeValid
                || !periodValid || year == null) {
            if (!titleValid) {
                titleEdit.requestFocus();
            } else if (!subjectValid) {
                subjectEdit.requestFocus();
            } else if (!gradeValid) {
                gradeEdit.requestFocus();
            } else if (!periodValid) {
                periodEdit.requestFocus();
            } else {
                yearEdit.requestFocus();
            }
            return;
        }

        AnswerKeyOption option =
                (AnswerKeyOption) answerKeySpinner.getSelectedItem();
        if (option == null) {
            Toast.makeText(
                    this,
                    R.string.omr_assessment_registration_save_error,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
            if (current == null) {
                registrationService.registerNew(
                        title,
                        subject,
                        grade,
                        period,
                        year,
                        option.answerKey.getId(),
                        option.answerKey.getVersion()
                );
            } else {
                registrationService.registerNewVersion(
                        current.getAssessmentId(),
                        title,
                        subject,
                        grade,
                        period,
                        year,
                        option.answerKey.getId(),
                        option.answerKey.getVersion()
                );
            }

            dialog.dismiss();
            reloadRepository();
            Toast.makeText(
                    this,
                    current == null
                            ? R.string.omr_assessment_registration_created
                            : R.string.omr_assessment_registration_revised,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao salvar prova.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_assessment_registration_save_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showStructureDialog(String assessmentId) {
        if (structureDialog != null && structureDialog.isShowing()) {
            return;
        }

        OmrAssessmentDefinition assessment = assessmentRepository
                .findLatestVersionOrNull(assessmentId);
        if (assessment == null) {
            Toast.makeText(
                    this,
                    R.string.omr_assessment_registration_missing,
                    Toast.LENGTH_LONG
            ).show();
            reloadRepository();
            return;
        }

        OmrAssessmentStructure stored = structureRepository
                .findStoredOrNull(
                        assessment.getAssessmentId(),
                        assessment.getVersion()
                );
        if (stored != null) {
            showPublishedStructureDialog(assessment, stored);
            return;
        }

        OmrAnswerKeyDefinition answerKey = answerKeyRepository.findOrNull(
                assessment.getAnswerKeyId(),
                assessment.getAnswerKeyVersion()
        );
        if (answerKey == null) {
            Toast.makeText(
                    this,
                    R.string
                            .omr_assessment_structure_missing_answer_key,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_omr_assessment_structure,
                null,
                false
        );
        TextView target = dialogView.findViewById(
                R.id.textOmrAssessmentStructureTarget
        );
        LinearLayout blocksContainer = dialogView.findViewById(
                R.id.containerOmrAssessmentStructureBlocks
        );
        Button addBlock = dialogView.findViewById(
                R.id.buttonOmrAssessmentStructureAddBlock
        );
        TextView total = dialogView.findViewById(
                R.id.textOmrAssessmentStructureTotal
        );
        List<BlockEditorRow> rows = new ArrayList<>();
        int expectedQuestionCount = answerKey.getQuestionCount();

        target.setText(getString(
                R.string.omr_assessment_structure_editor_target,
                assessment.getTitle(),
                assessment.getVersion(),
                expectedQuestionCount
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.omr_assessment_structure_editor_title)
                .setView(dialogView)
                .setNegativeButton(
                        R.string.omr_assessment_structure_action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_assessment_structure_action_publish,
                        null
                )
                .create();
        structureDialog = dialog;

        addBlock.setOnClickListener(view -> addBlockEditorRow(
                blocksContainer,
                rows,
                total,
                expectedQuestionCount
        ));
        addBlockEditorRow(
                blocksContainer,
                rows,
                total,
                expectedQuestionCount
        );

        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(view -> publishStructure(
                dialog,
                assessment,
                rows,
                total,
                expectedQuestionCount
        )));
        dialog.setOnDismissListener(ignored -> {
            for (BlockEditorRow row : rows) {
                row.release();
            }
            rows.clear();
            addBlock.setOnClickListener(null);
            if (structureDialog == dialog) {
                structureDialog = null;
            }
        });
        dialog.show();
    }

    private void showPublishedStructureDialog(
            OmrAssessmentDefinition assessment,
            OmrAssessmentStructure structure
    ) {
        StringBuilder message = new StringBuilder(getString(
                R.string.omr_assessment_structure_read_only_header,
                assessment.getTitle(),
                assessment.getVersion()
        ));

        for (OmrAssessmentBlock block : structure.getBlocks()) {
            String area = block.hasAreaName()
                    ? getString(
                    R.string.omr_assessment_structure_read_only_area,
                    block.getAreaName()
            )
                    : "";
            message.append("\n\n").append(getString(
                    R.string.omr_assessment_structure_read_only_row,
                    block.getOrder(),
                    block.getComponentName(),
                    area,
                    block.getFirstQuestionPosition(),
                    block.getLastQuestionPosition(),
                    block.getQuestionCount()
            ));
        }
        message.append("\n\n").append(getString(
                R.string.omr_assessment_structure_read_only_policy
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(
                        R.string.omr_assessment_structure_read_only_title
                )
                .setMessage(message)
                .setPositiveButton(
                        R.string.omr_assessment_structure_action_close,
                        null
                )
                .create();
        structureDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (structureDialog == dialog) {
                structureDialog = null;
            }
        });
        dialog.show();
    }

    private void addBlockEditorRow(
            LinearLayout container,
            List<BlockEditorRow> rows,
            TextView total,
            int expectedQuestionCount
    ) {
        View rowView = LayoutInflater.from(this).inflate(
                R.layout.item_omr_assessment_structure_editor,
                container,
                false
        );
        BlockEditorRow row = new BlockEditorRow(rowView, () ->
                updateStructureEditor(
                        rows,
                        total,
                        expectedQuestionCount
                )
        );
        rows.add(row);
        container.addView(rowView);
        row.removeButton.setOnClickListener(view -> {
            if (rows.size() <= 1) {
                return;
            }
            row.release();
            rows.remove(row);
            container.removeView(rowView);
            updateStructureEditor(
                    rows,
                    total,
                    expectedQuestionCount
            );
        });
        updateStructureEditor(rows, total, expectedQuestionCount);
    }

    private void updateStructureEditor(
            List<BlockEditorRow> rows,
            TextView total,
            int expectedQuestionCount
    ) {
        long informed = 0L;
        for (int index = 0; index < rows.size(); index++) {
            BlockEditorRow row = rows.get(index);
            row.order.setText(getString(
                    R.string.omr_assessment_structure_block_order,
                    index + 1
            ));
            row.removeButton.setEnabled(rows.size() > 1);
            Integer count = positiveIntegerOrNull(
                    textOf(row.questionCount)
            );
            if (count != null) {
                informed += count;
            }
        }

        int safeTotal = informed > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) informed;
        total.setText(getString(
                R.string.omr_assessment_structure_total,
                safeTotal,
                expectedQuestionCount
        ));
        total.setTextColor(safeTotal == expectedQuestionCount
                ? 0xFF047857
                : 0xFFB45309);
    }

    private void publishStructure(
            AlertDialog dialog,
            OmrAssessmentDefinition assessment,
            List<BlockEditorRow> rows,
            TextView total,
            int expectedQuestionCount
    ) {
        List<OmrAssessmentStructureRegistrationService.BlockInput> inputs =
                new ArrayList<>();
        boolean valid = true;
        long informedTotal = 0L;
        View firstInvalid = null;

        for (int index = 0; index < rows.size(); index++) {
            BlockEditorRow row = rows.get(index);
            String component = textOf(row.component);
            Integer count = positiveIntegerOrNull(
                    textOf(row.questionCount)
            );
            boolean componentValid = !component.trim().isEmpty();

            row.componentLayout.setError(componentValid
                    ? null
                    : getString(
                    R.string.omr_assessment_structure_error_component
            ));
            row.questionCountLayout.setError(count != null
                    ? null
                    : getString(
                    R.string
                            .omr_assessment_structure_error_question_count
            ));

            if (!componentValid || count == null) {
                valid = false;
                if (firstInvalid == null) {
                    firstInvalid = componentValid
                            ? row.questionCount
                            : row.component;
                }
                continue;
            }

            informedTotal += count;
            inputs.add(new OmrAssessmentStructureRegistrationService
                    .BlockInput(
                    index + 1,
                    null,
                    textOf(row.area),
                    null,
                    component,
                    count
            ));
        }

        if (!valid) {
            firstInvalid.requestFocus();
            return;
        }
        if (informedTotal != expectedQuestionCount) {
            total.setText(getString(
                    R.string.omr_assessment_structure_total_mismatch,
                    expectedQuestionCount
            ));
            total.setTextColor(0xFFB91C1C);
            return;
        }

        try {
            structureRegistrationService.register(
                    assessment.getAssessmentId(),
                    assessment.getVersion(),
                    inputs
            );
            dialog.dismiss();
            reloadRepository();
            Toast.makeText(
                    this,
                    R.string.omr_assessment_structure_created,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao publicar blocos.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_assessment_structure_save_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void handleBlocksCsvDocument(@Nullable Uri documentUri) {
        if (documentUri == null || released) {
            return;
        }

        viewBinder.setBusy(true);
        backgroundExecutor.execute(() -> {
            try {
                byte[] bytes = readCsvBytes(documentUri);
                OmrAssessmentStructureCsvParser.Preview preview =
                        structureCsvParser.parse(bytes);
                postToUi(() -> showBlocksCsvPreview(preview));
            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV de blocos acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_csv_too_large
                ));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao ler CSV de blocos.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_csv_read_error
                ));
            }
        });
    }

    void showBlocksCsvPreview(
            OmrAssessmentStructureCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A previa do CSV de blocos e obrigatoria."
            );
        }

        viewBinder.setBusy(false);
        dismissStructureCsvPreviewDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(preview.isReadyToImport()
                        ? R.string
                        .omr_assessment_blocks_csv_preview_title
                        : R.string
                        .omr_assessment_blocks_csv_preview_error_title)
                .setMessage(formatBlocksCsvPreview(preview));

        if (preview.isReadyToImport()) {
            builder.setNegativeButton(
                    R.string.omr_assessment_csv_action_cancel,
                    null
            );
            builder.setPositiveButton(
                    R.string.omr_assessment_blocks_csv_action_import,
                    (dialog, which) -> importBlocksCsvPreview(preview)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_assessment_csv_action_close,
                    null
            );
        }

        AlertDialog dialog = builder.create();
        structureCsvPreviewDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (structureCsvPreviewDialog == dialog) {
                structureCsvPreviewDialog = null;
            }
        });
        dialog.show();
    }

    private String formatBlocksCsvPreview(
            OmrAssessmentStructureCsvParser.Preview preview
    ) {
        if (preview.isReadyToImport()) {
            int rowCount = preview.getRows().size();
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_assessment_blocks_csv_preview_valid_rows,
                            rowCount,
                            rowCount
                    )
            );
            int visibleCount = Math.min(rowCount, MAX_PREVIEW_ROWS);
            for (int index = 0; index < visibleCount; index++) {
                OmrAssessmentStructureCsvParser.Row row =
                        preview.getRows().get(index);
                message.append("\n\n• ").append(getString(
                        R.string.omr_assessment_blocks_csv_preview_row,
                        row.getAssessmentReference(),
                        row.getAssessmentVersion(),
                        row.getBlockOrder(),
                        row.getComponentName(),
                        row.getQuestionCount()
                ));
            }
            int hiddenCount = rowCount - visibleCount;
            if (hiddenCount > 0) {
                message.append("\n\n… +").append(hiddenCount);
            }
            message.append("\n\n").append(getString(
                    R.string
                            .omr_assessment_blocks_csv_preview_safety_note
            )).append("\n\n").append(getString(
                    R.string
                            .omr_assessment_blocks_csv_preview_technical,
                    preview.getEncoding(),
                    String.valueOf(preview.getDelimiter())
            ));
            return message.toString();
        }

        List<OmrAssessmentStructureCsvParser.Issue> issues =
                preview.getIssues();
        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals
                                .omr_assessment_blocks_csv_preview_error_summary,
                        issues.size(),
                        issues.size()
                )
        );
        int visibleCount = Math.min(issues.size(), MAX_PREVIEW_ISSUES);
        for (int index = 0; index < visibleCount; index++) {
            OmrAssessmentStructureCsvParser.Issue issue =
                    issues.get(index);
            message.append("\n\n• ").append(getString(
                    R.string.omr_assessment_blocks_csv_preview_issue,
                    issue.getLineNumber(),
                    issue.getMessage()
            ));
        }
        int hiddenCount = issues.size() - visibleCount;
        if (hiddenCount > 0) {
            message.append("\n\n… +").append(hiddenCount);
        }
        return message.toString();
    }

    private void importBlocksCsvPreview(
            OmrAssessmentStructureCsvParser.Preview preview
    ) {
        viewBinder.setBusy(true);
        backgroundExecutor.execute(() -> {
            try {
                OmrAssessmentStructureRegistrationService.ImportSummary
                        summary = structureRegistrationService
                        .importPreview(preview);
                postToUi(() -> {
                    viewBinder.setBusy(false);
                    reloadRepository();
                    Toast.makeText(
                            this,
                            getString(
                                    R.string
                                            .omr_assessment_blocks_csv_import_success,
                                    summary.getCreatedStructureCount(),
                                    summary.getUnchangedStructureCount(),
                                    summary.getProcessedBlockCount()
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao importar blocos.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_blocks_csv_import_error
                ));
            }
        });
    }

    private void handleCsvDocument(@Nullable Uri documentUri) {
        if (documentUri == null || released) {
            return;
        }

        viewBinder.setBusy(true);
        backgroundExecutor.execute(() -> {
            try {
                byte[] bytes = readCsvBytes(documentUri);
                OmrAssessmentCsvParser.Preview preview =
                        csvParser.parse(bytes);
                postToUi(() -> showCsvPreview(preview));
            } catch (CsvTooLargeException exception) {
                Log.w(TAG, "CSV acima do limite.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_csv_too_large
                ));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao ler CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_csv_read_error
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

    void showCsvPreview(OmrAssessmentCsvParser.Preview preview) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A prévia do CSV é obrigatória."
            );
        }

        viewBinder.setBusy(false);
        dismissCsvPreviewDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(preview.isReadyToImport()
                        ? R.string.omr_assessment_csv_preview_title
                        : R.string.omr_assessment_csv_preview_error_title)
                .setMessage(formatCsvPreview(preview));

        if (preview.isReadyToImport()) {
            builder.setNegativeButton(
                    R.string.omr_assessment_csv_action_cancel,
                    null
            );
            builder.setPositiveButton(
                    R.string.omr_assessment_csv_action_import,
                    (dialog, which) -> importCsvPreview(preview)
            );
        } else {
            builder.setPositiveButton(
                    R.string.omr_assessment_csv_action_close,
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
            OmrAssessmentCsvParser.Preview preview
    ) {
        if (preview.isReadyToImport()) {
            int rowCount = preview.getRows().size();
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals.omr_assessment_csv_preview_valid_rows,
                            rowCount,
                            rowCount
                    )
            );

            int visibleCount = Math.min(rowCount, MAX_PREVIEW_ROWS);
            for (int index = 0; index < visibleCount; index++) {
                OmrAssessmentCsvParser.Row row =
                        preview.getRows().get(index);
                message.append("\n\n• ").append(getString(
                        R.string.omr_assessment_csv_preview_row,
                        row.getAssessmentCode(),
                        row.getAssessmentVersion(),
                        row.getTitle()
                ));
            }

            int hiddenCount = rowCount - visibleCount;
            if (hiddenCount > 0) {
                message.append("\n\n").append(
                        getResources().getQuantityString(
                                R.plurals
                                        .omr_assessment_csv_preview_more_rows,
                                hiddenCount,
                                hiddenCount
                        )
                );
            }

            message.append("\n\n")
                    .append(getString(
                            R.string.omr_assessment_csv_preview_safety_note
                    ))
                    .append("\n\n")
                    .append(getString(
                            R.string.omr_assessment_csv_preview_technical,
                            preview.getEncoding(),
                            String.valueOf(preview.getDelimiter())
                    ));
            return message.toString();
        }

        List<OmrAssessmentCsvParser.Issue> issues =
                preview.getIssues();
        StringBuilder message = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals.omr_assessment_csv_preview_error_summary,
                        issues.size(),
                        issues.size()
                )
        );
        int visibleCount = Math.min(
                issues.size(),
                MAX_PREVIEW_ISSUES
        );
        for (int index = 0; index < visibleCount; index++) {
            message.append("\n\n• ").append(issues.get(index));
        }
        int hiddenCount = issues.size() - visibleCount;
        if (hiddenCount > 0) {
            message.append("\n\n").append(
                    getResources().getQuantityString(
                            R.plurals
                                    .omr_assessment_csv_preview_more_errors,
                            hiddenCount,
                            hiddenCount
                    )
            );
        }
        return message.toString();
    }

    private void importCsvPreview(
            OmrAssessmentCsvParser.Preview preview
    ) {
        viewBinder.setBusy(true);
        backgroundExecutor.execute(() -> {
            try {
                OmrAssessmentRegistrationService.ImportSummary summary =
                        registrationService.importPreview(preview);
                postToUi(() -> {
                    viewBinder.setBusy(false);
                    reloadRepository();
                    Toast.makeText(
                            this,
                            getString(
                                    R.string
                                            .omr_assessment_csv_import_success,
                                    summary.getCreatedVersionCount(),
                                    summary.getUnchangedVersionCount()
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao importar CSV.", exception);
                postToUi(() -> showBackgroundError(
                        R.string.omr_assessment_csv_import_error
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

    @Nullable
    private static Integer positiveIntegerOrNull(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void dismissCsvPreviewDialog() {
        if (csvPreviewDialog != null) {
            csvPreviewDialog.dismiss();
            csvPreviewDialog = null;
        }
    }

    private void dismissStructureCsvPreviewDialog() {
        if (structureCsvPreviewDialog != null) {
            structureCsvPreviewDialog.dismiss();
            structureCsvPreviewDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        released = true;

        if (registrationDialog != null) {
            registrationDialog.dismiss();
            registrationDialog = null;
        }
        if (noAnswerKeysDialog != null) {
            noAnswerKeysDialog.dismiss();
            noAnswerKeysDialog = null;
        }
        dismissCsvPreviewDialog();
        dismissStructureCsvPreviewDialog();
        if (structureDialog != null) {
            structureDialog.dismiss();
            structureDialog = null;
        }

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
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
        if (structureRepository != null) {
            structureRepository.close();
            structureRepository = null;
        }

        registrationService = null;
        structureRegistrationService = null;
        answerKeyRepository = null;
        csvParser = null;
        structureCsvParser = null;
        assessmentCatalog = Collections.emptyList();
        structureCatalog = Collections.emptyList();
        super.onDestroy();
    }

    private static final class AnswerKeyOption {
        private final OmrAnswerKeyDefinition answerKey;
        private final String label;

        private AnswerKeyOption(
                OmrAnswerKeyDefinition answerKey,
                String label
        ) {
            this.answerKey = answerKey;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class BlockEditorRow {
        private final TextView order;
        private final TextInputEditText area;
        private final TextInputLayout componentLayout;
        private final TextInputEditText component;
        private final TextInputLayout questionCountLayout;
        private final TextInputEditText questionCount;
        private final Button removeButton;
        private final TextWatcher countWatcher;

        private BlockEditorRow(View root, Runnable onCountChanged) {
            order = root.findViewById(
                    R.id.textOmrAssessmentStructureBlockOrder
            );
            area = root.findViewById(
                    R.id.editOmrAssessmentStructureArea
            );
            componentLayout = root.findViewById(
                    R.id.inputLayoutOmrAssessmentStructureComponent
            );
            component = root.findViewById(
                    R.id.editOmrAssessmentStructureComponent
            );
            questionCountLayout = root.findViewById(
                    R.id.inputLayoutOmrAssessmentStructureQuestionCount
            );
            questionCount = root.findViewById(
                    R.id.editOmrAssessmentStructureQuestionCount
            );
            removeButton = root.findViewById(
                    R.id.buttonOmrAssessmentStructureRemoveBlock
            );
            countWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence value,
                        int start,
                        int count,
                        int after
                ) {
                    // Nenhuma acao necessaria.
                }

                @Override
                public void onTextChanged(
                        CharSequence value,
                        int start,
                        int before,
                        int count
                ) {
                    onCountChanged.run();
                }

                @Override
                public void afterTextChanged(Editable value) {
                    // O total ja foi atualizado.
                }
            };
            questionCount.addTextChangedListener(countWatcher);
        }

        private void release() {
            questionCount.removeTextChangedListener(countWatcher);
            removeButton.setOnClickListener(null);
        }
    }

    private static final class CsvTooLargeException extends Exception {
    }
}
