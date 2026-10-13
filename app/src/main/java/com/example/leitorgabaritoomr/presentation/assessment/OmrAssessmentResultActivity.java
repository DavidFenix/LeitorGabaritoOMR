package com.example.leitorgabaritoomr.presentation.assessment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReportService;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultCsvExporter;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultExportNames;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultPdfExporter;
import com.example.leitorgabaritoomr.infrastructure.export.OmrAssessmentResultXlsxExporter;
import com.example.leitorgabaritoomr.infrastructure.history.OmrSQLiteGradingHistoryRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentApplicationRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentParticipantRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Consulta e exporta os resultados consolidados de uma aplicação. */
public final class OmrAssessmentResultActivity extends AppCompatActivity {

    private enum Format {
        CSV,
        XLSX,
        PDF
    }

    private static final String TAG = "OmrAssessmentResult";
    private static final String EXTRA_APPLICATION_ID =
            "com.example.leitorgabaritoomr.extra.RESULT_APPLICATION_ID";
    private static final String EXTRA_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra.RESULT_DATABASE_NAME";
    private static final String EXTRA_HISTORY_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra.RESULT_HISTORY_DATABASE_NAME";

    private OmrSQLiteAssessmentApplicationRepository applicationRepository;
    private OmrSQLiteAssessmentRepository assessmentRepository;
    private OmrSQLiteAssessmentParticipantRepository participantRepository;
    private OmrSQLiteAssessmentAttemptRepository attemptRepository;
    private OmrSQLiteGradingHistoryRepository historyRepository;
    private OmrAssessmentResultReportService reportService;
    private OmrAssessmentResultViewBinder viewBinder;
    private ExecutorService executor;
    private OmrAssessmentResultReport report;
    private volatile boolean released;

