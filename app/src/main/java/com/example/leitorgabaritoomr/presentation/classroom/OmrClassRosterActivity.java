package com.example.leitorgabaritoomr.presentation.classroom;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.classroom.OmrClassRosterRegistrationService;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;
import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassEnrollmentRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassGroupRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteClassRosterBatchRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteStudentRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exibe e edita a lista de chamada de uma turma. */
public final class OmrClassRosterActivity extends AppCompatActivity {

    private static final String TAG = "OmrClassRoster";
    private static final String EXTRA_CLASS_GROUP_ID =
            "com.example.leitorgabaritoomr.extra.CLASS_GROUP_ID";
    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra.ROSTER_DATABASE_NAME";

    private OmrSQLiteClassGroupRepository classRepository;
    private OmrSQLiteClassEnrollmentRepository enrollmentRepository;
    private OmrSQLiteStudentRepository studentRepository;
    private OmrSQLiteClassRosterBatchRepository batchRepository;
    private OmrClassRosterRegistrationService registrationService;
    private OmrClassRosterViewBinder viewBinder;

    private String classGroupId;
    private OmrClassGroup currentClass;
    private List<OmrClassEnrollment> enrollmentCatalog =
            Collections.emptyList();
    private List<OmrStudentProfile> studentCatalog =
            Collections.emptyList();
    private AlertDialog enrollmentDialog;

