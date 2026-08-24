package com.example.leitorgabaritoomr.vision.fixture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Primeiro teste de imagem fixa do Laboratorio OMR.
 *
 * Confirma que o PNG de regressao pode ser carregado sem camera e
 * convertido para as mesmas representacoes OpenCV usadas pelo app.
 * Ainda nao executa deteccao, registro ou classificacao.
 */
@RunWith(AndroidJUnit4.class)
public final class OmrFixtureImageLoadInstrumentedTest {

    private static final String TAG =
            "OMR_FIXTURE_TEST";

    private static final String ASSET_PATH =
            "omr/gabarito_casos_controlados_v2.png";

    private static final int EXPECTED_WIDTH = 1303;
    private static final int EXPECTED_HEIGHT = 602;

    @BeforeClass
    public static void initializeOpenCv() {
        assertTrue(
                "OpenCV nao foi inicializado no ambiente de teste.",
                OpenCVLoader.initDebug()
        );
    }

    @Test
    public void controlledFixtureLoadsAsRgbaAndGrayMat()
            throws IOException {

        Context testContext =
                InstrumentationRegistry
                        .getInstrumentation()
                        .getContext();

        Bitmap decodedBitmap;

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        try (InputStream inputStream =
                     testContext
                             .getAssets()
                             .open(ASSET_PATH)) {

            decodedBitmap =
                    BitmapFactory.decodeStream(
                            inputStream,
                            null,
                            options
                    );
        }

        assertNotNull(
                "O PNG de regressao nao foi decodificado.",
                decodedBitmap
        );

        Bitmap rgbaBitmap =
                decodedBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                );

        assertNotNull(
                "Nao foi possivel obter Bitmap ARGB_8888.",
                rgbaBitmap
        );

        Mat rgba = new Mat();
        Mat gray = new Mat();

        try {
            assertEquals(
                    EXPECTED_WIDTH,
                    rgbaBitmap.getWidth()
            );

            assertEquals(
                    EXPECTED_HEIGHT,
                    rgbaBitmap.getHeight()
            );

            Utils.bitmapToMat(
                    rgbaBitmap,
                    rgba
            );

            assertFalse(
                    "O Mat RGBA ficou vazio.",
                    rgba.empty()
            );

            assertEquals(
                    EXPECTED_WIDTH,
                    rgba.cols()
            );

            assertEquals(
                    EXPECTED_HEIGHT,
                    rgba.rows()
            );

            assertEquals(
                    CvType.CV_8UC4,
                    rgba.type()
            );

            Imgproc.cvtColor(
                    rgba,
                    gray,
                    Imgproc.COLOR_RGBA2GRAY
            );

            assertFalse(
                    "O Mat em escala de cinza ficou vazio.",
                    gray.empty()
            );

            assertEquals(
                    EXPECTED_WIDTH,
                    gray.cols()
            );

            assertEquals(
                    EXPECTED_HEIGHT,
                    gray.rows()
            );

            assertEquals(
                    CvType.CV_8UC1,
                    gray.type()
            );

            Core.MinMaxLocResult minMax =
                    Core.minMaxLoc(gray);

            assertTrue(
                    "A imagem nao preservou regioes escuras.",
                    minMax.minVal < 40.0
            );

            assertTrue(
                    "A imagem nao preservou regioes claras.",
                    minMax.maxVal > 200.0
            );

            Log.i(
                    TAG,
                    String.format(
                            Locale.US,
                            "FIXTURE_OK | path=%s | size=%dx%d"
                                    + " | rgbaType=%d | grayType=%d"
                                    + " | grayMin=%.1f | grayMax=%.1f",
                            ASSET_PATH,
                            rgba.cols(),
                            rgba.rows(),
                            rgba.type(),
                            gray.type(),
                            minMax.minVal,
                            minMax.maxVal
                    )
            );

        } finally {
            rgba.release();
            gray.release();

            rgbaBitmap.recycle();
            decodedBitmap.recycle();
        }
    }
}
