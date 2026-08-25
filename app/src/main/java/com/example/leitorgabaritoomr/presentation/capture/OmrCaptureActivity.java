package com.example.leitorgabaritoomr.presentation.capture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.leitorgabaritoomr.R;
import com.example.leitorgabaritoomr.vision.interpretation.SheetInterpretationResult;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

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

    private static final int CAMERA_PERMISSION_CODE =
            101;

    private static final int MAX_FRAME_WIDTH =
            1920;

    private static final int MAX_FRAME_HEIGHT =
            1440;

    private CameraBridgeViewBase cameraBridgeView;
    private OmrCaptureController captureController;

    private boolean openCvLoaded;
    private volatile boolean cameraEnabled;
    private volatile boolean destroyed;

    private SheetInterpretationResult
            completedInterpretationResult;

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

        configureCameraView();
        initializeOpenCvAndCapture();

        if (openCvLoaded) {
            ensureCameraPermission();
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

            captureController =
                    OmrCaptureController.createDefault(
                            findViewById(
                                    android.R.id.content
                            ),
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
            SheetInterpretationResult interpretationResult
    ) {
        if (destroyed) {
            return;
        }

        completedInterpretationResult =
                interpretationResult;

        stopCamera();

        Log.i(
                TAG,
                "Leitura OMR concluida e confirmada."
        );

        /*
         * A navegacao para a futura tela de resultado sera ligada
         * aqui. Ate ela existir, o resultado permanece preservado
         * nesta Activity e a interface mostra Leitura concluida.
         */
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

        completedInterpretationResult = null;
        cameraBridgeView = null;

        super.onDestroy();
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