    public static Intent createIntent(
            Context context,
            String classGroupId
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto e obrigatorio."
            );
        }
        if (classGroupId == null || classGroupId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "A turma e obrigatoria."
            );
        }
        return new Intent(context, OmrClassRosterActivity.class)
                .putExtra(EXTRA_CLASS_GROUP_ID, classGroupId.trim());
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String classGroupId,
            String databaseName
    ) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco isolado e obrigatorio."
            );
        }
        return createIntent(context, classGroupId).putExtra(
                EXTRA_DATABASE_NAME,
                databaseName.trim()
        );
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_omr_class_roster);

        classGroupId = getIntent().getStringExtra(EXTRA_CLASS_GROUP_ID);
        if (classGroupId == null || classGroupId.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.omr_class_roster_missing,
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }
        classGroupId = classGroupId.trim();

        createDependencies();
        viewBinder = new OmrClassRosterViewBinder(
                findViewById(android.R.id.content)
        );
        configureBinder();
        reloadRepository();
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
    }

    private void configureBinder() {
        viewBinder.setOnSearchQueryChangedListener(this::renderQuery);
        viewBinder.setOnEnrollmentActionListener(
                this::showEditEnrollmentDialog
        );
        viewBinder.setOnBackClickListener(view -> finish());
        viewBinder.setOnEnrollClickListener(
                view -> showEnrollmentDialog(null, null)
        );
    }

    private void reloadRepository() {
        try {
            currentClass = classRepository.findByIdOrNull(classGroupId);
            if (currentClass == null) {
                Toast.makeText(
                        this,
                        R.string.omr_class_roster_missing,
                        Toast.LENGTH_LONG
                ).show();
                finish();
                return;
            }
            enrollmentCatalog = new ArrayList<>(
                    enrollmentRepository.loadForClassGroup(classGroupId)
            );
            studentCatalog = new ArrayList<>(studentRepository.loadAll());
            renderQuery(viewBinder.getSearchQuery());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar lista da turma.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_class_roster_load_error,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderQuery(String query) {
        if (currentClass == null) {
            return;
        }
        viewBinder.render(OmrClassRosterViewState.from(
                currentClass,
                enrollmentCatalog,
                studentCatalog,
                query
        ));
    }

    private void showEditEnrollmentDialog(String studentId) {
        OmrClassEnrollment enrollment = enrollmentRepository.findOrNull(
                classGroupId, studentId
        );
        OmrStudentProfile student = studentRepository.findByIdOrNull(
                studentId
        );
        if (enrollment == null || student == null) {
            Toast.makeText(
                    this,
                    R.string.omr_class_roster_student_missing,
                    Toast.LENGTH_LONG
            ).show();
            reloadRepository();
            return;
        }
        showEnrollmentDialog(student, enrollment);
    }

    private void showEnrollmentDialog(
            @Nullable OmrStudentProfile existingStudent,
            @Nullable OmrClassEnrollment existingEnrollment
    ) {
        if (enrollmentDialog != null && enrollmentDialog.isShowing()) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_omr_class_enrollment,
                null,
                false
        );
        TextInputLayout registrationLayout = dialogView.findViewById(
                R.id.inputLayoutOmrClassEnrollmentRegistrationDialog
        );
        TextInputEditText registrationEditText = dialogView.findViewById(
                R.id.editOmrClassEnrollmentRegistrationDialog
        );
        TextInputLayout positionLayout = dialogView.findViewById(
                R.id.inputLayoutOmrClassEnrollmentPositionDialog
        );
        TextInputEditText positionEditText = dialogView.findViewById(
                R.id.editOmrClassEnrollmentPositionDialog
        );

        boolean editing = existingStudent != null
                && existingEnrollment != null;
        if (editing) {
            registrationEditText.setText(
                    existingStudent.getRegistration()
            );
            registrationEditText.setEnabled(false);
            positionEditText.setText(Integer.toString(
                    existingEnrollment.getRosterPosition()
            ));
            positionEditText.setSelection(positionEditText.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing
                        ? R.string.omr_class_enrollment_edit_title
                        : R.string.omr_class_enrollment_create_title)
                .setView(dialogView)
                .setNegativeButton(
                        R.string.omr_class_enrollment_action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_class_enrollment_action_save,
                        null
                )
                .create();
        enrollmentDialog = dialog;

        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            );
            saveButton.setOnClickListener(view -> saveEnrollmentFromDialog(
                    dialog,
                    existingStudent,
                    existingEnrollment,
                    registrationLayout,
                    registrationEditText,
                    positionLayout,
                    positionEditText
            ));
            positionEditText.setOnEditorActionListener(
                    (view, actionId, event) -> {
                        if (actionId != EditorInfo.IME_ACTION_DONE) {
                            return false;
                        }
                        return saveButton.performClick();
                    }
            );
            if (editing) {
                positionEditText.requestFocus();
            } else {
                registrationEditText.requestFocus();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            positionEditText.setOnEditorActionListener(null);
            if (enrollmentDialog == dialog) {
                enrollmentDialog = null;
            }
        });
        dialog.show();
    }

    private void saveEnrollmentFromDialog(
            AlertDialog dialog,
            @Nullable OmrStudentProfile existingStudent,
            @Nullable OmrClassEnrollment existingEnrollment,
            TextInputLayout registrationLayout,
            TextInputEditText registrationEditText,
            TextInputLayout positionLayout,
            TextInputEditText positionEditText
    ) {
        String registration = textOf(registrationEditText);
        Integer position = positiveIntegerOrNull(textOf(positionEditText));
        boolean registrationValid = !registration.trim().isEmpty();

        registrationLayout.setError(registrationValid
                ? null
                : getString(
                R.string.omr_class_enrollment_error_registration
        ));
        positionLayout.setError(position != null
                ? null
                : getString(R.string.omr_class_enrollment_error_position));

        if (!registrationValid || position == null) {
            if (!registrationValid) {
                registrationEditText.requestFocus();
            } else {
                positionEditText.requestFocus();
            }
            return;
        }

        OmrStudentProfile student = existingStudent == null
                ? studentRepository.findByRegistrationOrNull(registration)
                : existingStudent;
        if (student == null) {
            registrationLayout.setError(getString(
                    R.string.omr_class_enrollment_student_not_found
            ));
            registrationEditText.requestFocus();
            return;
        }

        boolean updating = existingEnrollment != null
                || enrollmentRepository.findOrNull(
                classGroupId, student.getStudentId()
        ) != null;
        try {
            registrationService.enrollStudent(
                    classGroupId,
                    student.getStudentId(),
                    position
            );
            dialog.dismiss();
            reloadRepository();
            Toast.makeText(
                    this,
                    updating
                            ? R.string.omr_class_enrollment_updated
                            : R.string.omr_class_enrollment_created,
                    Toast.LENGTH_SHORT
            ).show();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao salvar matricula.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_class_enrollment_save_error,
                    Toast.LENGTH_LONG
            ).show();
        }
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

    @Override
    protected void onDestroy() {
        if (enrollmentDialog != null) {
            enrollmentDialog.dismiss();
            enrollmentDialog = null;
        }
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
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
        currentClass = null;
        enrollmentCatalog = Collections.emptyList();
        studentCatalog = Collections.emptyList();
        super.onDestroy();
    }
}
