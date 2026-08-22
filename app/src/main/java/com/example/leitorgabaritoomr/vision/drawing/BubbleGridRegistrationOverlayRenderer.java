package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.registration.BubbleBlockRegistration;
import com.example.leitorgabaritoomr.vision.registration.BubbleBlockTransform;
import com.example.leitorgabaritoomr.vision.registration.BubbleBlockTranslationSeed;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourCandidate;
import com.example.leitorgabaritoomr.vision.registration.BubbleContourExtractionResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleGridRegistrationResult;
import com.example.leitorgabaritoomr.vision.registration.BubbleGridSupport;
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
 * Renderiza exatamente o registro geometrico calculado para os
 * blocos de respostas.
 *
 * Cinza    = contorno extraido que nao participou da translacao.
 * Laranja  = apoio da translacao descartado pelo refinamento.
 * Amarelo  = centro originalmente previsto pelo layout.
 * Azul     = regiao prevista pelo registro aceito.
 * Vermelho = regiao prevista por um bloco rejeitado.
 * Magenta  = contorno realmente usado pelo registro final.
 * Ciano    = centro observado do apoio final.
 * Verde    = residuo entre centro previsto e centro observado.
 *
 * O renderizador nao estima, corrige ou desloca coordenadas. Todos
 * os pontos e dimensoes desenhados vem dos objetos armazenados no
 * BubbleGridRegistrationResult.
 */
public final class BubbleGridRegistrationOverlayRenderer {

    private static final Scalar RAW_CANDIDATE_COLOR =
            new Scalar(96.0, 96.0, 96.0, 255.0);

    private static final Scalar DISCARDED_SUPPORT_COLOR =
            new Scalar(255.0, 165.0, 0.0, 255.0);

    private static final Scalar ORIGINAL_CENTER_COLOR =
            new Scalar(255.0, 255.0, 0.0, 255.0);

    private static final Scalar ACCEPTED_TRANSFORM_COLOR =
            new Scalar(0.0, 160.0, 255.0, 255.0);

