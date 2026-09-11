package com.example.leitorgabaritoomr.vision.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.leitorgabaritoomr.vision.model.DetectedMarker;
import com.example.leitorgabaritoomr.vision.model.MarkerType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.core.Point;

import java.util.Arrays;
import java.util.List;

/**
 * Protege a atribuicao visual dos quatro papeis geometricos.
 *
 * Como quadrados solidos nao possuem identidade, uma mesma moldura nao
 * pode concorrer novamente espelhada ou com os papeis dos cantos girados.
 */
@RunWith(AndroidJUnit4.class)
public final class
MarkerSetResolverCornerRoleInstrumentedTest {

    private static final int FRAME_WIDTH = 1265;
    private static final int FRAME_HEIGHT = 565;

    @Test
    public void symmetricRectangleHasOnlyOneCoherentAssignment() {
        DetectedMarker topLeft = marker(32.0, 32.0, 36.0);
        DetectedMarker topRight = marker(1232.0, 32.0, 36.0);
        DetectedMarker bottomRight = marker(1232.0, 532.0, 36.0);
        DetectedMarker bottomLeft = marker(32.0, 532.0, 36.0);

        MarkerSetResolutionResult result =
                new MarkerSetResolver().resolve(
                        Arrays.asList(
                                topLeft,
                                topRight,
                                bottomRight,
                                bottomLeft
                        ),
                        FRAME_WIDTH,
                        FRAME_HEIGHT
                );

        assertTrue(result.getReason(), result.isAccepted());
        assertEquals(1, result.getEvaluatedCombinations());
        assertNull(result.getSecondBestCandidateEvaluation());

        assertResolvedMarker(
                result,
                CornerRole.TOP_LEFT,
                topLeft
        );

        assertResolvedMarker(
                result,
                CornerRole.TOP_RIGHT,
                topRight
        );

        assertResolvedMarker(
                result,
                CornerRole.BOTTOM_RIGHT,
                bottomRight
        );

        assertResolvedMarker(
                result,
                CornerRole.BOTTOM_LEFT,
                bottomLeft
        );
    }

    @Test
    public void inputOrderDoesNotChangeCornerRoles() {
        DetectedMarker topLeft = marker(32.0, 32.0, 36.0);
        DetectedMarker topRight = marker(1232.0, 32.0, 36.0);
        DetectedMarker bottomRight = marker(1232.0, 532.0, 36.0);
        DetectedMarker bottomLeft = marker(32.0, 532.0, 36.0);

        List<DetectedMarker> shuffled =
                Arrays.asList(
                        bottomRight,
                        topLeft,
                        bottomLeft,
                        topRight
                );

        MarkerSetResolutionResult result =
                new MarkerSetResolver().resolve(
                        shuffled,
                        FRAME_WIDTH,
                        FRAME_HEIGHT
                );

        assertTrue(result.getReason(), result.isAccepted());
        assertEquals(1, result.getEvaluatedCombinations());

        assertResolvedMarker(
                result,
                CornerRole.TOP_LEFT,
                topLeft
        );

        assertResolvedMarker(
                result,
                CornerRole.TOP_RIGHT,
                topRight
        );

        assertResolvedMarker(
                result,
                CornerRole.BOTTOM_RIGHT,
                bottomRight
        );

        assertResolvedMarker(
                result,
                CornerRole.BOTTOM_LEFT,
                bottomLeft
        );
    }

    @Test
    public void perspectiveAndInteriorDistractorsPreserveOuterFrame() {
        DetectedMarker topLeft = marker(82.0, 58.0, 38.0);
        DetectedMarker topRight = marker(1168.0, 86.0, 37.0);
        DetectedMarker bottomRight = marker(1202.0, 510.0, 39.0);
        DetectedMarker bottomLeft = marker(54.0, 535.0, 36.0);

        DetectedMarker interiorOne =
                marker(300.0, 180.0, 28.0);

        DetectedMarker interiorTwo =
                marker(900.0, 370.0, 27.0);

        MarkerSetResolutionResult result =
                new MarkerSetResolver().resolve(
                        Arrays.asList(
                                interiorTwo,
                                bottomLeft,
                                topRight,
                                interiorOne,
                                bottomRight,
                                topLeft
                        ),
                        FRAME_WIDTH,
                        FRAME_HEIGHT
                );

        assertTrue(result.getReason(), result.isAccepted());

        assertResolvedMarker(
                result,
                CornerRole.TOP_LEFT,
                topLeft
        );

        assertResolvedMarker(
                result,
                CornerRole.TOP_RIGHT,
                topRight
        );

        assertResolvedMarker(
                result,
                CornerRole.BOTTOM_RIGHT,
                bottomRight
        );

        assertResolvedMarker(
                result,
                CornerRole.BOTTOM_LEFT,
                bottomLeft
        );
    }

    private static DetectedMarker marker(
            double centerX,
            double centerY,
            double side
    ) {
        double half = side / 2.0;

        return new DetectedMarker(
                MarkerType.SOLID_SQUARE,
                null,
                new Point[]{
                        new Point(
                                centerX - half,
                                centerY - half
                        ),
                        new Point(
                                centerX + half,
                                centerY - half
                        ),
                        new Point(
                                centerX + half,
                                centerY + half
                        ),
                        new Point(
                                centerX - half,
                                centerY + half
                        )
                },
                1.0
        );
    }

    private static void assertResolvedMarker(
            MarkerSetResolutionResult result,
            CornerRole role,
            DetectedMarker expected
    ) {
        assertNotNull(result.getMarkerSet());

        ResolvedMarker actual =
                result.getMarkerSet().get(role);

        assertNotNull(actual);
        assertTrue(actual.getMarker() == expected);
    }
}
