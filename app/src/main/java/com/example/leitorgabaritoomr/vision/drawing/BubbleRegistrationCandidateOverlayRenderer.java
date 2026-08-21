package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.registration.BubbleContourCandidate;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractionResult;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;

/**
 * Mostra exatamente os candidatos produzidos pelo
 * BubbleContourExtractor.
 *
 * Magenta = contorno realmente aceito pelo extrator.
 * Ciano   = centro calculado para esse mesmo contorno.
 *
 * Nenhuma geometria é criada apenas para decoração.
 */
public final class
BubbleRegistrationCandidateOverlayRenderer {

    private static final Scalar CONTOUR_COLOR =
            new Scalar(
                    255.0,
                    0.0,
                    255.0,
                    255.0
            );

    private static final Scalar CENTER_COLOR =
            new Scalar(
                    255.0,
                    255.0,
                    0.0,
                    255.0
            );

    private static final Scalar TEXT_COLOR =
            new Scalar(
                    0.0,
                    255.0,
                    0.0,
                    255.0
            );

    public void draw(
            Mat normalizedRegion,
            BubbleContourExtractionResult result
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || result == null) {

            return;
        }

        for (BubbleContourCandidate candidate
                : result.getCandidates()) {

            drawCandidate(
                    normalizedRegion,
                    candidate
            );
        }

        drawSummary(
                normalizedRegion,
                result
        );
    }

    private void drawCandidate(
            Mat image,
            BubbleContourCandidate candidate
    ) {
        Point[] contourPoints =
                candidate.copyContourPoints();

        if (contourPoints.length >= 2) {
            MatOfPoint contour =
                    new MatOfPoint(
                            contourPoints
                    );

            try {
                Imgproc.drawContours(
                        image,
                        Collections.singletonList(
                                contour
                        ),
                        -1,
                        CONTOUR_COLOR,
                        2
                );
            } finally {
                contour.release();
            }
        }

        /*
         * Este é o centro armazenado no candidato.
         * Não recalculamos um centro apenas para desenhar.
         */
        Imgproc.circle(
                image,
                new Point(
                        candidate.getCenterX(),
                        candidate.getCenterY()
                ),
                3,
                CENTER_COLOR,
                -1
        );
    }

    private void drawSummary(
            Mat image,
            BubbleContourExtractionResult result
    ) {
        String text =
                "contornos aceitos: "
                        + result.getCandidates().size();

        int textY =
                Math.max(
                        24,
                        image.rows() - 16
                );

        Imgproc.putText(
                image,
                text,
                new Point(12, textY),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.55,
                TEXT_COLOR,
                2
        );
    }
}