package com.example.leitorgabaritoomr.presentation.assessment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;

import java.util.Collections;
import java.util.List;

/** Consulta somente de leitura da chamada congelada de uma aplicação. */
public final class OmrAssessmentApplicationRosterActivity
        extends AppCompatActivity {

    private static final String TAG = "OmrApplicationRoster";
    private static final String EXTRA_APPLICATION_ID =
            "com.example.leitorgabaritoomr.extra."
                    + "ROSTER_APPLICATION_ID";
    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra."
                    + "ROSTER_DATABASE_NAME";

    private OmrSQLiteAssessmentApplicationRepository applicationRepository;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteAssessmentParticipantRepository participantRepository;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrAssessmentApplicationRosterViewBinder viewBinder;
    private OmrAssessmentApplication application;
    private OmrAssessmentDefinition assessment;
    private List<OmrAssessmentParticipant> participants =
            Collections.emptyList();
    private List<OmrAssessmentAttempt> attempts = Collections.emptyList();

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
        loadRoster();
    }

    private void loadRoster() {
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
            renderQuery("");
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
        viewBinder.render(OmrAssessmentApplicationRosterViewState.from(
                application,
                assessment,
                participants,
                attempts,
                query
        ));
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
    protected void onDestroy() {
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }
        if (attemptRepository != null) {
            attemptRepository.close();
            attemptRepository = null;
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
        participants = Collections.emptyList();
        attempts = Collections.emptyList();
        super.onDestroy();
    }
}
