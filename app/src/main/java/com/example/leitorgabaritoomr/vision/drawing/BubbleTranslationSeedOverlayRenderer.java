package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.registration.BubbleBlockTranslationSeed;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourCandidate;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractionResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleTranslationEstimationResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleTranslationSupport;
import com.example.leitorgabaritoomr.vision.registration.ExpectedBubbleTarget;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exibe exatamente a semente de translacao calculada para cada
 * bloco e os apoios utilizados pelo estimador.
 *
 * Cinza    = contorno extraido, ainda nao utilizado como apoio.
 * Amarelo  = centro originalmente previsto pelo layout.
 * Azul     = regiao prevista depois da translacao aceita.
 * Vermelho = regiao de um bloco cuja translacao foi rejeitada.
 * Magenta  = contorno realmente usado como apoio.
 * Ciano    = centro observado do apoio.
 * Verde    = residuo previsto -> observado.
 */
public final class BubbleTranslationSeedOverlayRenderer {

    private static final Scalar RAW_CANDIDATE_COLOR =
            new Scalar(96.0, 96.0, 96.0, 255.0);

    private static final Scalar ORIGINAL_CENTER_COLOR =
            new Scalar(255.0, 255.0, 0.0, 255.0);

    private static final Scalar ACCEPTED_PREDICTION_COLOR =
            new Scalar(0.0, 128.0, 255.0, 255.0);

    private static final Scalar REJECTED_PREDICTION_COLOR =
            new Scalar(255.0, 0.0, 0.0, 255.0);

    private static final Scalar SUPPORT_CONTOUR_COLOR =
            new Scalar(255.0, 0.0, 255.0, 255.0);

    private static final Scalar OBSERVED_CENTER_COLOR =
            new Scalar(0.0, 255.0, 255.0, 255.0);

    private static final Scalar RESIDUAL_COLOR =
            new Scalar(0.0, 255.0, 0.0, 255.0);

    private static final Scalar SUMMARY_COLOR =
            new Scalar(0.0, 255.0, 0.0, 255.0);

    private static final Scalar FAILURE_COLOR =
            new Scalar(255.0, 0.0, 0.0, 255.0);

    public void draw(
            Mat normalizedRegion,
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult,
            BubbleTranslationEstimationResult estimationResult
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()) {
            return;
        }

        String validationError = validateInput(
                targets,
                extractionResult,
                estimationResult
        );

        if (validationError != null) {
            drawFailure(normalizedRegion, validationError);
            return;
        }

        Map<Integer, BubbleTranslationSupport>
                supportByCandidateId =
                createSupportByCandidateId(estimationResult);

        drawRawCandidates(
                normalizedRegion,
                extractionResult,
                supportByCandidateId.keySet()
        );

        drawPredictedTargets(
                normalizedRegion,
                targets,
                estimationResult
        );

        drawSupports(
                normalizedRegion,
                estimationResult
        );

        drawBlockLabels(
                normalizedRegion,
                targets,
                estimationResult
        );

