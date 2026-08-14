package com.example.leitorgabaritoomr;

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

import com.example.leitorgabaritoomr.vision.detector.ArucoMarkerDetector;
import com.example.leitorgabaritoomr.vision.detector.OmrMarkerDetector;
import com.example.leitorgabaritoomr.vision.drawing.MarkerOverlayRenderer;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;
import com.example.leitorgabaritoomr.vision.processing.MarkerFrameProcessor;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.util.Locale;

import com.example.leitorgabaritoomr.vision.detector.SolidSquareMarkerDetector;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectorMode;

import android.view.KeyEvent;

import com.example.leitorgabaritoomr.vision.debug.VisionDebugController;
import com.example.leitorgabaritoomr.vision.debug.VisionStage;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private VisionDebugController visionDebugController;

    private static final MarkerDetectorMode MARKER_DETECTOR_MODE =
            MarkerDetectorMode.SOLID_SQUARE;

    private static final String TAG = "OMR_Camera";

    private static final int CAMERA_PERMISSION_CODE = 100;

    /*
     * Permite desativar temporariamente a câmera durante
     * testes sem remover o código relacionado a ela.
     */
    private static final boolean DEBUG_ATIVAR_CAMERA = true;

    private CameraBridgeViewBase cameraBridgeView;
    private MarkerFrameProcessor markerFrameProcessor;

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

        configurarCameraView();

        inicializarOpenCV();

        if (DEBUG_ATIVAR_CAMERA) {
            checarPermissaoCamera();
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

                    selecionarEtapaAnterior();

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

            OmrMarkerDetector detector =
                    criarDetectorConfigurado();

            /*
             * Para continuar usando o detector que já funciona,
             * mantemos aqui o ArucoMarkerDetector.
             *
             * Futuramente poderemos trocar somente esta criação:
             *
             * new SolidSquareMarkerDetector()
             */
//            OmrMarkerDetector detector =
//                    new ArucoMarkerDetector();

            MarkerOverlayRenderer renderer =
                    new MarkerOverlayRenderer();

            visionDebugController =
                    new VisionDebugController();

            markerFrameProcessor =
                    new MarkerFrameProcessor(
                            detector,
                            renderer,
                            visionDebugController
                    );
//            MarkerOverlayRenderer renderer =
//                    new MarkerOverlayRenderer();
//
//            markerFrameProcessor =
//                    new MarkerFrameProcessor(
//                            detector,
//                            renderer
//                    );

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

    /*
     * =========================================================
     * PROCESSAMENTO DOS FRAMES
     * =========================================================
     */

    private OmrMarkerDetector criarDetectorConfigurado() {

        switch (MARKER_DETECTOR_MODE) {

            case ARUCO:

                return new ArucoMarkerDetector();

            case SOLID_SQUARE:

                return new SolidSquareMarkerDetector();

            default:

                throw new IllegalStateException(
                        "Modo de detector não suportado: "
                                + MARKER_DETECTOR_MODE
                );
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
}

//package com.example.leitorgabaritoomr;
//
//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.SurfaceView;
//import android.view.WindowManager;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import org.opencv.android.CameraBridgeViewBase;
//import org.opencv.android.OpenCVLoader;
//import org.opencv.core.Mat;
//import org.opencv.core.Scalar;
//import org.opencv.objdetect.ArucoDetector;
//import org.opencv.objdetect.DetectorParameters;
//import org.opencv.objdetect.Dictionary;
//import org.opencv.objdetect.Objdetect;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import org.opencv.imgproc.Imgproc;
//
//public class MainActivity extends AppCompatActivity
//        implements CameraBridgeViewBase.CvCameraViewListener2 {
//
//    private static final boolean DEBUG_ATIVAR_CAMERA = true;
//    private volatile boolean cameraHabilitada = false;
//
//    private long ultimoLogDeteccao = 0;
//
//    private static final String TAG = "OMR_Camera";
//    private static final int CAMERA_PERMISSION_CODE = 100;
//
//    private CameraBridgeViewBase cameraBridgeView;
//    private ArucoDetector arucoDetector;
//
//    private boolean openCvCarregado = false;
//
//    private long contadorFrames = 0;
//    private boolean primeiroFrameRegistrado = false;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        getWindow().addFlags(
//                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
//        );
//
//        setContentView(R.layout.activity_main);
//
//        cameraBridgeView =
//                findViewById(R.id.cameraViewLeitor);
//
//        cameraBridgeView.setMaxFrameSize(
//                1920,
//                1440
//        );
//
//        cameraBridgeView.setVisibility(
//                SurfaceView.VISIBLE
//        );
//
//        cameraBridgeView.setCvCameraViewListener(this);
//
//        inicializarOpenCV();
//
//        if (DEBUG_ATIVAR_CAMERA) {
//            checarPermissaoCamera();
//        }
//    }
//
//    private void inicializarOpenCV() {
//
//        try {
//
//            openCvCarregado = OpenCVLoader.initDebug();
//
//            Log.d(
//                    TAG,
//                    "OPEN_CV_ETAPA_3: initDebug() terminou. Resultado = "
//                            + openCvCarregado
//            );
//
//            if (!openCvCarregado) {
//
//                Log.e(TAG, "OPEN_CV_ERRO: initDebug() retornou false.");
//
//                Toast.makeText(
//                        this,
//                        "Não foi possível inicializar o OpenCV.",
//                        Toast.LENGTH_LONG
//                ).show();
//
//                return;
//            }
//
//            Log.d(TAG, "OPEN_CV_ETAPA_4: OpenCV carregado.");
//
//            configurarDetectorAruco();
//
//            Log.d(TAG, "OPEN_CV_ETAPA_5: inicialização concluída.");
//
//        } catch (Throwable throwable) {
//
//            openCvCarregado = false;
//            arucoDetector = null;
//
//            Log.e(
//                    TAG,
//                    "OPEN_CV_ERRO_FATAL: falha durante a inicialização.",
//                    throwable
//            );
//
//            Toast.makeText(
//                    this,
//                    "Erro durante a inicialização do OpenCV: "
//                            + throwable.getClass().getSimpleName(),
//                    Toast.LENGTH_LONG
//            ).show();
//        }
//    }
//
//    private void configurarDetectorAruco() {
//
//        Log.d(TAG, "ARUCO_ETAPA_1: iniciando configuração.");
//
//        try {
//
//            Log.d(TAG, "ARUCO_ETAPA_2: obtendo dicionário.");
//
//            Dictionary dictionary =
//                    Objdetect.getPredefinedDictionary(
//                            Objdetect.DICT_4X4_50
//                    );
//
//            Log.d(TAG, "ARUCO_ETAPA_3: criando parâmetros.");
//
//            DetectorParameters parameters =
//                    new DetectorParameters();
//
//            Log.d(TAG, "ARUCO_ETAPA_4: criando detector.");
//
//            arucoDetector =
//                    new ArucoDetector(
//                            dictionary,
//                            parameters
//                    );
//
//            Log.d(TAG, "ARUCO_ETAPA_5: detector criado.");
//
//        } catch (Throwable throwable) {
//
//            arucoDetector = null;
//
//            Log.e(
//                    TAG,
//                    "ARUCO_ERRO: falha ao configurar o detector.",
//                    throwable
//            );
//
//            Toast.makeText(
//                    this,
//                    "Erro ao configurar ArUco: "
//                            + throwable.getClass().getSimpleName(),
//                    Toast.LENGTH_LONG
//            ).show();
//        }
//    }
//
//    @Override
//    public Mat onCameraFrame(
//            CameraBridgeViewBase.CvCameraViewFrame inputFrame
//    ) {
//
//        Mat rgbaFrame = inputFrame.rgba();
//        Mat grayFrame = inputFrame.gray();
//
//        contadorFrames++;
//
//        if (!primeiroFrameRegistrado) {
//
//            primeiroFrameRegistrado = true;
//
//            Log.d(
//                    TAG,
//                    "Primeiro frame recebido"
//                            + " | RGBA=" + rgbaFrame.cols()
//                            + "x" + rgbaFrame.rows()
//                            + " canais=" + rgbaFrame.channels()
//                            + " | GRAY=" + grayFrame.cols()
//                            + "x" + grayFrame.rows()
//                            + " canais=" + grayFrame.channels()
//            );
//        }
//
//        if (arucoDetector == null) {
//
//            if (contadorFrames % 60 == 0) {
//                Log.e(TAG, "Detector ArUco está nulo.");
//            }
//
//            return rgbaFrame;
//        }
//
//        List<Mat> corners = new ArrayList<>();
//        List<Mat> rejectedCandidates = new ArrayList<>();
//
//        Mat ids = new Mat();
//
//        try {
//
//            /*
//             * A detecção é feita na imagem em escala de cinza.
//             * O desenho continua sendo feito no frame colorido.
//             */
//            arucoDetector.detectMarkers(
//                    grayFrame,
//                    corners,
//                    ids,
//                    rejectedCandidates
//            );
//
//            if (!ids.empty()) {
//
//                Mat rgbFrame = new Mat();
//
//                try {
//
//                    /*
//                     * drawDetectedMarkers aceita imagens de 1 ou 3 canais.
//                     * O frame da câmera possui 4 canais (RGBA).
//                     */
//                    Imgproc.cvtColor(
//                            rgbaFrame,
//                            rgbFrame,
//                            Imgproc.COLOR_RGBA2RGB
//                    );
//
//                    Scalar corVerde =
//                            new Scalar(0, 255, 0);
//
//                    Objdetect.drawDetectedMarkers(
//                            rgbFrame,
//                            corners,
//                            ids,
//                            corVerde
//                    );
//
//                    /*
//                     * Converte novamente para RGBA, formato esperado
//                     * pelo JavaCameraView.
//                     */
//                    Imgproc.cvtColor(
//                            rgbFrame,
//                            rgbaFrame,
//                            Imgproc.COLOR_RGB2RGBA
//                    );
//
//                    long agora = System.currentTimeMillis();
//
//                    if (agora - ultimoLogDeteccao >= 1000) {
//
//                        ultimoLogDeteccao = agora;
//
//                        Log.d(
//                                TAG,
//                                "Marcadores detectados: "
//                                        + ids.dump()
//                                        + " | rejeitados="
//                                        + rejectedCandidates.size()
//                        );
//                    }
//
//                } finally {
//
//                    rgbFrame.release();
//                }
//
//            } else {
//
//                long agora = System.currentTimeMillis();
//
//                if (agora - ultimoLogDeteccao >= 1000) {
//
//                    ultimoLogDeteccao = agora;
//
//                    Log.d(
//                            TAG,
//                            "Nenhum marcador validado"
//                                    + " | candidatos rejeitados="
//                                    + rejectedCandidates.size()
//                    );
//                }
//            }
//
//        } catch (Throwable throwable) {
//
//            long agora = System.currentTimeMillis();
//
//            if (agora - ultimoLogDeteccao >= 1000) {
//
//                ultimoLogDeteccao = agora;
//
//                Log.e(
//                        TAG,
//                        "Erro durante a detecção ArUco.",
//                        throwable
//                );
//            }
//        } finally {
//
//            ids.release();
//
//            liberarMats(corners);
//            liberarMats(rejectedCandidates);
//        }
//
//        return rgbaFrame;
//    }
//
//    private void liberarMats(List<Mat> mats) {
//
//        for (Mat mat : mats) {
//
//            if (mat != null) {
//                mat.release();
//            }
//        }
//
//        mats.clear();
//    }
//
//    @Override
//    public void onCameraViewStarted(int width, int height) {
//
//        cameraHabilitada = true;
//
//        Log.d(
//                TAG,
//                "Câmera iniciada"
//                        + " | frame=" + width + "x" + height
//                        + " | view=" + cameraBridgeView.getWidth()
//                        + "x" + cameraBridgeView.getHeight()
//        );
//    }
//
//    @Override
//    public void onCameraViewStopped() {
//
//        cameraHabilitada = false;
//
//        Log.d(TAG, "Câmera interrompida.");
//    }
//
//    private boolean possuiPermissaoCamera() {
//
//        return ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.CAMERA
//        ) == PackageManager.PERMISSION_GRANTED;
//    }
//
//    private void checarPermissaoCamera() {
//
//        if (possuiPermissaoCamera()) {
//
//            iniciarCamera();
//
//        } else {
//
//            ActivityCompat.requestPermissions(
//                    this,
//                    new String[]{
//                            Manifest.permission.CAMERA
//                    },
//                    CAMERA_PERMISSION_CODE
//            );
//        }
//    }
//
//    private void iniciarCamera() {
//
//        if (cameraHabilitada) {
//            return;
//        }
//
//        if (!openCvCarregado) {
//
//            Log.e(
//                    TAG,
//                    "A câmera não pode ser iniciada: OpenCV indisponível."
//            );
//
//            return;
//        }
//
//        if (!possuiPermissaoCamera()) {
//
//            Log.w(
//                    TAG,
//                    "A câmera não pode ser iniciada: permissão ausente."
//            );
//
//            return;
//        }
//
//        if (cameraBridgeView != null) {
//
//            cameraBridgeView.setCameraPermissionGranted();
//            cameraBridgeView.enableView();
//
//            cameraHabilitada = true;
//
//            Log.d(TAG, "Visualização da câmera habilitada.");
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(
//            int requestCode,
//            @NonNull String[] permissions,
//            @NonNull int[] grantResults
//    ) {
//
//        super.onRequestPermissionsResult(
//                requestCode,
//                permissions,
//                grantResults
//        );
//
//        if (requestCode != CAMERA_PERMISSION_CODE) {
//            return;
//        }
//
//        if (grantResults.length > 0
//                && grantResults[0]
//                == PackageManager.PERMISSION_GRANTED) {
//
//            iniciarCamera();
//
//        } else {
//
//            Toast.makeText(
//                    this,
//                    "A permissão da câmera é necessária para o leitor.",
//                    Toast.LENGTH_LONG
//            ).show();
//        }
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//
//        if (DEBUG_ATIVAR_CAMERA
//                && openCvCarregado
//                && possuiPermissaoCamera()) {
//
//            iniciarCamera();
//        }
//    }
//
//    @Override
//    protected void onPause() {
//
//        if (cameraBridgeView != null && cameraHabilitada) {
//            cameraBridgeView.disableView();
//            cameraHabilitada = false;
//        }
//
//        super.onPause();
//    }
//
//    @Override
//    protected void onDestroy() {
//
//        if (cameraBridgeView != null && cameraHabilitada) {
//            cameraBridgeView.disableView();
//            cameraHabilitada = false;
//        }
//
//        arucoDetector = null;
//
//        super.onDestroy();
//    }
//
//}