    private final ActivityResultLauncher<String> csvLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            OmrAssessmentResultExportNames.CSV_MIME_TYPE
                    ),
                    uri -> handleDestination(Format.CSV, uri)
            );

    private final ActivityResultLauncher<String> xlsxLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            OmrAssessmentResultExportNames.XLSX_MIME_TYPE
                    ),
                    uri -> handleDestination(Format.XLSX, uri)
            );

    private final ActivityResultLauncher<String> pdfLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            OmrAssessmentResultExportNames.PDF_MIME_TYPE
                    ),
                    uri -> handleDestination(Format.PDF, uri)
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
        return new Intent(context, OmrAssessmentResultActivity.class)
                .putExtra(
                        EXTRA_APPLICATION_ID,
                        requireApplicationId(applicationId)
                );
    }

    static Intent createIsolatedStorageIntent(
            Context context,
            String applicationId,
            String databaseName
    ) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O banco escolar isolado é obrigatório."
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
            String historyDatabaseName
    ) {
        if (databaseName == null || databaseName.trim().isEmpty()
                || historyDatabaseName == null
                || historyDatabaseName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Os bancos isolados são obrigatórios."
            );
        }
        return createIsolatedStorageIntent(
                context,
                applicationId,
                databaseName
        )
                .putExtra(
                        EXTRA_HISTORY_DATABASE_NAME,
                        historyDatabaseName.trim()
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
        setContentView(R.layout.activity_omr_assessment_result);
        viewBinder = new OmrAssessmentResultViewBinder(
                findViewById(android.R.id.content)
        );
        executor = Executors.newSingleThreadExecutor();
        configureBinder();
        configureRepositories();
        loadReport();
    }

    private void configureBinder() {
        viewBinder.setOnSearchQueryChangedListener(this::renderQuery);
        viewBinder.setOnCsvClickListener(
                view -> requestDestination(Format.CSV)
        );
        viewBinder.setOnXlsxClickListener(
                view -> requestDestination(Format.XLSX)
        );
        viewBinder.setOnPdfClickListener(
                view -> requestDestination(Format.PDF)
        );
        viewBinder.setOnBackClickListener(view -> finish());
        viewBinder.setBusy(true);
    }

    private void configureRepositories() {
        String databaseName = normalizedExtra(EXTRA_DATABASE_NAME);
        if (databaseName == null) {
            applicationRepository =
                    new OmrSQLiteAssessmentApplicationRepository(this);
            assessmentRepository = new OmrSQLiteAssessmentRepository(this);
            participantRepository =
                    new OmrSQLiteAssessmentParticipantRepository(this);
            attemptRepository =
                    new OmrSQLiteAssessmentAttemptRepository(this);
        } else {
            applicationRepository =
                    new OmrSQLiteAssessmentApplicationRepository(
                            this, databaseName
                    );
            assessmentRepository = new OmrSQLiteAssessmentRepository(
                    this, databaseName
            );
            participantRepository =
                    new OmrSQLiteAssessmentParticipantRepository(
                            this, databaseName
                    );
            attemptRepository = new OmrSQLiteAssessmentAttemptRepository(
                    this, databaseName
            );
        }
        String historyDatabaseName = normalizedExtra(
                EXTRA_HISTORY_DATABASE_NAME
        );
        historyRepository = historyDatabaseName == null
                ? new OmrSQLiteGradingHistoryRepository(this)
                : new OmrSQLiteGradingHistoryRepository(
                this, historyDatabaseName
        );
        reportService = new OmrAssessmentResultReportService(
                applicationRepository,
                assessmentRepository,
                participantRepository,
                attemptRepository,
                historyRepository
        );
    }

    private void loadReport() {
        String applicationId = extractApplicationId(getIntent());
        if (applicationId == null) {
            showLoadErrorAndFinish();
            return;
        }
        executor.execute(() -> {
            try {
                OmrAssessmentResultReport loaded = reportService.build(
                        applicationId
                );
                runOnUiThread(() -> {
                    if (released || viewBinder == null) {
                        return;
                    }
                    report = loaded;
                    renderQuery(viewBinder.getSearchQuery());
                    viewBinder.setBusy(false);
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Falha ao carregar resultados.", exception);
                runOnUiThread(this::showLoadErrorAndFinish);
            }
        });
    }

    private void renderQuery(String query) {
        if (report == null || viewBinder == null) {
            return;
        }
        viewBinder.render(OmrAssessmentResultViewState.from(report, query));
    }

    private void requestDestination(Format format) {
        OmrAssessmentResultReport current = report;
        if (current == null) {
            return;
        }
        switch (format) {
            case CSV:
                csvLauncher.launch(OmrAssessmentResultExportNames.csv(current));
                break;
            case XLSX:
                xlsxLauncher.launch(
                        OmrAssessmentResultExportNames.xlsx(current)
                );
                break;
            case PDF:
                pdfLauncher.launch(OmrAssessmentResultExportNames.pdf(current));
                break;
            default:
                throw new IllegalStateException("Formato desconhecido.");
        }
    }

    private void handleDestination(Format format, @Nullable Uri uri) {
        if (uri == null) {
            Toast.makeText(
                    this,
                    R.string.omr_assessment_result_export_cancelled,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        OmrAssessmentResultReport current = report;
        if (current == null || executor == null || viewBinder == null) {
            return;
        }
        viewBinder.setBusy(true);
        executor.execute(() -> {
            try (OutputStream output =
                         getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IOException("Destino indisponível.");
                }
                write(format, current, output);
                runOnUiThread(() -> finishExport(true));
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "Falha ao exportar resultados.", exception);
                runOnUiThread(() -> finishExport(false));
            }
        });
    }

    private static void write(
            Format format,
            OmrAssessmentResultReport report,
            OutputStream output
    ) throws IOException {
        switch (format) {
            case CSV:
                new OmrAssessmentResultCsvExporter().write(report, output);
                return;
            case XLSX:
                new OmrAssessmentResultXlsxExporter().write(report, output);
                return;
            case PDF:
                new OmrAssessmentResultPdfExporter().write(report, output);
                return;
            default:
                throw new IllegalStateException("Formato desconhecido.");
        }
    }

    private void finishExport(boolean success) {
        if (released || viewBinder == null) {
            return;
        }
        viewBinder.setBusy(false);
        Toast.makeText(
                this,
                success
                        ? R.string.omr_assessment_result_export_success
                        : R.string.omr_assessment_result_export_error,
                success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG
        ).show();
    }

    @Nullable
    private String normalizedExtra(String name) {
        String value = getIntent().getStringExtra(name);
        return value == null || value.trim().isEmpty()
                ? null : value.trim();
    }

    private void showLoadErrorAndFinish() {
        if (released) {
            return;
        }
        Toast.makeText(
                this,
                R.string.omr_assessment_result_load_error,
                Toast.LENGTH_LONG
        ).show();
        finish();
    }

    private static String requireApplicationId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "O identificador da aplicação é obrigatório."
            );
        }
        return value.trim();
    }

    @Override
    protected void onDestroy() {
        released = true;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (viewBinder != null) {
            viewBinder.release();
            viewBinder = null;
        }
        if (historyRepository != null) {
            historyRepository.close();
            historyRepository = null;
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
        report = null;
        reportService = null;
        super.onDestroy();
    }
}