        drawSummary(
                normalizedRegion,
                estimationResult
        );
    }

    private String validateInput(
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult,
            BubbleTranslationEstimationResult estimationResult
    ) {
        if (targets == null || targets.isEmpty()) {
            return "alvos esperados indisponiveis";
        }

        if (extractionResult == null) {
            return "extracao de candidatos indisponivel";
        }

        if (!extractionResult.isSuccess()) {
            return "extracao falhou: "
                    + extractionResult.getMessage();
        }

        if (estimationResult == null) {
            return "estimativa de translacao indisponivel";
        }

        if (!estimationResult.isSuccess()) {
            return "estimativa falhou: "
                    + estimationResult.getMessage();
        }

        return null;
    }

    private Map<Integer, BubbleTranslationSupport>
    createSupportByCandidateId(
            BubbleTranslationEstimationResult result
    ) {
        Map<Integer, BubbleTranslationSupport> supports =
                new HashMap<>();

        for (BubbleBlockTranslationSeed seed
                : result.getBlockSeeds()) {

            for (BubbleTranslationSupport support
                    : seed.getSupports()) {

                supports.put(
                        support.getCandidate()
                                .getCandidateId(),
                        support
                );
            }
        }

        return supports;
    }

    private void drawRawCandidates(
            Mat image,
            BubbleContourExtractionResult extractionResult,
            Set<Integer> supportCandidateIds
    ) {
        for (BubbleContourCandidate candidate
                : extractionResult.getCandidates()) {

            if (supportCandidateIds.contains(
                    candidate.getCandidateId()
            )) {
                continue;
            }

            drawContour(
                    image,
                    candidate,
                    RAW_CANDIDATE_COLOR,
                    1
            );
        }
    }

    private void drawPredictedTargets(
            Mat image,
            List<ExpectedBubbleTarget> targets,
            BubbleTranslationEstimationResult result
    ) {
        for (ExpectedBubbleTarget target : targets) {
            BubbleBlockTranslationSeed seed =
                    result.findByBlockIndex(
                            target.getBlockIndex()
                    );

            Point originalCenter = new Point(
                    target.getExpectedCenterX(),
                    target.getExpectedCenterY()
            );

            drawCross(
                    image,
                    originalCenter,
                    ORIGINAL_CENTER_COLOR,
                    2,
                    1
            );

            if (seed == null) {
                drawExpectedBounds(
                        image,
                        target.getExpectedBounds(),
                        REJECTED_PREDICTION_COLOR,
                        2
                );

                continue;
            }

            Point predictedCenter = new Point(
                    seed.predictCenterX(target),
                    seed.predictCenterY(target)
            );

            Scalar predictionColor = seed.isAccepted()
                    ? ACCEPTED_PREDICTION_COLOR
                    : REJECTED_PREDICTION_COLOR;

            Imgproc.line(
                    image,
                    originalCenter,
                    predictedCenter,
                    predictionColor,
                    1
            );

            drawTranslatedBounds(
                    image,
                    target,
                    predictedCenter,
                    predictionColor,
                    seed.isAccepted() ? 1 : 2
            );
        }
    }

    private void drawSupports(
            Mat image,
            BubbleTranslationEstimationResult result
    ) {
        for (BubbleBlockTranslationSeed seed
                : result.getBlockSeeds()) {

            for (BubbleTranslationSupport support
                    : seed.getSupports()) {

                BubbleContourCandidate candidate =
                        support.getCandidate();

                Point predictedCenter = new Point(
                        support.getPredictedCenterX(),
                        support.getPredictedCenterY()
                );

                Point observedCenter = new Point(
                        candidate.getCenterX(),
                        candidate.getCenterY()
                );

                Imgproc.line(
                        image,
                        predictedCenter,
                        observedCenter,
                        RESIDUAL_COLOR,
                        1
                );

                drawContour(
                        image,
                        candidate,
                        SUPPORT_CONTOUR_COLOR,
                        2
                );

                Imgproc.circle(
                        image,
                        observedCenter,
                        3,
                        OBSERVED_CENTER_COLOR,
                        -1
                );
            }
        }
    }

    private void drawBlockLabels(
            Mat image,
            List<ExpectedBubbleTarget> targets,
            BubbleTranslationEstimationResult result
    ) {
        Map<Integer, Point> anchorByBlock =
                createBlockAnchors(targets);

        for (BubbleBlockTranslationSeed seed
                : result.getBlockSeeds()) {

            Point anchor = anchorByBlock.get(
                    seed.getBlockIndex()
            );

            if (anchor == null) {
                continue;
            }

            String text = String.format(
                    Locale.US,
                    "%s dx=%.1f dy=%.1f apoio=%d/%d conf=%.2f",
                    seed.getBlockId(),
                    seed.getOffsetX(),
                    seed.getOffsetY(),
                    seed.getSupportCount(),
                    seed.getTargetCount(),
                    seed.getConfidence()
            );

            Imgproc.putText(
                    image,
                    text,
                    anchor,
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.34,
                    seed.isAccepted()
                            ? ACCEPTED_PREDICTION_COLOR
                            : REJECTED_PREDICTION_COLOR,
                    1
            );
        }
    }

    private Map<Integer, Point> createBlockAnchors(
            List<ExpectedBubbleTarget> targets
    ) {
        Map<Integer, Point> result = new HashMap<>();

        for (ExpectedBubbleTarget target : targets) {
            PixelRectangle bounds =
                    target.getExpectedBounds();

            Point current = result.get(
                    target.getBlockIndex()
            );

            double candidateX = bounds.getLeft();
            double candidateY = Math.max(
                    14.0,
                    bounds.getTop() - 5.0
            );

            if (current == null) {
                result.put(
                        target.getBlockIndex(),
                        new Point(candidateX, candidateY)
                );

                continue;
            }

            current.x = Math.min(current.x, candidateX);
            current.y = Math.min(current.y, candidateY);
        }

        return result;
    }

    private void drawExpectedBounds(
            Mat image,
            PixelRectangle bounds,
            Scalar color,
            int thickness
    ) {
        Imgproc.rectangle(
                image,
                new Point(
                        bounds.getLeft(),
                        bounds.getTop()
                ),
                new Point(
                        bounds.getRightInclusive(),
                        bounds.getBottomInclusive()
                ),
                color,
                thickness
        );
    }

    private void drawTranslatedBounds(
            Mat image,
            ExpectedBubbleTarget target,
            Point translatedCenter,
            Scalar color,
            int thickness
    ) {
        double halfWidth =
                (target.getExpectedWidth() - 1.0)
                        / 2.0;

        double halfHeight =
                (target.getExpectedHeight() - 1.0)
                        / 2.0;

        Imgproc.rectangle(
                image,
                new Point(
                        translatedCenter.x - halfWidth,
                        translatedCenter.y - halfHeight
                ),
                new Point(
                        translatedCenter.x + halfWidth,
                        translatedCenter.y + halfHeight
                ),
                color,
                thickness
        );
    }

    private void drawContour(
            Mat image,
            BubbleContourCandidate candidate,
            Scalar color,
            int thickness
    ) {
        Point[] points = candidate.copyContourPoints();

        if (points.length < 2) {
            return;
        }

        MatOfPoint contour = new MatOfPoint(points);

        try {
            Imgproc.drawContours(
                    image,
                    Collections.singletonList(contour),
                    -1,
                    color,
                    thickness
            );
        } finally {
            contour.release();
        }
    }

    private void drawCross(
            Mat image,
            Point center,
            Scalar color,
            int radius,
            int thickness
    ) {
        Imgproc.line(
                image,
                new Point(center.x - radius, center.y),
                new Point(center.x + radius, center.y),
                color,
                thickness
        );

        Imgproc.line(
                image,
                new Point(center.x, center.y - radius),
                new Point(center.x, center.y + radius),
                color,
                thickness
        );
    }

    private void drawSummary(
            Mat image,
            BubbleTranslationEstimationResult result
    ) {
        String text = String.format(
                Locale.US,
                "translacao | blocos=%d/%d | apoios=%d/%d"
                        + " | cobertura=%.1f%% | conf=%.3f",
                result.getAcceptedBlockCount(),
                result.getBlockCount(),
                result.getSupportedTargetCount(),
                result.getTargetCount(),
                result.getSupportedTargetRatio() * 100.0,
                result.getSheetConfidence()
        );

        Imgproc.putText(
                image,
                text,
                new Point(
                        12,
                        Math.max(24, image.rows() - 16)
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.48,
                SUMMARY_COLOR,
                2
        );
    }

    private void drawFailure(
            Mat image,
            String message
    ) {
        Imgproc.putText(
                image,
                message,
                new Point(
                        12,
                        Math.max(24, image.rows() - 16)
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.48,
                FAILURE_COLOR,
                2
        );
    }
}
