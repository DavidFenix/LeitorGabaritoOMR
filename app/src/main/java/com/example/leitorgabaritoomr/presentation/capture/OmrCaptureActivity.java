package com.example.leitorgabaritoomr.presentation.capture;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionContext;
import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentCorrectionRecorder;
import com.example.leitorgabaritoomr.application.grading.OmrGradingService;
import com.example.leitorgabaritoomr.application.history.OmrGradingHistoryRecorder;
import com.example.leitorgabaritoomr.application.layout.OmrCaptureLayoutProvider;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;
import com.example.leitorgabaritoomr.infrastructure.history.OmrSQLiteGradingHistoryRepository;
import com.example.leitorgabaritoomr.infrastructure.school.OmrSQLiteAssessmentAttemptRepository;
import com.example.leitorgabaritoomr.presentation.grading.OmrGradingResultActivity;
import com.example.leitorgabaritoomr.presentation.result.OmrReadingResultActivity;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.Serializable;

/**
 * Tela de captura OMR de producao.
 *
 * O Laboratorio OMR permanece na MainActivity. Esta Activity usa a
 * mesma arquitetura validada, mas apresenta somente instrucoes e
 * progresso necessarios para a leitura real.
 */
public final class OmrCaptureActivity
        extends AppCompatActivity
        implements
        CameraBridgeViewBase.CvCameraViewListener2,
        OmrCaptureController.Listener {

    private static final String TAG =
            "OMR_Capture";

    public static final String EXTRA_ANSWER_KEY =
            "com.example.leitorgabaritoomr.extra.OMR_ANSWER_KEY";

    public static final String EXTRA_STUDENT_IDENTITY =
            "com.example.leitorgabaritoomr.extra."
                    + "OMR_STUDENT_IDENTITY";

    public static final String EXTRA_ASSESSMENT_CORRECTION_CONTEXT =
            "com.example.leitorgabaritoomr.extra."
                    + "OMR_ASSESSMENT_CORRECTION_CONTEXT";

    private static final String EXTRA_SCHOOL_DATABASE_NAME =
            "com.example.leitorgabaritoomr.extra."
                    + "OMR_CAPTURE_SCHOOL_DATABASE_NAME";

    private static final int CAMERA_PERMISSION_CODE =
            101;

    private static final int MAX_FRAME_WIDTH =
            1920;

    private static final int MAX_FRAME_HEIGHT =
            1440;

    private CameraBridgeViewBase cameraBridgeView;
    private OmrCaptureController captureController;
    private OmrLayoutDefinition captureLayoutDefinition;
    private OmrAnswerKeyDefinition activeAnswerKey;
    private OmrStudentIdentity activeStudentIdentity;
    private OmrAssessmentCorrectionContext activeAssessmentContext;
    private OmrSQLiteGradingHistoryRepository gradingHistoryRepository;
    private OmrGradingHistoryRecorder gradingHistoryRecorder;
    private OmrSQLiteAssessmentAttemptRepository assessmentAttemptRepository;
    private OmrAssessmentCorrectionRecorder assessmentCorrectionRecorder;

    private boolean openCvLoaded;
    private volatile boolean cameraEnabled;
    private volatile boolean destroyed;

    private OmrReadingResult completedReadingResult;
    private OmrGradingResult completedGradingResult;
    private boolean resultScreenOpen;

    private final ActivityResultLauncher<Intent>
            readingResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    this::onReadingResultActivityFinished
            );

    private final ActivityResultLauncher<Intent>
            gradingResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    this::onGradingResultActivityFinished
            );

    /**
     * Cria uma captura sem correção automática por gabarito.
     */
    public static Intent createIntent(
            Context context
    ) {
        return createIntent(
                context,
                null,
                null
        );
    }

    /**
     * Cria uma captura que poderá corrigir a leitura com o gabarito
     * oficial informado.
     */
    public static Intent createIntent(
            Context context,
            @Nullable OmrAnswerKeyDefinition answerKeyDefinition
    ) {
        return createIntent(
                context,
                answerKeyDefinition,
                null
        );
    }

    /**
     * Cria uma captura vinculada ao gabarito e ao aluno informados.
     *
     * Os parametros permanecem opcionais para preservar o Laboratorio e os
     * fluxos de leitura sem correcao que ja existem.
     */
    public static Intent createIntent(
            Context context,
            @Nullable OmrAnswerKeyDefinition answerKeyDefinition,
            @Nullable OmrStudentIdentity studentIdentity
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        Intent intent = new Intent(
                context,
                OmrCaptureActivity.class
        );

        if (answerKeyDefinition != null) {
            intent.putExtra(
                    EXTRA_ANSWER_KEY,
                    answerKeyDefinition
            );
        }

        if (studentIdentity != null) {
            intent.putExtra(
                    EXTRA_STUDENT_IDENTITY,
                    studentIdentity
            );
        }

        return intent;
    }

    /**
     * Cria uma captura vinculada a uma aplicação e a um participante da
     * chamada congelada. O gabarito e o aluno são obtidos do contexto já
     * validado, sem consultar a seleção global do laboratório.
     */
    public static Intent createAssessmentIntent(
            Context context,
            OmrAssessmentCorrectionContext correctionContext
    ) {
        return createAssessmentIntent(
                context,
                correctionContext,
                null
        );
    }

    /**
     * Variante usada por fluxos com armazenamento escolar isolado.
     * Um nome nulo mantém o banco padrão de produção.
     */
    public static Intent createAssessmentIntent(
            Context context,
            OmrAssessmentCorrectionContext correctionContext,
            @Nullable String schoolDatabaseName
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "O contexto é obrigatório."
            );
        }

        if (correctionContext == null) {
            throw new IllegalArgumentException(
                    "O contexto da correção é obrigatório."
            );
        }

        Intent intent = new Intent(
                context,
                OmrCaptureActivity.class
        ).putExtra(
                EXTRA_ASSESSMENT_CORRECTION_CONTEXT,
                correctionContext
        );

        if (schoolDatabaseName != null) {
            if (schoolDatabaseName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "O nome do banco escolar não pode ser vazio."
                );
            }

            intent.putExtra(
                    EXTRA_SCHOOL_DATABASE_NAME,
                    schoolDatabaseName.trim()
            );
        }

        return intent;
    }

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(
                R.layout.activity_omr_capture
        );

        activeAssessmentContext = extractAssessmentCorrectionContext(
                getIntent()
        );

        if (activeAssessmentContext == null) {
            activeAnswerKey = extractAnswerKey(getIntent());
            activeStudentIdentity = extractStudentIdentity(getIntent());
        } else {
            activeAnswerKey = activeAssessmentContext.getAnswerKey();
            activeStudentIdentity = activeAssessmentContext
                    .getStudentIdentity();
        }

        logActiveAnswerKey();
        logActiveStudentIdentity();
        renderAssessmentCorrectionContext();

        if (!resolveCaptureLayout()) {
            return;
        }

        initializeGradingHistory();
        if (!initializeAssessmentCorrection()) {
            return;
        }

        configureCameraView();
        initializeOpenCvAndCapture();

        if (openCvLoaded) {
            ensureCameraPermission();
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private OmrAnswerKeyDefinition extractAnswerKey(
            @Nullable Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        Serializable value =
                intent.getSerializableExtra(
                        EXTRA_ANSWER_KEY
                );

        if (!(value instanceof OmrAnswerKeyDefinition)) {
            return null;
        }

        return (OmrAnswerKeyDefinition) value;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private OmrStudentIdentity extractStudentIdentity(
            @Nullable Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        Serializable value =
                intent.getSerializableExtra(
                        EXTRA_STUDENT_IDENTITY
                );

        if (!(value instanceof OmrStudentIdentity)) {
            return null;
        }

        return (OmrStudentIdentity) value;
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static OmrAssessmentCorrectionContext
    extractAssessmentCorrectionContext(
            @Nullable Intent intent
    ) {
        if (intent == null) {
            return null;
        }

        Serializable value = intent.getSerializableExtra(
                EXTRA_ASSESSMENT_CORRECTION_CONTEXT
        );

        if (!(value instanceof OmrAssessmentCorrectionContext)) {
            return null;
        }

        return (OmrAssessmentCorrectionContext) value;
    }

    private void renderAssessmentCorrectionContext() {
        TextView contextTextView = findViewById(
                R.id.textOmrCaptureAssessmentContext
        );

        if (contextTextView == null) {
            return;
        }

        if (activeAssessmentContext == null) {
            contextTextView.setVisibility(View.GONE);
            return;
        }

        contextTextView.setText(getString(
                R.string.omr_capture_assessment_context,
                activeAssessmentContext.getParticipant()
                        .getRosterPosition(),
                activeAssessmentContext.getParticipant()
                        .getStudentName(),
                activeAssessmentContext.getParticipant()
                        .getRegistration(),
                activeAssessmentContext.getAssessment().getTitle(),
                activeAssessmentContext.getAssessment().getVersion()
        ));
        contextTextView.setVisibility(View.VISIBLE);
    }

    private void logActiveAnswerKey() {
        if (activeAnswerKey == null) {
            Log.i(
                    TAG,
                    "Captura iniciada sem gabarito oficial."
            );
            return;
        }

        Log.i(
                TAG,
                "Captura iniciada com gabarito oficial"
                        + " | id="
                        + activeAnswerKey.getId()
                        + " | layout="
                        + activeAnswerKey.getLayoutId()
                        + "@v"
                        + activeAnswerKey.getLayoutVersion()
                        + " | questoes="
                        + activeAnswerKey.getQuestionCount()
        );
    }

    private void logActiveStudentIdentity() {
        if (activeStudentIdentity == null) {
            Log.i(
                    TAG,
                    "Captura iniciada sem aluno identificado."
            );
            return;
        }

        Log.i(
                TAG,
                "Aluno vinculado a sessao de captura"
                        + " | id="
                        + activeStudentIdentity.getStudentId()
                        + " | matricula="
                        + activeStudentIdentity.getRegistration()
                        + " | turma="
                        + activeStudentIdentity.getClassName()
        );
    }

    private void initializeGradingHistory() {
        if (activeStudentIdentity == null) {
            return;
        }

        try {
            gradingHistoryRepository =
                    new OmrSQLiteGradingHistoryRepository(
                            this
                    );

            gradingHistoryRecorder =
                    new OmrGradingHistoryRecorder(
                            gradingHistoryRepository
                    );

            Log.i(
                    TAG,
                    "Historico de correcoes preparado para o aluno"
                            + " | studentId="
                            + activeStudentIdentity.getStudentId()
            );

        } catch (RuntimeException exception) {
            closeGradingHistory();

            Log.e(
                    TAG,
                    "Nao foi possivel preparar o historico de correcoes.",
                    exception
            );

            showLongMessage(
                    "Nao foi possivel preparar o historico deste aluno."
            );
        }
    }

    private boolean initializeAssessmentCorrection() {
        if (activeAssessmentContext == null) {
            return true;
        }

        if (gradingHistoryRecorder == null) {
            showLongMessage(
                    "Não foi possível preparar o histórico desta aplicação."
            );
            setResult(Activity.RESULT_CANCELED);
            finish();
            return false;
        }

        try {
            String databaseName = getIntent().getStringExtra(
                    EXTRA_SCHOOL_DATABASE_NAME
            );

            if (databaseName == null || databaseName.trim().isEmpty()) {
                assessmentAttemptRepository =
                        new OmrSQLiteAssessmentAttemptRepository(this);
            } else {
                assessmentAttemptRepository =
                        new OmrSQLiteAssessmentAttemptRepository(
                                this,
                                databaseName.trim()
                        );
            }

            assessmentCorrectionRecorder =
                    new OmrAssessmentCorrectionRecorder(
                            assessmentAttemptRepository
                    );

            return true;

        } catch (RuntimeException exception) {
            closeAssessmentCorrection();

            Log.e(
                    TAG,
                    "Não foi possível preparar o vínculo com a aplicação.",
                    exception
            );

            showLongMessage(
                    "Não foi possível preparar a correção desta aplicação."
            );
            setResult(Activity.RESULT_CANCELED);
            finish();
            return false;
        }
    }

    private boolean resolveCaptureLayout() {
        try {
            captureLayoutDefinition =
                    new OmrCaptureLayoutProvider()
                            .resolve(activeAnswerKey);

            Log.i(
                    TAG,
                    "Layout da captura resolvido"
                            + " | id="
                            + captureLayoutDefinition.getId()
                            + " | versao="
                            + captureLayoutDefinition.getVersion()
                            + " | questoes="
                            + captureLayoutDefinition
                            .getQuestionCount()
            );

            return true;

        } catch (RuntimeException exception) {
            captureLayoutDefinition = null;

            Log.e(
                    TAG,
                    "O layout do gabarito nao e suportado.",
                    exception
            );

            showLongMessage(
                    "Este gabarito usa um modelo de folha"
                            + " incompatível com esta versão do app."
            );

            setResult(Activity.RESULT_CANCELED);
            finish();

            return false;
        }
    }

    private void configureCameraView() {
        cameraBridgeView =
                findViewById(
                        R.id.cameraViewOmrCapture
                );

        if (cameraBridgeView == null) {
            throw new IllegalStateException(
                    "cameraViewOmrCapture nao foi encontrada."
            );
        }

        cameraBridgeView.setMaxFrameSize(
                MAX_FRAME_WIDTH,
                MAX_FRAME_HEIGHT
        );

        cameraBridgeView.setVisibility(
                SurfaceView.VISIBLE
        );

        cameraBridgeView.setCvCameraViewListener(
                this
        );
    }

    private void initializeOpenCvAndCapture() {
        try {
            openCvLoaded =
                    OpenCVLoader.initDebug();

            if (!openCvLoaded) {
                showLongMessage(
                        "Nao foi possivel inicializar o OpenCV."
                );

                Log.e(
                        TAG,
                        "OpenCVLoader.initDebug() retornou false."
                );

                return;
            }

            if (captureLayoutDefinition == null) {
                throw new IllegalStateException(
                        "O layout da captura nao foi resolvido."
                );
            }

            captureController =
                    OmrCaptureController.create(
                            findViewById(
                                    android.R.id.content
                            ),
                            captureLayoutDefinition,
                            this
                    );

            captureController
                    .setShowTechnicalDiagnostic(
                            false
                    );

            Log.i(
                    TAG,
                    "OpenCV e controlador de captura inicializados."
            );

        } catch (Throwable throwable) {
            openCvLoaded = false;

            closeCaptureController();

            Log.e(
                    TAG,
                    "Falha ao inicializar a captura OMR.",
                    throwable
            );

            showLongMessage(
                    "Erro ao iniciar o leitor OMR: "
                            + throwable
                            .getClass()
                            .getSimpleName()
            );
        }
    }

    @Override
    public Mat onCameraFrame(
            CameraBridgeViewBase.CvCameraViewFrame inputFrame
    ) {
        Mat rgbaFrame = inputFrame.rgba();
        Mat grayFrame = inputFrame.gray();

        OmrCaptureController controller =
                captureController;

        if (destroyed
                || controller == null
                || controller.isClosed()) {

            return rgbaFrame;
        }

        try {
            controller.processFrame(
                    grayFrame,
                    rgbaFrame
            );

        } catch (IllegalStateException exception) {
            if (!destroyed) {
                Log.w(
                        TAG,
                        "Frame ignorado pelo estado da captura.",
                        exception
                );
            }

        } catch (Throwable throwable) {
            Log.e(
                    TAG,
                    "Erro inesperado ao entregar frame OMR.",
                    throwable
            );
        }

        return rgbaFrame;
    }

    @Override
    public void onCameraViewStarted(
            int width,
            int height
    ) {
        cameraEnabled = true;

        Log.i(
                TAG,
                "Camera iniciada | frame="
                        + width
                        + "x"
                        + height
        );
    }

    @Override
    public void onCameraViewStopped() {
        cameraEnabled = false;

        Log.i(
                TAG,
                "Camera interrompida."
        );
    }

    /**
     * Chamado uma unica vez e sempre na thread principal.
     */
    @Override
    public void onCaptureCompleted(
            OmrReadingResult readingResult
    ) {
        if (destroyed) {
            return;
        }

        completedReadingResult = readingResult;

        stopCamera();

        Log.i(
                TAG,
                "Leitura OMR concluida e confirmada"
                        + " | id="
                        + readingResult.getReadingId()
                        + " | layout="
                        + readingResult.getLayoutId()
                        + "@v"
                        + readingResult.getLayoutVersion()
                        + " | questoes="
                        + readingResult.getQuestionCount()
                        + " | unicas="
                        + readingResult.getSingleMarkCount()
                        + " | brancas="
                        + readingResult.getBlankCount()
                        + " | multiplas="
                        + readingResult.getMultipleMarkCount()
                        + " | ambiguas="
                        + readingResult.getAmbiguousCount()
        );

        if (activeAnswerKey == null) {
            openReadingResultScreen();
            return;
        }

        gradeAndOpenGradingResultScreen();
    }

    private void gradeAndOpenGradingResultScreen() {
        if (destroyed
                || completedReadingResult == null
                || activeAnswerKey == null) {

            return;
        }

        try {
            if (captureLayoutDefinition == null) {
                throw new IllegalStateException(
                        "O layout usado na captura nao esta disponivel."
                );
            }

            OmrGradingService gradingService =
                    new OmrGradingService();

            completedGradingResult = gradingService.grade(
                    captureLayoutDefinition,
                    activeAnswerKey,
                    completedReadingResult
            );

            Log.i(
                    TAG,
                    "Leitura corrigida com o gabarito oficial"
                            + " | gabarito="
                            + activeAnswerKey.getId()
                            + " | acertos="
                            + completedGradingResult
                            .getCorrectCount()
                            + " | erros="
                            + completedGradingResult
                            .getIncorrectCount()
                            + " | percentual="
                            + completedGradingResult
                            .getAwardedPercentage()
            );

        } catch (RuntimeException exception) {
            completedGradingResult = null;

            Log.e(
                    TAG,
                    "O gabarito oficial não pôde corrigir a leitura.",
                    exception
            );

            showLongMessage(
                    "O gabarito oficial não é compatível com esta"
                            + " leitura. O resultado será exibido"
                            + " sem correção."
            );

            openReadingResultScreen();
            return;
        }

        openGradingResultScreen();
    }

    private void openReadingResultScreen() {
        if (destroyed
                || resultScreenOpen
                || completedReadingResult == null) {

            return;
        }

        resultScreenOpen = true;

        readingResultLauncher.launch(
                OmrReadingResultActivity.createIntent(
                        this,
                        completedReadingResult
                )
        );
    }

    private void onReadingResultActivityFinished(
            ActivityResult activityResult
    ) {
        resultScreenOpen = false;

        if (destroyed) {
            return;
        }

        if (activityResult.getResultCode()
                == OmrReadingResultActivity.RESULT_READ_AGAIN) {

            restartCapture();
            return;
        }

        finishCaptureFlow(activityResult);
    }

    private void openGradingResultScreen() {
        if (destroyed
                || resultScreenOpen
                || completedGradingResult == null) {

            return;
        }

        resultScreenOpen = true;

        gradingResultLauncher.launch(
                activeAssessmentContext == null
                        ? OmrGradingResultActivity.createIntent(
                        this,
                        completedGradingResult
                )
                        : OmrGradingResultActivity.createIntent(
                        this,
                        completedGradingResult,
                        activeAssessmentContext
                )
        );
    }

    private void onGradingResultActivityFinished(
            ActivityResult activityResult
    ) {
        resultScreenOpen = false;

        if (destroyed) {
            return;
        }

        int resultCode = activityResult.getResultCode();

        if (!applyGradingHistoryDecision(resultCode)) {

            openGradingResultScreen();
            return;
        }

        if (resultCode
                == OmrGradingResultActivity.RESULT_READ_AGAIN) {

            restartCapture();
            return;
        }

        finishCaptureFlow(activityResult);
    }

    /**
     * Aplica ao historico a decisao devolvida pela tela de resultado.
     */
    private boolean applyGradingHistoryDecision(
            int activityResultCode
    ) {
        try {
            OmrGradingHistoryRecord historyRecord =
                    OmrCaptureHistoryCommitter
                            .recordIfConfirmed(
                            activityResultCode,
                            activeStudentIdentity,
                            completedGradingResult,
                            gradingHistoryRecorder
                    );

            if (historyRecord == null) {
                Log.i(
                        TAG,
                        "Historico mantido sem alteracoes"
                                + " | resultCode="
                                + activityResultCode
                                + " | possuiAluno="
                                + (activeStudentIdentity != null)
                );

                return true;
            }

            if (activeAssessmentContext != null) {
                if (assessmentCorrectionRecorder == null) {
                    throw new IllegalStateException(
                            "O gravador da tentativa não está disponível."
                    );
                }

                assessmentCorrectionRecorder.record(
                        activeAssessmentContext,
                        historyRecord
                );
            }

            Log.i(
                    TAG,
                    "Correcao registrada no historico"
                            + " | historyRecordId="
                            + historyRecord.getHistoryRecordId()
                            + " | readingId="
                            + historyRecord.getReadingId()
                            + " | studentId="
                            + historyRecord.getStudent().getStudentId()
                            + " | percentual="
                            + historyRecord.getAwardedPercentage()
            );

            return true;

        } catch (RuntimeException exception) {
            Log.e(
                    TAG,
                    "Falha ao registrar a correcao no historico.",
                    exception
            );

            showLongMessage(
                    "A nota nao foi salva. Confira o armazenamento"
                            + " e toque em concluir novamente."
            );

            return false;
        }
    }

    private void restartCapture() {
        OmrCaptureController controller =
                captureController;

        if (controller == null
                || controller.isClosed()) {

            showLongMessage(
                    "Nao foi possivel reiniciar a leitura OMR."
            );

            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        completedReadingResult = null;
        completedGradingResult = null;

        controller.retry();
        startCamera();

        Log.i(
                TAG,
                "Nova tentativa de leitura OMR iniciada."
        );
    }

    private void finishCaptureFlow(
            ActivityResult activityResult
    ) {
        Intent resultData =
                activityResult.getData();

        if (activityResult.getResultCode()
                == Activity.RESULT_OK) {

            setResult(
                    Activity.RESULT_OK,
                    resultData
            );

        } else {
            setResult(Activity.RESULT_CANCELED);
        }

        finish();
    }

    /**
     * Chamado uma unica vez e sempre na thread principal.
     * O Binder mantem a previa e apresenta o botao de nova tentativa.
     */
    @Override
    public void onCaptureFailed(
            String failureMessage
    ) {
        if (destroyed) {
            return;
        }

        Log.e(
                TAG,
                "Captura OMR encerrada com falha: "
                        + failureMessage
        );
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureCameraPermission() {
        if (hasCameraPermission()) {
            startCamera();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.CAMERA
                },
                CAMERA_PERMISSION_CODE
        );
    }

    private void startCamera() {
        if (destroyed
                || cameraEnabled
                || !openCvLoaded
                || !hasCameraPermission()
                || cameraBridgeView == null) {

            return;
        }

        OmrCaptureController controller =
                captureController;

        if (controller == null
                || controller.isClosed()
                || controller.getState().isSuccessful()) {

            return;
        }

        cameraBridgeView
                .setCameraPermissionGranted();

        cameraBridgeView.enableView();

        /*
         * Evita enableView() duplicado enquanto aguardamos o callback
         * onCameraViewStarted().
         */
        cameraEnabled = true;
    }

    private void stopCamera() {
        CameraBridgeViewBase cameraView =
                cameraBridgeView;

        if (cameraView == null
                || !cameraEnabled) {
            return;
        }

        cameraView.disableView();
        cameraEnabled = false;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode
                != CAMERA_PERMISSION_CODE) {
            return;
        }

        if (grantResults.length > 0
                && grantResults[0]
                == PackageManager.PERMISSION_GRANTED) {

            startCamera();
            return;
        }

        showLongMessage(
                "A permissao da camera e necessaria para o leitor."
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (openCvLoaded
                && hasCameraPermission()) {

            startCamera();
        }
    }

    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;

        stopCamera();
        closeCaptureController();
        closeGradingHistory();
        closeAssessmentCorrection();

        completedReadingResult = null;
        completedGradingResult = null;
        captureLayoutDefinition = null;
        activeAnswerKey = null;
        activeStudentIdentity = null;
        activeAssessmentContext = null;
        resultScreenOpen = false;
        cameraBridgeView = null;

        super.onDestroy();
    }

    private void closeGradingHistory() {
        gradingHistoryRecorder = null;

        OmrSQLiteGradingHistoryRepository repository =
                gradingHistoryRepository;

        gradingHistoryRepository = null;

        if (repository == null) {
            return;
        }

        try {
            repository.close();

        } catch (RuntimeException exception) {
            Log.w(
                    TAG,
                    "Falha ao fechar o historico de correcoes.",
                    exception
            );
        }
    }

    private void closeAssessmentCorrection() {
        assessmentCorrectionRecorder = null;

        OmrSQLiteAssessmentAttemptRepository repository =
                assessmentAttemptRepository;
        assessmentAttemptRepository = null;

        if (repository == null) {
            return;
        }

        try {
            repository.close();
        } catch (RuntimeException exception) {
            Log.w(
                    TAG,
                    "Falha ao fechar as tentativas da aplicação.",
                    exception
            );
        }
    }

    private void closeCaptureController() {
        OmrCaptureController controller =
                captureController;

        captureController = null;

        if (controller != null) {
            controller.close();
        }
    }

    private void showLongMessage(
            String message
    ) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
