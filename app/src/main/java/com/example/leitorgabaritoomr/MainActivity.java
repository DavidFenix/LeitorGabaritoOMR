package com.example.leitorgabaritoomr;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "OpenCVTest";

    private TextView tvStatusOpenCV;
    private Button btnTestarOpenCV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        tvStatusOpenCV = findViewById(R.id.tvStatusOpenCV);
        btnTestarOpenCV = findViewById(R.id.btnTestarOpenCV);

        btnTestarOpenCV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                verificarOpenCV();
            }
        });
    }

    private void verificarOpenCV() {

        if (OpenCVLoader.initDebug()) {

            Log.d(TAG, "OpenCV carregado com sucesso!");

            tvStatusOpenCV.setText(
                    "✅ OpenCV inicializado com sucesso!"
            );

            tvStatusOpenCV.setTextColor(
                    ContextCompat.getColor(
                            this,
                            android.R.color.holo_green_dark
                    )
            );

        } else {

            Log.e(TAG, "Falha ao carregar o OpenCV.");

            tvStatusOpenCV.setText(
                    "❌ Erro ao carregar o OpenCV."
            );

            tvStatusOpenCV.setTextColor(
                    ContextCompat.getColor(
                            this,
                            android.R.color.holo_red_dark
                    )
            );
        }
    }
}