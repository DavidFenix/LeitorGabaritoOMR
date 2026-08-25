//package com.example.leitorgabaritoomr.vision.drawing;
//
//import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
//import com.example.leitorgabaritoomr.vision.registration.BubbleCandidateMatch;
//import com.example.leitorgabaritoomr.vision.registration.BubbleCandidateMatchingResult;
//import com.example.leitorgabaritoomr.vision.registration.BubbleContourCandidate;
//import com.example.leitorgabaritoomr.vision.registration.ExpectedBubbleTarget;
//
//import org.opencv.core.Mat;
//import org.opencv.core.MatOfPoint;
//import org.opencv.core.Point;
//import org.opencv.core.Scalar;
//import org.opencv.imgproc.Imgproc;
//
//import java.util.Collections;
//import java.util.Locale;
//
///**
// * Desenha exatamente o resultado produzido pelo associador.
// *
// * Cinza   = candidato extraído, mas não escolhido.
// * Amarelo = região esperada de um alvo associado.
// * Vermelho = alvo esperado ainda sem candidato.
// * Magenta = contorno realmente escolhido.
// * Ciano   = centro observado do candidato escolhido.
// * Azul    = deslocamento esperado -> observado.
// */
//public final class
//BubbleCandidateMatchingOverlayRenderer {
//
//    private static final Scalar
//            UNMATCHED_CANDIDATE_COLOR =
//            new Scalar(
//                    96.0,
//                    96.0,
//                    96.0,
//                    255.0
//            );
//
//    private static final Scalar
//            MATCHED_EXPECTED_COLOR =
//            new Scalar(
//                    255.0,
//                    255.0,
//                    0.0,
//                    255.0
//            );
//
//    private static final Scalar
//            UNMATCHED_EXPECTED_COLOR =
//            new Scalar(
//                    255.0,
//                    0.0,
//                    0.0,
//                    255.0
//            );
//
//    private static final Scalar
//            MATCHED_CONTOUR_COLOR =
//            new Scalar(
//                    255.0,
//                    0.0,
//                    255.0,
//                    255.0
//            );
//
//    private static final Scalar
//            OBSERVED_CENTER_COLOR =
//            new Scalar(
//                    0.0,
//                    255.0,
//                    255.0,
//                    255.0
//            );
//
//    private static final Scalar
//            OFFSET_LINE_COLOR =
//            new Scalar(
//                    0.0,
//                    96.0,
//                    255.0,
//                    255.0
//            );
//
//    private static final Scalar
//            SUMMARY_COLOR =
//            new Scalar(
//                    0.0,
//                    255.0,
//                    0.0,
//                    255.0
//            );
//
//    public void draw(
//            Mat normalizedRegion,
//            BubbleCandidateMatchingResult result
//    ) {
//        if (normalizedRegion == null
//                || normalizedRegion.empty()
//                || result == null) {
//
//            return;
//        }
//
//        if (!result.isSuccess()) {
//            drawFailure(
//                    normalizedRegion,
//                    result.getMessage()
//            );
//
//            return;
//        }
//
//        drawUnmatchedCandidates(
//                normalizedRegion,
//                result
//        );
//
//        drawUnmatchedTargets(
//                normalizedRegion,
//                result
//        );
//
//        drawMatches(
//                normalizedRegion,
//                result
//        );
//
//        drawSummary(
//                normalizedRegion,
//                result
//        );
//    }
//
//    private void drawUnmatchedCandidates(
//            Mat image,
//            BubbleCandidateMatchingResult result
//    ) {
//        for (BubbleContourCandidate candidate
//                : result.getUnmatchedCandidates()) {
//
//            drawContour(
//                    image,
//                    candidate,
//                    UNMATCHED_CANDIDATE_COLOR,
//                    1
//            );
//        }
//    }
//
//    private void drawUnmatchedTargets(
//            Mat image,
//            BubbleCandidateMatchingResult result
//    ) {
//        for (ExpectedBubbleTarget target
//                : result.getUnmatchedTargets()) {
//
//            drawExpectedBounds(
//                    image,
//                    target,
//                    UNMATCHED_EXPECTED_COLOR,
//                    2
//            );
//
//            drawCross(
//                    image,
//                    new Point(
//                            target.getExpectedCenterX(),
//                            target.getExpectedCenterY()
//                    ),
//                    UNMATCHED_EXPECTED_COLOR,
//                    4,
//                    1
//            );
//        }
//    }
//
//    private void drawMatches(
//            Mat image,
//            BubbleCandidateMatchingResult result
//    ) {
//        for (BubbleCandidateMatch match
//                : result.getMatches()) {
//
//            ExpectedBubbleTarget target =
//                    match.getTarget();
//
//            BubbleContourCandidate candidate =
//                    match.getCandidate();
//
//            Point expectedCenter =
//                    new Point(
//                            target.getExpectedCenterX(),
//                            target.getExpectedCenterY()
//                    );
//
//            Point observedCenter =
//                    new Point(
//                            candidate.getCenterX(),
//                            candidate.getCenterY()
//                    );
//
//            drawExpectedBounds(
//                    image,
//                    target,
//                    MATCHED_EXPECTED_COLOR,
//                    1
//            );
//
//            drawCross(
//                    image,
//                    expectedCenter,
//                    MATCHED_EXPECTED_COLOR,
//                    3,
//                    1
//            );
//
//            Imgproc.line(
//                    image,
//                    expectedCenter,
//                    observedCenter,
//                    OFFSET_LINE_COLOR,
//                    1
//            );
//
//            drawContour(
//                    image,
//                    candidate,
//                    MATCHED_CONTOUR_COLOR,
//                    2
//            );
//
//            Imgproc.circle(
//                    image,
//                    observedCenter,
//                    3,
//                    OBSERVED_CENTER_COLOR,
//                    -1
//            );
//        }
//    }
//
//    private void drawExpectedBounds(
//            Mat image,
//            ExpectedBubbleTarget target,
//            Scalar color,
//            int thickness
//    ) {
//        PixelRectangle bounds =
//                target.getExpectedBounds();
//
//        Imgproc.rectangle(
//                image,
//                new Point(
//                        bounds.getLeft(),
//                        bounds.getTop()
//                ),
//                new Point(
//                        bounds.getRightInclusive(),
//                        bounds.getBottomInclusive()
//                ),
//                color,
//                thickness
//        );
//    }
//
//    private void drawContour(
//            Mat image,
//            BubbleContourCandidate candidate,
//            Scalar color,
//            int thickness
//    ) {
//        Point[] points =
//                candidate.copyContourPoints();
//
//        if (points.length < 2) {
//            return;
//        }
//
//        MatOfPoint contour =
//                new MatOfPoint(points);
//
//        try {
//            Imgproc.drawContours(
//                    image,
//                    Collections.singletonList(
//                            contour
//                    ),
//                    -1,
//                    color,
//                    thickness
//            );
//        } finally {
//            contour.release();
//        }
//    }
//
//    private void drawCross(
//            Mat image,
//            Point center,
//            Scalar color,
//            int radius,
//            int thickness
//    ) {
//        Imgproc.line(
//                image,
//                new Point(
//                        center.x - radius,
//                        center.y
//                ),
//                new Point(
//                        center.x + radius,
//                        center.y
//                ),
//                color,
//                thickness
//        );
//
//        Imgproc.line(
//                image,
//                new Point(
//                        center.x,
//                        center.y - radius
//                ),
//                new Point(
//                        center.x,
//                        center.y + radius
//                ),
//                color,
//                thickness
//        );
//    }
//
//    private void drawSummary(
//            Mat image,
//            BubbleCandidateMatchingResult result
//    ) {
//        String text =
//                String.format(
//                        Locale.US,
//                        "alvos=%d | associados=%d"
//                                + " | sem candidato=%d"
//                                + " | extras=%d"
//                                + " | cobertura=%.1f%%",
//                        result.getTargetCount(),
//                        result.getMatchCount(),
//                        result.getUnmatchedTargets().size(),
//                        result.getUnmatchedCandidates().size(),
//                        result.getDirectMatchRatio()
//                                * 100.0
//                );
//
//        Imgproc.putText(
//                image,
//                text,
//                new Point(
//                        12,
//                        Math.max(
//                                24,
//                                image.rows() - 16
//                        )
//                ),
//                Imgproc.FONT_HERSHEY_SIMPLEX,
//                0.50,
//                SUMMARY_COLOR,
//                2
//        );
//    }
//
//    private void drawFailure(
//            Mat image,
//            String message
//    ) {
//        String safeMessage =
//                message == null
//                        || message.trim().isEmpty()
//                        ? "associacao indisponivel"
//                        : message.trim();
//
//        Imgproc.putText(
//                image,
//                safeMessage,
//                new Point(
//                        12,
//                        Math.max(
//                                24,
//                                image.rows() - 16
//                        )
//                ),
//                Imgproc.FONT_HERSHEY_SIMPLEX,
//                0.50,
//                UNMATCHED_EXPECTED_COLOR,
//                2
//        );
//    }
//}
