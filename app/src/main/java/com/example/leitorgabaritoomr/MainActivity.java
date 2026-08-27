package com.example.leitorgabaritoomr;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.presentation.capture.OmrCaptureActivity;
import com.example.leitorgabaritoomr.presentation.grading.OmrManualAnswerKeyActivity;
import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.processing.DefaultMarkerFrameProcessorFactory;
import com.example.leitorgabaritoomr.vision.processing.MarkerFrameProcessor;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.Serializable;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private VisionDebugController visionDebugController;

    private static final MarkerDetectorMode MARKER_DETECTOR_MODE =
            MarkerDetectorMode.SOLID_SQUARE;

    private static final String TAG = "OMR_Camera";

    private static final String STATE_CURRENT_ANSWER_KEY =
            "omr.main.current_answer_key";

    private static final int CAMERA_PERMISSION_CODE = 100;

    /*
     * Permite desativar temporariamente a câmera durante
     * testes sem remover o código relacionado a ela.
     */
    private static final boolean DEBUG_ATIVAR_CAMERA = true;

    private CameraBridgeViewBase cameraBridgeView;
    private MarkerFrameProcessor markerFrameProcessor;
    private OmrAnswerKeyDefinition currentAnswerKey;

    private final ActivityResultLauncher<Intent>
            manualAnswerKeyLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    this::handleManualAnswerKeyResult
            );

    private volatile boolean cameraHabilitada = false;
    private boolean openCvCarregado = false;

    private long contadorFrames = 0;
    private boolean primeiroFrameRegistrado = false;
    private long ultimoLogDeteccao = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        restoreCurrentAnswerKey(savedInstanceState);

        configurarAcessoCapturaReal();
        configureManualAnswerKeyAccess();

        configurarCameraView();

        inicializarOpenCV();

        if (DEBUG_ATIVAR_CAMERA) {
            checarPermissaoCamera();
        }
    }

    private void configurarAcessoCapturaReal() {

        findViewById(
                R.id.buttonOpenOmrCapture
        ).setOnClickListener(
                view -> startActivity(
                        OmrCaptureActivity.createIntent(
                                this,
                                currentAnswerKey
                        )
                )
        );
    }

    private void configureManualAnswerKeyAccess() {

        findViewById(
                R.id.buttonOpenManualAnswerKey
        ).setOnClickListener(
                view -> manualAnswerKeyLauncher.launch(
                        OmrManualAnswerKeyActivity.createIntent(
                                this
                        )
                )
        );
    }

    private void handleManualAnswerKeyResult(
            ActivityResult activityResult
    ) {
        if (activityResult.getResultCode()
                != Activity.RESULT_OK) {

            return;
        }

        OmrAnswerKeyDefinition createdAnswerKey =
                OmrManualAnswerKeyActivity
                        .extractCreatedAnswerKey(
                                activityResult.getData()
                        );

        if (createdAnswerKey == null) {
            Log.e(
                    TAG,
                    "O cadastro retornou sem gabarito válido."
            );

            Toast.makeText(
                    this,
                    "Não foi possível recuperar o gabarito criado.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        currentAnswerKey = createdAnswerKey;

        Log.i(
                TAG,
                "Gabarito oficial cadastrado"
                        + " | id="
                        + createdAnswerKey.getId()
                        + " | layout="
                        + createdAnswerKey.getLayoutId()
                        + "@v"
                        + createdAnswerKey.getLayoutVersion()
                        + " | questões="
                        + createdAnswerKey.getQuestionCount()
        );

        Toast.makeText(
                this,
                "Gabarito \""
                        + createdAnswerKey.getName()
                        + "\" cadastrado com "
                        + createdAnswerKey.getQuestionCount()
                        + " questões.",
                Toast.LENGTH_LONG
        ).show();
    }

    @SuppressWarnings("deprecation")
    private void restoreCurrentAnswerKey(
            Bundle savedInstanceState
    ) {
        if (savedInstanceState == null) {
            return;
        }

        Serializable value =
                savedInstanceState.getSerializable(
                        STATE_CURRENT_ANSWER_KEY
                );

        if (value instanceof OmrAnswerKeyDefinition) {
            currentAnswerKey =
                    (OmrAnswerKeyDefinition) value;
        }
    }

    /*
     * =========================================================
     * CONFIGURAÇÃO DA CAMERA VIEW
     * =========================================================
     */

    private void configurarCameraView() {

        cameraBridgeView =
                findViewById(R.id.cameraViewLeitor);

        cameraBridgeView.setMaxFrameSize(
                1920,
                1440
        );

        cameraBridgeView.setVisibility(
                SurfaceView.VISIBLE
        );

        cameraBridgeView.setCvCameraViewListener(
                this
        );

        /*
         * Toque curto: próxima etapa.
         * Toque longo: etapa anterior.
         */
        cameraBridgeView.setClickable(true);
        cameraBridgeView.setLongClickable(true);

        cameraBridgeView.setOnClickListener(
                view -> selecionarProximaEtapa()
        );

        cameraBridgeView.setOnLongClickListener(
                view -> {

                    alternarPausaLaboratorio();

                    return true;
                }
        );

    }

    /*
     * =========================================================
     * INICIALIZAÇÃO DO OPENCV
     * =========================================================
     */

    private void inicializarOpenCV() {

        Log.d(
                TAG,
                "OPEN_CV_ETAPA_1: entrando em inicializarOpenCV()."
        );

        try {

            Log.d(
                    TAG,
                    "OPEN_CV_ETAPA_2: antes de initDebug()."
            );

            openCvCarregado =
                    OpenCVLoader.initDebug();

            Log.d(
                    TAG,
                    "OPEN_CV_ETAPA_3: initDebug() terminou."
                            + " Resultado = "
                            + openCvCarregado
            );

            if (!openCvCarregado) {

                Log.e(
                        TAG,
                        "OPEN_CV_ERRO: initDebug() retornou false."
                );

                Toast.makeText(
                        this,
                        "Não foi possível inicializar o OpenCV.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            Log.d(
                    TAG,
                    "OPEN_CV_ETAPA_4: OpenCV carregado."
            );

            configurarProcessamento();

            Log.d(
                    TAG,
                    "OPEN_CV_ETAPA_5: inicialização concluída."
            );

        } catch (Throwable throwable) {

            openCvCarregado = false;
            markerFrameProcessor = null;

            Log.e(
                    TAG,
                    "OPEN_CV_ERRO_FATAL:"
                            + " falha durante a inicialização.",
                    throwable
            );

            Toast.makeText(
                    this,
                    "Erro durante a inicialização do OpenCV: "
                            + throwable.getClass().getSimpleName(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /*
     * =========================================================
     * CONFIGURAÇÃO DO PROCESSAMENTO
     * =========================================================
     */

    private void selecionarProximaEtapa() {

        if (visionDebugController == null) {
            return;
        }

        VisionStage stage =
                visionDebugController.selectNext();

        Log.d(
                TAG,
                "Laboratório OMR"
                        + " | etapa="
                        + stage.getDisplayName()
        );
    }

    private void selecionarEtapaAnterior() {

        if (visionDebugController == null) {
            return;
        }

        VisionStage stage =
                visionDebugController.selectPrevious();

        Log.d(
                TAG,
                "Laboratório OMR"
                        + " | etapa="
                        + stage.getDisplayName()
        );
    }

    @Override
    public boolean onKeyDown(
            int keyCode,
            KeyEvent event
    ) {

        if (keyCode == KeyEvent.KEYCODE_F
                || keyCode == KeyEvent.KEYCODE_SPACE) {

            alternarPausaLaboratorio();

            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {

            selecionarProximaEtapa();

            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

            selecionarEtapaAnterior();

            return true;
        }

        return super.onKeyDown(
                keyCode,
                event
        );
    }

    private void configurarProcessamento() {

        Log.d(
                TAG,
                "PROCESSAMENTO_ETAPA_1:"
                        + " iniciando configuração."
        );

        try {

            visionDebugController =
                    new VisionDebugController();

            markerFrameProcessor =
                    DefaultMarkerFrameProcessorFactory.create(
                            MARKER_DETECTOR_MODE,
                            visionDebugController
                    );

            Log.d(
                    TAG,
                    "PROCESSAMENTO_ETAPA_2:"
                            + " processador configurado"
                            + " | detector="
                            + markerFrameProcessor.getDetectorName()
            );

        } catch (Throwable throwable) {

            markerFrameProcessor = null;

            Log.e(
                    TAG,
                    "PROCESSAMENTO_ERRO:"
                            + " falha ao configurar o processador.",
                    throwable
            );

            Toast.makeText(
                    this,
                    "Erro ao configurar o detector: "
                            + throwable.getClass().getSimpleName(),
                    Toast.LENGTH_LONG
            ).show();

            throw throwable;
        }
    }
    
    @Override
    public Mat onCameraFrame(
            CameraBridgeViewBase.CvCameraViewFrame inputFrame
    ) {

        Mat rgbaFrame = inputFrame.rgba();
        Mat grayFrame = inputFrame.gray();

        contadorFrames++;

        registrarPrimeiroFrame(
                rgbaFrame,
                grayFrame
        );

        if (markerFrameProcessor == null) {

            if (contadorFrames % 60 == 0) {

                Log.e(
                        TAG,
                        "Processador de marcadores está nulo."
                );
            }

            return rgbaFrame;
        }

        try {

            MarkerDetectionResult result =
                    markerFrameProcessor.process(
                            grayFrame,
                            rgbaFrame
                    );

            registrarResultadoDeteccao(result);

        } catch (Throwable throwable) {

            registrarErroDeteccao(throwable);
        }

        return rgbaFrame;
    }

    private void registrarPrimeiroFrame(
            Mat rgbaFrame,
            Mat grayFrame
    ) {

        if (primeiroFrameRegistrado) {
            return;
        }

        primeiroFrameRegistrado = true;

        Log.d(
                TAG,
                "Primeiro frame recebido"
                        + " | RGBA="
                        + rgbaFrame.cols()
                        + "x"
                        + rgbaFrame.rows()
                        + " canais="
                        + rgbaFrame.channels()
                        + " | GRAY="
                        + grayFrame.cols()
                        + "x"
                        + grayFrame.rows()
                        + " canais="
                        + grayFrame.channels()
        );
    }

    /*
     * =========================================================
     * LOGS DA DETECÇÃO
     * =========================================================
     */

    private void registrarResultadoDeteccao(
            MarkerDetectionResult result
    ) {

        if (!podeRegistrarLogDeteccao()) {
            return;
        }

        if (result == null) {

            Log.w(
                    TAG,
                    "O processador retornou resultado nulo."
            );

            return;
        }

        String tempoFormatado =
                String.format(
                        Locale.US,
                        "%.2f ms",
                        result.getProcessingTimeMillis()
                );

        if (result.hasMarkers()) {

            StringBuilder marcadores =
                    new StringBuilder();

            result.getMarkers().forEach(marker -> {

                if (marcadores.length() > 0) {
                    marcadores.append(", ");
                }

                if (marker.getCode() != null) {

                    marcadores.append(
                            marker.getCode()
                    );

                } else {

                    marcadores.append(
                            marker.getType().name()
                    );
                }
            });

            Log.d(
                    TAG,
                    "Marcadores detectados: ["
                            + marcadores
                            + "]"
                            + " | detector="
                            + result.getDetectorName()
                            + " | quantidade="
                            + result.getMarkerCount()
                            + " | rejeitados="
                            + result.getRejectedCandidates()
                            + " | tempo="
                            + tempoFormatado
            );

        } else {

            Log.d(
                    TAG,
                    "Nenhum marcador validado"
                            + " | detector="
                            + result.getDetectorName()
                            + " | candidatos rejeitados="
                            + result.getRejectedCandidates()
                            + " | tempo="
                            + tempoFormatado
            );
        }

        MarkerSetResolutionResult resolutionResult =
                markerFrameProcessor == null
                        ? null
                        : markerFrameProcessor
                        .getLastResolutionResult();

        if (resolutionResult == null) {
            return;
        }

        if (resolutionResult.isAccepted()) {

            Log.d(
                    TAG,
                    "Conjunto geométrico ACEITO"
                            + " | score="
                            + String.format(
                            Locale.US,
                            "%.3f",
                            resolutionResult.getBestScore()
                    )
                            + " | diferença="
                            + String.format(
                            Locale.US,
                            "%.3f",
                            resolutionResult.getScoreDifference()
                    )
                            + " | combinações="
                            + resolutionResult
                            .getEvaluatedCombinations()
            );

        } else {

            Log.d(
                    TAG,
                    "Conjunto geométrico REJEITADO"
                            + " | motivo="
                            + resolutionResult.getReason()
                            + " | score="
                            + String.format(
                            Locale.US,
                            "%.3f",
                            resolutionResult.getBestScore()
                    )
                            + " | diferença="
                            + String.format(
                            Locale.US,
                            "%.3f",
                            resolutionResult.getScoreDifference()
                    )
                            + " | combinações="
                            + resolutionResult
                            .getEvaluatedCombinations()
            );
        }

        MarkerStabilityResult stabilityResult =
                markerFrameProcessor == null
                        ? null
                        : markerFrameProcessor
                        .getLastStabilityResult();

        if (stabilityResult != null) {

            Log.d(
                    TAG,
                    "Estabilidade temporal"
                            + " | estado="
                            + stabilityResult.getState()
                            + " | confirmações="
                            + stabilityResult.getConsistentFrames()
                            + "/"
                            + stabilityResult.getRequiredFrames()
                            + " | falhas="
                            + stabilityResult.getMissedFrames()
                            + " | forma="
                            + String.format(
                            Locale.US,
                            "%.3f",
                            stabilityResult
                                    .getNormalizedShapeDistance()
                    )
                            + " | variaçãoÁrea="
                            + String.format(
                            Locale.US,
                            "%.3f",
                            stabilityResult
                                    .getRegionAreaChangeRatio()
                    )
            );
        }

    }

    private void registrarErroDeteccao(
            Throwable throwable
    ) {

        if (!podeRegistrarLogDeteccao()) {
            return;
        }

        String detectorName =
                markerFrameProcessor == null
                        ? "indisponível"
                        : markerFrameProcessor.getDetectorName();

        Log.e(
                TAG,
                "Erro durante a detecção"
                        + " | detector="
                        + detectorName,
                throwable
        );
    }

    private boolean podeRegistrarLogDeteccao() {

        long agora =
                System.currentTimeMillis();

        if (agora - ultimoLogDeteccao < 1000) {
            return false;
        }

        ultimoLogDeteccao = agora;

        return true;
    }

    /*
     * =========================================================
     * CALLBACKS DA CÂMERA
     * =========================================================
     */

    @Override
    public void onCameraViewStarted(
            int width,
            int height
    ) {

        cameraHabilitada = true;

        /*
         * A contagem é reiniciada sempre que uma nova sessão
         * da câmera começa.
         */
        contadorFrames = 0;
        primeiroFrameRegistrado = false;

        Log.d(
                TAG,
                "Câmera iniciada"
                        + " | frame="
                        + width
                        + "x"
                        + height
                        + " | view="
                        + cameraBridgeView.getWidth()
                        + "x"
                        + cameraBridgeView.getHeight()
        );
    }

    @Override
    public void onCameraViewStopped() {

        cameraHabilitada = false;

        Log.d(
                TAG,
                "Câmera interrompida."
        );
    }

    /*
     * =========================================================
     * PERMISSÃO DA CÂMERA
     * =========================================================
     */

    private boolean possuiPermissaoCamera() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void checarPermissaoCamera() {

        if (possuiPermissaoCamera()) {

            iniciarCamera();

        } else {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.CAMERA
                    },
                    CAMERA_PERMISSION_CODE
            );
        }
    }

    private void iniciarCamera() {

        if (cameraHabilitada) {

            Log.d(
                    TAG,
                    "Solicitação para iniciar câmera ignorada:"
                            + " câmera já habilitada."
            );

            return;
        }

        if (!DEBUG_ATIVAR_CAMERA) {

            Log.d(
                    TAG,
                    "A câmera está desativada"
                            + " pela configuração de depuração."
            );

            return;
        }

        if (!openCvCarregado) {

            Log.e(
                    TAG,
                    "A câmera não pode ser iniciada:"
                            + " OpenCV indisponível."
            );

            return;
        }

        if (!possuiPermissaoCamera()) {

            Log.w(
                    TAG,
                    "A câmera não pode ser iniciada:"
                            + " permissão ausente."
            );

            return;
        }

        if (cameraBridgeView == null) {

            Log.e(
                    TAG,
                    "A câmera não pode ser iniciada:"
                            + " CameraBridgeViewBase está nulo."
            );

            return;
        }

        cameraBridgeView.setCameraPermissionGranted();
        cameraBridgeView.enableView();

        /*
         * Esta atribuição evita chamadas duplicadas enquanto
         * aguardamos onCameraViewStarted().
         */
        cameraHabilitada = true;

        Log.d(
                TAG,
                "Visualização da câmera habilitada."
        );
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

        if (requestCode != CAMERA_PERMISSION_CODE) {
            return;
        }

        if (grantResults.length > 0
                && grantResults[0]
                == PackageManager.PERMISSION_GRANTED) {

            iniciarCamera();

        } else {

            Toast.makeText(
                    this,
                    "A permissão da câmera é necessária para o leitor.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /*
     * =========================================================
     * CICLO DE VIDA DA ACTIVITY
     * =========================================================
     */

    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle outState
    ) {
        if (currentAnswerKey != null) {
            outState.putSerializable(
                    STATE_CURRENT_ANSWER_KEY,
                    currentAnswerKey
            );
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (DEBUG_ATIVAR_CAMERA
                && openCvCarregado
                && possuiPermissaoCamera()) {

            iniciarCamera();
        }
    }

    @Override
    protected void onPause() {

        interromperCamera();

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        interromperCamera();

        if (visionDebugController != null) {

            visionDebugController.release();
            visionDebugController = null;
        }

        markerFrameProcessor = null;
        cameraBridgeView = null;

        super.onDestroy();
    }

    private void interromperCamera() {

        if (cameraBridgeView == null) {
            return;
        }

        if (!cameraHabilitada) {
            return;
        }

        cameraBridgeView.disableView();
        cameraHabilitada = false;
    }

    private void alternarPausaLaboratorio() {

        if (visionDebugController == null) {
            return;
        }

        boolean pausado =
                visionDebugController.toggleFreeze();

        Log.d(
                TAG,
                pausado
                        ? "Laboratório OMR pausado."
                        : "Laboratório OMR retomado."
        );
    }

}
