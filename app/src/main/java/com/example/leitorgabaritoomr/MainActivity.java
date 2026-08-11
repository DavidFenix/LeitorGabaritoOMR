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

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.objdetect.ArucoDetector;
import org.opencv.objdetect.DetectorParameters;
import org.opencv.objdetect.Dictionary;
import org.opencv.objdetect.Objdetect;

import java.util.ArrayList;
import java.util.List;

import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final boolean DEBUG_ATIVAR_CAMERA = true;
    private volatile boolean cameraHabilitada = false;

    private long ultimoLogDeteccao = 0;

    private static final String TAG = "OMR_Camera";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private CameraBridgeViewBase cameraBridgeView;
    private ArucoDetector arucoDetector;

    private boolean openCvCarregado = false;

    private long contadorFrames = 0;
    private boolean primeiroFrameRegistrado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        cameraBridgeView =
                findViewById(R.id.cameraViewLeitor);

        cameraBridgeView.setMaxFrameSize(
                1920,
                1440
        );

        cameraBridgeView.setVisibility(
                SurfaceView.VISIBLE
        );

        cameraBridgeView.setCvCameraViewListener(this);

        inicializarOpenCV();

        if (DEBUG_ATIVAR_CAMERA) {
            checarPermissaoCamera();
        }
    }

    private void inicializarOpenCV() {

        try {

            openCvCarregado = OpenCVLoader.initDebug();

            Log.d(
                    TAG,
                    "OPEN_CV_ETAPA_3: initDebug() terminou. Resultado = "
                            + openCvCarregado
            );

            if (!openCvCarregado) {

                Log.e(TAG, "OPEN_CV_ERRO: initDebug() retornou false.");

                Toast.makeText(
                        this,
                        "Não foi possível inicializar o OpenCV.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            Log.d(TAG, "OPEN_CV_ETAPA_4: OpenCV carregado.");

            configurarDetectorAruco();

            Log.d(TAG, "OPEN_CV_ETAPA_5: inicialização concluída.");

        } catch (Throwable throwable) {

            openCvCarregado = false;
            arucoDetector = null;

            Log.e(
                    TAG,
                    "OPEN_CV_ERRO_FATAL: falha durante a inicialização.",
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

    private void configurarDetectorAruco() {

        Log.d(TAG, "ARUCO_ETAPA_1: iniciando configuração.");

        try {

            Log.d(TAG, "ARUCO_ETAPA_2: obtendo dicionário.");

            Dictionary dictionary =
                    Objdetect.getPredefinedDictionary(
                            Objdetect.DICT_4X4_50
                    );

            Log.d(TAG, "ARUCO_ETAPA_3: criando parâmetros.");

            DetectorParameters parameters =
                    new DetectorParameters();

            Log.d(TAG, "ARUCO_ETAPA_4: criando detector.");

            arucoDetector =
                    new ArucoDetector(
                            dictionary,
                            parameters
                    );

            Log.d(TAG, "ARUCO_ETAPA_5: detector criado.");

        } catch (Throwable throwable) {

            arucoDetector = null;

            Log.e(
                    TAG,
                    "ARUCO_ERRO: falha ao configurar o detector.",
                    throwable
            );

            Toast.makeText(
                    this,
                    "Erro ao configurar ArUco: "
                            + throwable.getClass().getSimpleName(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public Mat onCameraFrame(
            CameraBridgeViewBase.CvCameraViewFrame inputFrame
    ) {

        Mat rgbaFrame = inputFrame.rgba();
        Mat grayFrame = inputFrame.gray();

        contadorFrames++;

        if (!primeiroFrameRegistrado) {

            primeiroFrameRegistrado = true;

            Log.d(
                    TAG,
                    "Primeiro frame recebido"
                            + " | RGBA=" + rgbaFrame.cols()
                            + "x" + rgbaFrame.rows()
                            + " canais=" + rgbaFrame.channels()
                            + " | GRAY=" + grayFrame.cols()
                            + "x" + grayFrame.rows()
                            + " canais=" + grayFrame.channels()
            );
        }

        if (arucoDetector == null) {

            if (contadorFrames % 60 == 0) {
                Log.e(TAG, "Detector ArUco está nulo.");
            }

            return rgbaFrame;
        }

        List<Mat> corners = new ArrayList<>();
        List<Mat> rejectedCandidates = new ArrayList<>();

        Mat ids = new Mat();

        try {

            /*
             * A detecção é feita na imagem em escala de cinza.
             * O desenho continua sendo feito no frame colorido.
             */
            arucoDetector.detectMarkers(
                    grayFrame,
                    corners,
                    ids,
                    rejectedCandidates
            );

            if (!ids.empty()) {

                Mat rgbFrame = new Mat();

                try {

                    /*
                     * drawDetectedMarkers aceita imagens de 1 ou 3 canais.
                     * O frame da câmera possui 4 canais (RGBA).
                     */
                    Imgproc.cvtColor(
                            rgbaFrame,
                            rgbFrame,
                            Imgproc.COLOR_RGBA2RGB
                    );

                    Scalar corVerde =
                            new Scalar(0, 255, 0);

                    Objdetect.drawDetectedMarkers(
                            rgbFrame,
                            corners,
                            ids,
                            corVerde
                    );

                    /*
                     * Converte novamente para RGBA, formato esperado
                     * pelo JavaCameraView.
                     */
                    Imgproc.cvtColor(
                            rgbFrame,
                            rgbaFrame,
                            Imgproc.COLOR_RGB2RGBA
                    );

                    long agora = System.currentTimeMillis();

                    if (agora - ultimoLogDeteccao >= 1000) {

                        ultimoLogDeteccao = agora;

                        Log.d(
                                TAG,
                                "Marcadores detectados: "
                                        + ids.dump()
                                        + " | rejeitados="
                                        + rejectedCandidates.size()
                        );
                    }

                } finally {

                    rgbFrame.release();
                }

            } else {

                long agora = System.currentTimeMillis();

                if (agora - ultimoLogDeteccao >= 1000) {

                    ultimoLogDeteccao = agora;

                    Log.d(
                            TAG,
                            "Nenhum marcador validado"
                                    + " | candidatos rejeitados="
                                    + rejectedCandidates.size()
                    );
                }
            }

        } catch (Throwable throwable) {

            long agora = System.currentTimeMillis();

            if (agora - ultimoLogDeteccao >= 1000) {

                ultimoLogDeteccao = agora;

                Log.e(
                        TAG,
                        "Erro durante a detecção ArUco.",
                        throwable
                );
            }
        } finally {

            ids.release();

            liberarMats(corners);
            liberarMats(rejectedCandidates);
        }

        return rgbaFrame;
    }

    private void liberarMats(List<Mat> mats) {

        for (Mat mat : mats) {

            if (mat != null) {
                mat.release();
            }
        }

        mats.clear();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

        cameraHabilitada = true;

        Log.d(
                TAG,
                "Câmera iniciada"
                        + " | frame=" + width + "x" + height
                        + " | view=" + cameraBridgeView.getWidth()
                        + "x" + cameraBridgeView.getHeight()
        );
    }

    @Override
    public void onCameraViewStopped() {

        cameraHabilitada = false;

        Log.d(TAG, "Câmera interrompida.");
    }

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
            return;
        }

        if (!openCvCarregado) {

            Log.e(
                    TAG,
                    "A câmera não pode ser iniciada: OpenCV indisponível."
            );

            return;
        }

        if (!possuiPermissaoCamera()) {

            Log.w(
                    TAG,
                    "A câmera não pode ser iniciada: permissão ausente."
            );

            return;
        }

        if (cameraBridgeView != null) {

            cameraBridgeView.setCameraPermissionGranted();
            cameraBridgeView.enableView();

            cameraHabilitada = true;

            Log.d(TAG, "Visualização da câmera habilitada.");
        }
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

        if (cameraBridgeView != null && cameraHabilitada) {
            cameraBridgeView.disableView();
            cameraHabilitada = false;
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (cameraBridgeView != null && cameraHabilitada) {
            cameraBridgeView.disableView();
            cameraHabilitada = false;
        }

        arucoDetector = null;

        super.onDestroy();
    }

}