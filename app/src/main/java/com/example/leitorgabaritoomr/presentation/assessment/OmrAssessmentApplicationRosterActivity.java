package com.example.leitorgabaritoomr.presentation.assessment;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionContext;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionResolver;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReportService;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.infrastructure.grading.OmrSharedPreferencesAnswerKeyRepository;
import com.example.leitorgabaritoomr.infrastructure.history.OmrSQLiteGradingHistoryRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;
import com.example.leitorgabaritoomr.presentation.capture.OmrCaptureActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exibe a chamada congelada e coordena suas correções e revisões. */
public final class OmrAssessmentApplicationRosterActivity
        extends AppCompatActivity {

    private static final String TAG = "OmrApplicationRoster";
    private static final String EXTRA_APPLICATION_ID =
            "com.example.leitorgabaritoomr.extra."
                    + "ROSTER_APPLICATION_ID";
    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra."
                    + "ROSTER_DATABASE_NAME";
    private static final String EXTRA_ANSWER_KEY_NAMESPACE =
            "com.example.leitorgabaritoomr.extra."
                    + "ROSTER_ANSWER_KEY_NAMESPACE";
    private static final String STATE_BATCH_CORRECTION_ACTIVE =
            "omr.application.roster.batch.active";

    private OmrSQLiteAssessmentApplicationRepository applicationRepository;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteAssessmentParticipantRepository participantRepository;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrSQLiteGradingHistoryRepository historyRepository;
    private OmrSharedPreferencesAnswerKeyRepository answerKeyRepository;
    private OmrAssessmentCorrectionResolver correctionResolver;
    private OmrAssessmentApplicationRosterViewBinder viewBinder;
    private OmrAssessmentApplication application;
    private OmrAssessmentDefinition assessment;
    private List<OmrAssessmentParticipant> participants =
            Collections.emptyList();
    private List<OmrAssessmentAttempt> attempts = Collections.emptyList();
    private Set<String> compatibleHistoryRecordIds = Collections.emptySet();
    private OmrAssessmentApplicationRosterViewState currentState;
    private AlertDialog revisionDialog;
    private boolean batchCorrectionActive;
    private String activeCorrectionStudentName;

    private final ActivityResultLauncher<Intent> correctionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::onCorrectionFinished
            );

    public static Intent createIntent(
            Context context,
            String applicationId
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }
        String normalizedId = requireApplicationId(applicationId);
        return new Intent(
                context,
                OmrAssessmentApplicationRosterActivity.class
        ).putExtra(EXTRA_APPLICATION_ID, normalizedId);
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String applicationId,
            String databaseName
    ) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O nome do banco isolado é obrigatório."
            );
        }
        return createIntent(context, applicationId).putExtra(
                EXTRA_DATABASE_NAME,
                databaseName.trim()
        );
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String applicationId,
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
                applicationId,
                databaseName
        ).putExtra(
                EXTRA_ANSWER_KEY_NAMESPACE,
                answerKeyNamespace.trim()
        );
    }

    @Nullable
    public static String extractApplicationId(Intent intent) {
        if (intent == null) {
            return null;
        }
        String value = intent.getStringExtra(EXTRA_APPLICATION_ID);
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(
                R.layout.activity_omr_assessment_application_roster
        );
        viewBinder = new OmrAssessmentApplicationRosterViewBinder(
                findViewById(android.R.id.content)
        );
        viewBinder.setOnBackClickListener(view -> finish());
        viewBinder.setOnSearchQueryChangedListener(this::renderQuery);
        viewBinder.setOnParticipantCorrectionListener(
                this::onParticipantCorrectionRequested
        );
        viewBinder.setOnNextPendingClickListener(
                view -> startNextPendingCorrection()
        );
        viewBinder.setOnResultsClickListener(view -> openResults());

        batchCorrectionActive = savedInstanceState != null
                && savedInstanceState.getBoolean(
                STATE_BATCH_CORRECTION_ACTIVE,
                false
        );

        String databaseName = getIntent().getStringExtra(
                EXTRA_DATABASE_NAME
        );
        if (databaseName == null || databaseName.trim().isEmpty()) {
            applicationRepository =
                    new OmrSQLiteAssessmentApplicationRepository(this);
            assessmentRepository = new OmrSQLiteAssessmentRepository(this);
            participantRepository =
                    new OmrSQLiteAssessmentParticipantRepository(this);
            attemptRepository =
                    new OmrSQLiteAssessmentAttemptRepository(this);
            historyRepository =
                    new OmrSQLiteGradingHistoryRepository(this);
        } else {
            String normalized = databaseName.trim();
            applicationRepository =
                    new OmrSQLiteAssessmentApplicationRepository(
                            this, normalized
                    );
            assessmentRepository = new OmrSQLiteAssessmentRepository(
                    this, normalized
            );
            participantRepository =
                    new OmrSQLiteAssessmentParticipantRepository(
                            this, normalized
                    );
            attemptRepository =
                    new OmrSQLiteAssessmentAttemptRepository(
                            this, normalized
                    );
        }

        answerKeyRepository =
                new OmrSharedPreferencesAnswerKeyRepository(
                        createAnswerKeyRepositoryContext()
                );
        correctionResolver = new OmrAssessmentCorrectionResolver(
                applicationRepository,
                assessmentRepository,
                participantRepository,
                answerKeyRepository
        );
        loadRoster();
    }

    private void loadRoster() {
        currentState = null;
        String applicationId = extractApplicationId(getIntent());
        if (applicationId == null) {
            showMissingAndFinish();
            return;
        }
        try {
            application = applicationRepository.findByIdOrNull(
                    applicationId
            );
            if (application == null) {
                showMissingAndFinish();
                return;
            }
            assessment = assessmentRepository.findOrNull(
                    application.getAssessmentId(),
                    application.getAssessmentVersion()
            );
            participants = participantRepository.loadForApplication(
                    applicationId
            );
            attempts = attemptRepository.loadForApplication(applicationId);
            if (assessment == null || participants.isEmpty()) {
                showMissingAndFinish();
                return;
            }
            compatibleHistoryRecordIds =
                    loadCompatibleHistoryRecordIds();
            renderQuery(viewBinder == null
                    ? ""
                    : viewBinder.getSearchQuery());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Falha ao carregar chamada.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_application_roster_load_error,
                    Toast.LENGTH_LONG
            ).show();
            finish();
        }
    }

    private void renderQuery(String query) {
        if (application == null || assessment == null) {
            return;
        }
        currentState = createViewState(query);
        viewBinder.render(currentState);
    }

    private OmrAssessmentApplicationRosterViewState createViewState(
            String query
    ) {
        if (historyRepository == null) {
            return OmrAssessmentApplicationRosterViewState.from(
                    application,
                    assessment,
                    participants,
                    attempts,
                    query
            );
        }
        return OmrAssessmentApplicationRosterViewState.from(
                application,
                assessment,
                participants,
                attempts,
                compatibleHistoryRecordIds,
                query
        );
    }

    private Set<String> loadCompatibleHistoryRecordIds() {
        if (historyRepository == null) {
            return Collections.emptySet();
        }
        Set<String> compatible = new HashSet<>();
        for (OmrAssessmentAttempt attempt : attempts) {
            OmrAssessmentParticipant participant =
                    findParticipantOrNull(attempt.getStudentId());
            if (participant == null) {
                continue;
            }
            try {
                OmrGradingHistoryRecord history =
                        historyRepository.findByIdOrNull(
                                attempt.getHistoryRecordId()
                        );
                if (OmrAssessmentResultReportService.isCompatible(
                        history,
                        participant,
                        assessment
                )) {
                    compatible.add(attempt.getHistoryRecordId());
                }
            } catch (RuntimeException exception) {
                Log.w(
                        TAG,
                        "Histórico incompatível ou ilegível: "
                                + attempt.getHistoryRecordId(),
                        exception
                );
            }
        }
        return Collections.unmodifiableSet(compatible);
    }

    @Nullable
    private OmrAssessmentParticipant findParticipantOrNull(
            String studentId
    ) {
        for (OmrAssessmentParticipant participant : participants) {
            if (participant.belongsToStudent(studentId)) {
                return participant;
            }
        }
        return null;
    }

    private void openResults() {
        if (application == null) {
            return;
        }
        String databaseName = normalizedDatabaseName();
        Intent intent = databaseName == null
                ? OmrAssessmentResultActivity.createIntent(
                this,
                application.getApplicationId()
        )
                : OmrAssessmentResultActivity.createIsolatedStorageIntent(
                this,
                application.getApplicationId(),
                databaseName
        );
        startActivity(intent);
    }

    private void onParticipantCorrectionRequested(String studentId) {
        OmrAssessmentApplicationRosterViewState.ParticipantItem item =
                currentState == null
                        ? null
                        : currentState.findVisibleParticipantOrNull(studentId);

        if (item == null) {
            return;
        }

        batchCorrectionActive = false;

        if (!item.isCorrected()) {
            launchCorrection(item);
            return;
        }

        dismissRevisionDialog();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.omr_application_roster_revision_title)
                .setMessage(getString(
                        R.string.omr_application_roster_revision_message,
                        item.getStudentName(),
                        item.getLatestRevision()
                ))
                .setNegativeButton(
                        R.string.omr_application_roster_revision_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.omr_application_roster_revision_confirm,
                        (ignored, which) -> launchCorrection(item)
                )
                .create();
        revisionDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (revisionDialog == dialog) {
                revisionDialog = null;
            }
        });
        dialog.show();
    }

    private void startNextPendingCorrection() {
        if (currentState == null) {
            return;
        }

        String studentId = currentState.getFirstPendingStudentIdOrNull();
        if (studentId == null) {
            batchCorrectionActive = false;
            Toast.makeText(
                    this,
                    R.string.omr_application_roster_batch_complete,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        OmrAssessmentApplicationRosterViewState.ParticipantItem item =
                findParticipantItem(studentId);
        if (item == null) {
            return;
        }

        batchCorrectionActive = true;
        launchCorrection(item);
    }

    @Nullable
    private OmrAssessmentApplicationRosterViewState.ParticipantItem
    findParticipantItem(String studentId) {
        OmrAssessmentApplicationRosterViewState unfiltered =
                createViewState("");
        return unfiltered.findVisibleParticipantOrNull(studentId);
    }

    private void launchCorrection(
            OmrAssessmentApplicationRosterViewState.ParticipantItem item
    ) {
        try {
            OmrAssessmentCorrectionContext context =
                    correctionResolver.resolve(
                            application.getApplicationId(),
                            item.getStudentId()
                    );

            activeCorrectionStudentName = item.getStudentName();
            viewBinder.setBusy(true);

            correctionLauncher.launch(
                    OmrCaptureActivity.createAssessmentIntent(
                            this,
                            context,
                            normalizedDatabaseName()
                    )
            );

        } catch (RuntimeException exception) {
            batchCorrectionActive = false;
            activeCorrectionStudentName = null;
            viewBinder.setBusy(false);
            Log.e(TAG, "Falha ao preparar correção.", exception);
            Toast.makeText(
                    this,
                    R.string.omr_application_roster_correction_unavailable,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void onCorrectionFinished(ActivityResult result) {
        if (viewBinder == null) {
            return;
        }

        viewBinder.setBusy(false);
        String correctedStudentName = activeCorrectionStudentName;
        activeCorrectionStudentName = null;
        loadRoster();

        if (isFinishing() || currentState == null) {
            batchCorrectionActive = false;
            return;
        }

        if (result.getResultCode() != Activity.RESULT_OK) {
            batchCorrectionActive = false;
            return;
        }

        if (batchCorrectionActive
                && currentState != null
                && currentState.getRemainingCount() > 0) {

            Toast.makeText(
                    this,
                    R.string.omr_application_roster_batch_next,
                    Toast.LENGTH_SHORT
            ).show();
            startNextPendingCorrection();
            return;
        }

        if (batchCorrectionActive) {
            batchCorrectionActive = false;
            Toast.makeText(
                    this,
                    R.string.omr_application_roster_batch_complete,
                    Toast.LENGTH_LONG
            ).show();
        } else if (correctedStudentName != null) {
            Toast.makeText(
                    this,
                    getString(
                            R.string.omr_application_roster_correction_saved,
                            correctedStudentName
                    ),
                    Toast.LENGTH_SHORT
            ).show();
        }
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

    @Nullable
    private String normalizedDatabaseName() {
        String value = getIntent().getStringExtra(EXTRA_DATABASE_NAME);
        return value == null || value.trim().isEmpty()
                ? null
                : value.trim();
    }

    private void showMissingAndFinish() {
        Toast.makeText(
                this,
                R.string.omr_application_roster_missing,
                Toast.LENGTH_LONG
        ).show();
        finish();
    }

    private static String requireApplicationId(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O identificador da aplicação é obrigatório."
            );
        }
        return applicationId.trim();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(
                STATE_BATCH_CORRECTION_ACTIVE,
                batchCorrectionActive
        );
        super.onSaveInstanceState(outState);
    }

    private void dismissRevisionDialog() {
        if (revisionDialog != null) {
            revisionDialog.dismiss();
            revisionDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissRevisionDialog();
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }
        if (attemptRepository != null) {
            attemptRepository.close();
            attemptRepository = null;
        }
        if (historyRepository != null) {
            historyRepository.close();
            historyRepository = null;
        }
        if (participantRepository != null) {
            participantRepository.close();
            participantRepository = null;
        }
        if (assessmentRepository != null) {
            assessmentRepository.close();
            assessmentRepository = null;
        }
        if (applicationRepository != null) {
            applicationRepository.close();
            applicationRepository = null;
        }
        application = null;
        assessment = null;
        answerKeyRepository = null;
        correctionResolver = null;
        currentState = null;
        participants = Collections.emptyList();
        attempts = Collections.emptyList();
        compatibleHistoryRecordIds = Collections.emptySet();
        activeCorrectionStudentName = null;
        super.onDestroy();
    }
}
