package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.model.DetectedMarker;
import com.example.leitorgabaritoomr.vision.model.MarkerDetectionResult;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public final class MarkerOverlayRenderer {

    private static final Scalar COLOR_GREEN =
            new Scalar(0, 255, 0, 255);

    private static final Scalar COLOR_YELLOW =
            new Scalar(255, 255, 0, 255);

    public void draw(
            Mat rgbaFrame,
            MarkerDetectionResult result
    ) {

        if (rgbaFrame == null
                || rgbaFrame.empty()
                || result == null) {

            return;
        }

        for (DetectedMarker marker : result.getMarkers()) {

            Point[] corners = marker.getCorners();

            for (int index = 0;
                 index < corners.length;
                 index++) {

                Point start = corners[index];

                Point end =
                        corners[
                                (index + 1)
                                        % corners.length
                                ];

                Imgproc.line(
                        rgbaFrame,
                        start,
                        end,
                        COLOR_GREEN,
                        3
                );
            }

            Point center = marker.getCenter();

            Imgproc.circle(
                    rgbaFrame,
                    center,
                    5,
                    COLOR_YELLOW,
                    -1
            );

            String label =
                    marker.getCode() == null
                            ? marker.getType().name()
                            : "ID " + marker.getCode();

            Imgproc.putText(
                    rgbaFrame,
                    label,
                    new Point(
                            corners[0].x,
                            Math.max(
                                    20,
                                    corners[0].y - 10
                            )
                    ),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    COLOR_GREEN,
                    2
            );
        }
    }
}