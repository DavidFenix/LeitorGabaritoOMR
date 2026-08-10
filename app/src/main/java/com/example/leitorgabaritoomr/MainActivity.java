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

public class MainActivity extends AppCompatActivity
        implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OMR_Camera";
    private static final int CAMERA_PERMISSION_CODE = 100;

    private CameraBridgeViewBase cameraBridgeView;
    private ArucoDetector arucoDetector;

    private boolean openCvCarregado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        cameraBridgeView = findViewById(R.id.cameraViewLeitor);
        cameraBridgeView.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeView.setCvCameraViewListener(this);

        inicializarOpenCV();
        checarPermissaoCamera();
    }

    private void inicializarOpenCV() {

        openCvCarregado = OpenCVLoader.initDebug();

        if (openCvCarregado) {

            Log.d(TAG, "OpenCV carregado com sucesso!");

            configurarDetectorAruco();

        } else {

            Log.e(TAG, "Não foi possível carregar o OpenCV.");

            Toast.makeText(
                    this,
                    "Não foi possível inicializar o OpenCV.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void configurarDetectorAruco() {

        try {

            // Família de marcadores ArUco utilizada pelo leitor.
            Dictionary dictionary =
                    Objdetect.getPredefinedDictionary(
                            Objdetect.DICT_4X4_50
                    );

            DetectorParameters parameters =
                    new DetectorParameters();

            arucoDetector =
                    new ArucoDetector(
                            dictionary,
                            parameters
                    );

            Log.d(TAG, "Detector ArUco configurado com sucesso.");

        } catch (Exception exception) {

            arucoDetector = null;

            Log.e(
                    TAG,
                    "Erro ao configurar o detector ArUco.",
                    exception
            );

            Toast.makeText(
                    this,
                    "Erro ao configurar o detector ArUco.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public Mat onCameraFrame(
            CameraBridgeViewBase.CvCameraViewFrame inputFrame
    ) {

        Mat rgbaFrame = inputFrame.rgba();

        if (arucoDetector == null) {
            return rgbaFrame;
        }

        List<Mat> corners = new ArrayList<>();
        List<Mat> rejectedCandidates = new ArrayList<>();

        Mat ids = new Mat();

        try {

            arucoDetector.detectMarkers(
                    rgbaFrame,
                    corners,
                    ids,
                    rejectedCandidates
            );

            if (!ids.empty()) {

                /*
                 * Como o frame é RGBA:
                 * R = 0
                 * G = 255
                 * B = 0
                 * A = 255
                 */
                Scalar corVerde =
                        new Scalar(0, 255, 0, 255);

                Objdetect.drawDetectedMarkers(
                        rgbaFrame,
                        corners,
                        ids,
                        corVerde
                );

                Log.d(
                        TAG,
                        "Marcadores detectados: " + ids.dump()
                );
            }

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Erro durante a detecção dos marcadores.",
                    exception
            );

        } finally {

            /*
             * Os objetos Mat usam memória nativa.
             * Como são criados a cada frame, precisam ser liberados.
             */
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

        Log.d(
                TAG,
                "Câmera iniciada: " + width + "x" + height
        );
    }

    @Override
    public void onCameraViewStopped() {

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

            /*
             * Informa ao componente do OpenCV que a permissão
             * da câmera já foi concedida.
             */
            cameraBridgeView.setCameraPermissionGranted();
            cameraBridgeView.enableView();

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

        /*
         * Quando o usuário volta para a Activity, a câmera precisa
         * ser habilitada novamente.
         */
        if (openCvCarregado && possuiPermissaoCamera()) {
            iniciarCamera();
        }
    }

    @Override
    protected void onPause() {

        if (cameraBridgeView != null) {
            cameraBridgeView.disableView();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        if (cameraBridgeView != null) {
            cameraBridgeView.disableView();
        }

        arucoDetector = null;

        super.onDestroy();
    }
}
//package com.example.leitorgabaritoomr;
//
//import android.os.Bundle;
//import android.util.Log;
//import android.view.View;
//import android.widget.Button;
//import android.widget.TextView;
//
//import androidx.activity.EdgeToEdge;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.content.ContextCompat;
//
//import org.opencv.android.OpenCVLoader;
//
//public class MainActivity extends AppCompatActivity {
//
//    private static final String TAG = "OpenCVTest";
//
//    private TextView tvStatusOpenCV;
//    private Button btnTestarOpenCV;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//
//        tvStatusOpenCV = findViewById(R.id.tvStatusOpenCV);
//        btnTestarOpenCV = findViewById(R.id.btnTestarOpenCV);
//
//        btnTestarOpenCV.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                verificarOpenCV();
//            }
//        });
//    }
//
//    private void verificarOpenCV() {
//
//        if (OpenCVLoader.initDebug()) {
//
//            Log.d(TAG, "OpenCV carregado com sucesso!");
//
//            tvStatusOpenCV.setText(
//                    "✅ OpenCV inicializado com sucesso!"
//            );
//
//            tvStatusOpenCV.setTextColor(
//                    ContextCompat.getColor(
//                            this,
//                            android.R.color.holo_green_dark
//                    )
//            );
//
//        } else {
//
//            Log.e(TAG, "Falha ao carregar o OpenCV.");
//
//            tvStatusOpenCV.setText(
//                    "❌ Erro ao carregar o OpenCV."
//            );
//
//            tvStatusOpenCV.setTextColor(
//                    ContextCompat.getColor(
//                            this,
//                            android.R.color.holo_red_dark
//                    )
//            );
//        }
//    }
//}