package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.geometry.CornerRole;
import com.example.leitorgabaritoomr.vision.geometry.MarkerSetResolutionResult;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarker;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

public final class ResolvedMarkerOverlayRenderer {

    private static final Scalar COLOR_RESOLVED =
            new Scalar(255, 0, 255, 255);

    private static final Scalar COLOR_CONNECTION =
            new Scalar(0, 170, 255, 255);

    private static final Scalar COLOR_CENTER =
            new Scalar(255, 255, 0, 255);

    private static final Scalar COLOR_ACCEPTED =
            new Scalar(0, 255, 0, 255);

    private static final Scalar COLOR_REJECTED =
            new Scalar(255, 60, 60, 255);

    public void draw(
            Mat rgbaFrame,
            MarkerSetResolutionResult result
    ) {

        if (rgbaFrame == null
                || rgbaFrame.empty()
                || result == null) {

            return;
        }

        if (!result.isAccepted()
                || result.getMarkerSet() == null) {

            drawRejectedStatus(
                    rgbaFrame,
                    result
            );

            return;
        }

        ResolvedMarkerSet markerSet =
                result.getMarkerSet();

        ResolvedMarker topLeft =
                markerSet.get(
                        CornerRole.TOP_LEFT
                );

        ResolvedMarker topRight =
                markerSet.get(
                        CornerRole.TOP_RIGHT
                );

        ResolvedMarker bottomRight =
                markerSet.get(
                        CornerRole.BOTTOM_RIGHT
                );

        ResolvedMarker bottomLeft =
                markerSet.get(
                        CornerRole.BOTTOM_LEFT
                );

        /*
         * Liga os centros dos quatro marcadores.
         */
        drawConnection(
                rgbaFrame,
                topLeft.getCenter(),
                topRight.getCenter()
        );

        drawConnection(
                rgbaFrame,
                topRight.getCenter(),
                bottomRight.getCenter()
        );

        drawConnection(
                rgbaFrame,
                bottomRight.getCenter(),
                bottomLeft.getCenter()
        );

        drawConnection(
                rgbaFrame,
                bottomLeft.getCenter(),
                topLeft.getCenter()
        );

        drawResolvedMarker(
                rgbaFrame,
                topLeft
        );

        drawResolvedMarker(
                rgbaFrame,
                topRight
        );

        drawResolvedMarker(
                rgbaFrame,
                bottomRight
        );

        drawResolvedMarker(
                rgbaFrame,
                bottomLeft
        );

        drawAcceptedStatus(
                rgbaFrame,
                markerSet,
                result
        );
    }

    private void drawResolvedMarker(
            Mat rgbaFrame,
            ResolvedMarker resolvedMarker
    ) {

        Point[] corners =
                resolvedMarker
                        .getMarker()
                        .getCorners();

        for (int index = 0;
             index < corners.length;
             index++) {

            Point start =
                    corners[index];

            Point end =
                    corners[
                            (index + 1)
                                    % corners.length
                            ];

            Imgproc.line(
                    rgbaFrame,
                    start,
                    end,
                    COLOR_RESOLVED,
                    6
            );
        }

        Point center =
                resolvedMarker.getCenter();

        Imgproc.circle(
                rgbaFrame,
                center,
                8,
                COLOR_CENTER,
                -1
        );

        Imgproc.putText(
                rgbaFrame,
                resolvedMarker
                        .getRole()
                        .getShortLabel(),
                new Point(
                        center.x + 10,
                        Math.max(
                                25,
                                center.y - 10
                        )
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.85,
                COLOR_RESOLVED,
                3
        );
    }

    private void drawConnection(
            Mat rgbaFrame,
            Point start,
            Point end
    ) {

        Imgproc.line(
                rgbaFrame,
                start,
                end,
                COLOR_CONNECTION,
                4
        );
    }

    private void drawAcceptedStatus(
            Mat rgbaFrame,
            ResolvedMarkerSet markerSet,
            MarkerSetResolutionResult result
    ) {

        String text =
                String.format(
                        Locale.US,
                        "SET ACCEPTED | score=%.3f"
                                + " | area=%.3f"
                                + " | size=%.3f"
                                + " | inside=%.3f"
                                + " | gap=%.3f",
                        markerSet.getConfidence(),
                        markerSet.getRegionAreaRatio(),
                        markerSet.getSizeSimilarity(),
                        markerSet.getContainmentRatio(),
                        result.getScoreDifference()
                );

        Imgproc.putText(
                rgbaFrame,
                text,
                new Point(
                        20,
                        Math.max(
                                30,
                                rgbaFrame.rows() - 25
                        )
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.55,
                COLOR_ACCEPTED,
                2
        );
    }

    private void drawRejectedStatus(
            Mat rgbaFrame,
            MarkerSetResolutionResult result
    ) {

        String text =
                String.format(
                        Locale.US,
                        "SET REJECTED"
                                + " | score=%.3f"
                                + " | gap=%.3f"
                                + " | combinations=%d",
                        result.getBestScore(),
                        result.getScoreDifference(),
                        result.getEvaluatedCombinations()
                );

        Imgproc.putText(
                rgbaFrame,
                text,
                new Point(
                        20,
                        Math.max(
                                30,
                                rgbaFrame.rows() - 25
                        )
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.55,
                COLOR_REJECTED,
                2
        );
    }
}