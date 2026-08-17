package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.geometry.CornerRole;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarker;
import com.example.leitorgabaritoomr.vision.geometry.ResolvedMarkerSet;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityResult;
import com.example.leitorgabaritoomr.vision.stability.MarkerStabilityState;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

public final class StableMarkerOverlayRenderer {

    private static final Scalar COLOR_STABLE =
            new Scalar(0, 255, 0, 255);

    private static final Scalar COLOR_ACCUMULATING =
            new Scalar(255, 210, 0, 255);

    private static final Scalar COLOR_HELD =
            new Scalar(0, 255, 255, 255);

    private static final Scalar COLOR_LOST =
            new Scalar(255, 50, 50, 255);

    private static final Scalar COLOR_CENTER =
            new Scalar(255, 0, 255, 255);

    public void draw(
            Mat rgbaFrame,
            MarkerStabilityResult result
    ) {

        if (rgbaFrame == null
                || rgbaFrame.empty()
                || result == null) {

            return;
        }

        Scalar stateColor =
                getStateColor(
                        result.getState()
                );

        if (result.getMarkerSet() != null) {

            drawMarkerSet(
                    rgbaFrame,
                    result.getMarkerSet(),
                    stateColor,
                    result.isStable() ? 6 : 3
            );
        }

        drawStatus(
                rgbaFrame,
                result,
                stateColor
        );
    }

    private void drawMarkerSet(
            Mat rgbaFrame,
            ResolvedMarkerSet markerSet,
            Scalar color,
            int thickness
    ) {

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

        drawConnection(
                rgbaFrame,
                topLeft.getCenter(),
                topRight.getCenter(),
                color,
                thickness
        );

        drawConnection(
                rgbaFrame,
                topRight.getCenter(),
                bottomRight.getCenter(),
                color,
                thickness
        );

        drawConnection(
                rgbaFrame,
                bottomRight.getCenter(),
                bottomLeft.getCenter(),
                color,
                thickness
        );

        drawConnection(
                rgbaFrame,
                bottomLeft.getCenter(),
                topLeft.getCenter(),
                color,
                thickness
        );

        drawMarker(
                rgbaFrame,
                topLeft,
                color,
                thickness
        );

        drawMarker(
                rgbaFrame,
                topRight,
                color,
                thickness
        );

        drawMarker(
                rgbaFrame,
                bottomRight,
                color,
                thickness
        );

        drawMarker(
                rgbaFrame,
                bottomLeft,
                color,
                thickness
        );
    }

    private void drawMarker(
            Mat rgbaFrame,
            ResolvedMarker resolvedMarker,
            Scalar color,
            int thickness
    ) {

        Point[] corners =
                resolvedMarker
                        .getMarker()
                        .getCorners();

        for (int index = 0;
             index < corners.length;
             index++) {

            Imgproc.line(
                    rgbaFrame,
                    corners[index],
                    corners[
                            (index + 1)
                                    % corners.length
                            ],
                    color,
                    thickness
            );
        }

        Point center =
                resolvedMarker.getCenter();

        Imgproc.circle(
                rgbaFrame,
                center,
                resultCenterRadius(thickness),
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
                0.8,
                color,
                Math.max(2, thickness / 2)
        );
    }

    private int resultCenterRadius(
            int thickness
    ) {

        return thickness >= 6 ? 9 : 6;
    }

    private void drawConnection(
            Mat rgbaFrame,
            Point start,
            Point end,
            Scalar color,
            int thickness
    ) {

        Imgproc.line(
                rgbaFrame,
                start,
                end,
                color,
                thickness
        );
    }

    private void drawStatus(
            Mat rgbaFrame,
            MarkerStabilityResult result,
            Scalar color
    ) {

        String text;

        switch (result.getState()) {

            case SEARCHING:

                text = "SEARCHING";
                break;

            case ACCUMULATING:

                text = String.format(
                        Locale.US,
                        "ACCUMULATING %d/%d"
                                + " | shape=%.3f"
                                + " | areaChange=%.3f",
                        result.getConsistentFrames(),
                        result.getRequiredFrames(),
                        result.getNormalizedShapeDistance(),
                        result.getRegionAreaChangeRatio()
                );

                break;

            case STABLE:

                text = String.format(
                        Locale.US,
                        "STABLE %d/%d"
                                + " | shape=%.3f"
                                + " | areaChange=%.3f",
                        result.getConsistentFrames(),
                        result.getRequiredFrames(),
                        result.getNormalizedShapeDistance(),
                        result.getRegionAreaChangeRatio()
                );

                break;

            case HELD_STABLE:

                text = String.format(
                        Locale.US,
                        "HELD STABLE"
                                + " | misses=%d",
                        result.getMissedFrames()
                );

                break;

            case LOST:

                text = "STABILITY LOST";
                break;

            default:

                text = result.getState().name();
                break;
        }

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
                0.65,
                color,
                2
        );
    }

    private Scalar getStateColor(
            MarkerStabilityState state
    ) {

        switch (state) {

            case STABLE:
                return COLOR_STABLE;

            case HELD_STABLE:
                return COLOR_HELD;

            case ACCUMULATING:
                return COLOR_ACCUMULATING;

            case SEARCHING:
            case LOST:
            default:
                return COLOR_LOST;
        }
    }
}