    private static final Scalar REJECTED_TRANSFORM_COLOR =
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
            BubbleTranslationEstimationResult translationResult,
            BubbleGridRegistrationResult registrationResult
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()) {
            return;
        }

        String validationError = validateInput(
                targets,
                extractionResult,
                translationResult,
                registrationResult
        );

        if (validationError != null) {
            drawFailure(
                    normalizedRegion,
                    validationError
            );

            return;
        }

        Map<Integer, BubbleTranslationSupport>
                translationSupportByCandidateId =
                createTranslationSupportByCandidateId(
                        translationResult
                );

        Set<Integer> registeredCandidateIds =
                createRegisteredCandidateIds(
                        registrationResult
                );

        drawCandidates(
                normalizedRegion,
                extractionResult,
                translationSupportByCandidateId.keySet(),
                registeredCandidateIds
        );

        drawTransformedTargets(
                normalizedRegion,
                targets,
                registrationResult
        );

        drawFinalSupports(
                normalizedRegion,
                registrationResult
        );

        drawBlockLabels(
                normalizedRegion,
                targets,
                registrationResult
        );

        drawSummary(
                normalizedRegion,
                registrationResult
        );
    }

    private String validateInput(
            List<ExpectedBubbleTarget> targets,
            BubbleContourExtractionResult extractionResult,
            BubbleTranslationEstimationResult translationResult,
            BubbleGridRegistrationResult registrationResult
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

        if (translationResult == null) {
            return "translacao inicial indisponivel";
        }

        if (!translationResult.isSuccess()) {
            return "translacao falhou: "
                    + translationResult.getMessage();
        }

        if (registrationResult == null) {
            return "registro geometrico indisponivel";
        }

        if (!registrationResult.isSuccess()) {
            return "registro falhou: "
                    + registrationResult.getMessage();
        }

        if (registrationResult.getTargetCount()
                != targets.size()) {

            return "quantidade de alvos inconsistente";
        }

        return null;
    }

    private Map<Integer, BubbleTranslationSupport>
    createTranslationSupportByCandidateId(
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

    private Set<Integer> createRegisteredCandidateIds(
            BubbleGridRegistrationResult result
    ) {
        Set<Integer> ids = new HashSet<>();

        for (BubbleBlockRegistration registration
                : result.getBlockRegistrations()) {

            for (BubbleGridSupport support
                    : registration.getSupports()) {

                ids.add(
                        support.getCandidate()
                                .getCandidateId()
                );
            }
        }

        return ids;
    }

    private void drawCandidates(
            Mat image,
            BubbleContourExtractionResult extractionResult,
            Set<Integer> translationCandidateIds,
            Set<Integer> registeredCandidateIds
    ) {
        for (BubbleContourCandidate candidate
                : extractionResult.getCandidates()) {

            int candidateId = candidate.getCandidateId();

            if (registeredCandidateIds.contains(candidateId)) {
                continue;
            }

            if (translationCandidateIds.contains(candidateId)) {
                drawContour(
                        image,
                        candidate,
                        DISCARDED_SUPPORT_COLOR,
                        2
                );

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

    private void drawTransformedTargets(
            Mat image,
            List<ExpectedBubbleTarget> targets,
            BubbleGridRegistrationResult result
    ) {
        for (ExpectedBubbleTarget target : targets) {
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

            BubbleBlockRegistration registration =
                    result.findByBlockIndex(
                            target.getBlockIndex()
                    );

            if (registration == null) {
                drawExpectedBounds(
                        image,
                        target.getExpectedBounds(),
                        REJECTED_TRANSFORM_COLOR,
                        2
                );

                continue;
            }

            Scalar color = registration.isAccepted()
                    ? ACCEPTED_TRANSFORM_COLOR
                    : REJECTED_TRANSFORM_COLOR;

            BubbleBlockTransform transform =
                    registration.getTransform();

            Point transformedCenter = new Point(
                    transform.predictCenterX(target),
                    transform.predictCenterY(target)
            );

            Imgproc.line(
                    image,
                    originalCenter,
                    transformedCenter,
                    color,
                    1
            );

            drawTransformedBounds(
                    image,
                    target,
                    transform,
                    color,
                    registration.isAccepted() ? 1 : 2
            );
        }
    }

    private void drawFinalSupports(
            Mat image,
            BubbleGridRegistrationResult result
    ) {
        for (BubbleBlockRegistration registration
                : result.getBlockRegistrations()) {

            for (BubbleGridSupport support
                    : registration.getSupports()) {

                BubbleContourCandidate candidate =
                        support.getCandidate();

                Point predictedCenter = new Point(
                        support.getPredictedCenterX(),
                        support.getPredictedCenterY()
                );

                Point observedCenter = new Point(
                        support.getObservedCenterX(),
                        support.getObservedCenterY()
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
            BubbleGridRegistrationResult result
    ) {
        Map<Integer, Point> anchorByBlock =
                createBlockAnchors(
                        targets,
                        result
                );

        for (BubbleBlockRegistration registration
                : result.getBlockRegistrations()) {

            Point anchor = anchorByBlock.get(
                    registration.getBlockIndex()
            );

            if (anchor == null) {
                continue;
            }

            BubbleBlockTransform transform =
                    registration.getTransform();

            String firstLine = String.format(
                    Locale.US,
                    "B%d sx=%.3f sy=%.3f",
                    registration.getBlockIndex() + 1,
                    transform.getScaleX(),
                    transform.getScaleY()
            );

            String secondLine = String.format(
                    Locale.US,
                    "apoio=%d/%d r=%.2f c=%.2f",
                    registration.getSupportCount(),
                    registration.getTargetCount(),
                    registration.getMedianResidual(),
                    registration.getConfidence()
            );

            Scalar color = registration.isAccepted()
                    ? ACCEPTED_TRANSFORM_COLOR
                    : REJECTED_TRANSFORM_COLOR;

            Imgproc.putText(
                    image,
                    firstLine,
                    anchor,
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.33,
                    color,
                    1
            );

            Imgproc.putText(
                    image,
                    secondLine,
                    new Point(
                            anchor.x,
                            anchor.y + 11.0
                    ),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.30,
                    color,
                    1
            );
        }
    }

    private Map<Integer, Point> createBlockAnchors(
            List<ExpectedBubbleTarget> targets,
            BubbleGridRegistrationResult result
    ) {
        Map<Integer, Point> anchors = new HashMap<>();

        for (ExpectedBubbleTarget target : targets) {
            BubbleBlockRegistration registration =
                    result.findByBlockIndex(
                            target.getBlockIndex()
                    );

            double candidateX;
            double candidateY;

            if (registration == null) {
                PixelRectangle bounds =
                        target.getExpectedBounds();

                candidateX = bounds.getLeft();
                candidateY = Math.max(
                        12.0,
                        bounds.getTop() - 19.0
                );
            } else {
                BubbleBlockTransform transform =
                        registration.getTransform();

                PixelRectangle bounds =
                        target.getExpectedBounds();

                candidateX = transform.transformX(
                        bounds.getLeft(),
                        bounds.getTop()
                );

                candidateY = Math.max(
                        12.0,
                        transform.transformY(
                                bounds.getLeft(),
                                bounds.getTop()
                        ) - 19.0
                );
            }

            Point current = anchors.get(
                    target.getBlockIndex()
            );

            if (current == null) {
                anchors.put(
                        target.getBlockIndex(),
                        new Point(
                                candidateX,
                                candidateY
                        )
                );

                continue;
            }

            current.x = Math.min(
                    current.x,
                    candidateX
            );

            current.y = Math.min(
                    current.y,
                    candidateY
            );
        }

        return anchors;
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

    /**
     * Desenha os quatro lados realmente transformados. Isso e
     * equivalente a aplicar a matriz do registro aos quatro cantos
     * da regiao usada posteriormente pelo calculo.
     */
    private void drawTransformedBounds(
            Mat image,
            ExpectedBubbleTarget target,
            BubbleBlockTransform transform,
            Scalar color,
            int thickness
    ) {
        PixelRectangle bounds =
                target.getExpectedBounds();

        Point topLeft = transformPoint(
                transform,
                bounds.getLeft(),
                bounds.getTop()
        );

        Point topRight = transformPoint(
                transform,
                bounds.getRightInclusive(),
                bounds.getTop()
        );

        Point bottomRight = transformPoint(
                transform,
                bounds.getRightInclusive(),
                bounds.getBottomInclusive()
        );

        Point bottomLeft = transformPoint(
                transform,
                bounds.getLeft(),
                bounds.getBottomInclusive()
        );

        Imgproc.line(
                image,
                topLeft,
                topRight,
                color,
                thickness
        );

        Imgproc.line(
                image,
                topRight,
                bottomRight,
                color,
                thickness
        );

        Imgproc.line(
                image,
                bottomRight,
                bottomLeft,
                color,
                thickness
        );

        Imgproc.line(
                image,
                bottomLeft,
                topLeft,
                color,
                thickness
        );
    }

    private Point transformPoint(
            BubbleBlockTransform transform,
            double x,
            double y
    ) {
        return new Point(
                transform.transformX(x, y),
                transform.transformY(x, y)
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
                new Point(
                        center.x - radius,
                        center.y
                ),
                new Point(
                        center.x + radius,
                        center.y
                ),
                color,
                thickness
        );

        Imgproc.line(
                image,
                new Point(
                        center.x,
                        center.y - radius
                ),
                new Point(
                        center.x,
                        center.y + radius
                ),
                color,
                thickness
        );
    }

    private void drawSummary(
            Mat image,
            BubbleGridRegistrationResult result
    ) {
        String text = String.format(
                Locale.US,
                "registro | blocos=%d/%d | apoios=%d/%d"
                        + " | descartados=%d | cobertura=%.1f%%"
                        + " | res=%.2fpx | conf=%.3f",
                result.getAcceptedBlockCount(),
                result.getBlockCount(),
                result.getRegisteredSupportCount(),
                result.getTargetCount(),
                result.getDiscardedSupportCount(),
                result.getRegisteredTargetRatio() * 100.0,
                result.getMedianResidual(),
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
                0.43,
                result.areAllBlocksAccepted()
                        ? SUMMARY_COLOR
                        : FAILURE_COLOR,
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
