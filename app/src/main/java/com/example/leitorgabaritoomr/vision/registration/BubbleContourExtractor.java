package com.example.leitorgabaritoomr.vision.registration;

import com.example.leitorgabaritoomr.vision.geometry.PixelRectangle;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extrai contornos com dimensões compatíveis com as bolhas
 * descritas pelo layout.
 *
 * Ainda não associa candidatos a alternativas.
 */
public final class BubbleContourExtractor {

    private final BubbleGridRegistrarConfig config;

    public BubbleContourExtractor(
            BubbleGridRegistrarConfig config
    ) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuração é obrigatória."
            );
        }

        this.config = config;
    }

    public BubbleContourExtractionResult extract(
            Mat normalizedGray,
            OmrLayoutDefinition layout
    ) {
        String validationError =
                validateInput(
                        normalizedGray,
                        layout
                );

        if (validationError != null) {
            return BubbleContourExtractionResult
                    .failure(validationError);
        }

        ExpectedSizeStatistics expectedSizes =
                calculateExpectedSizes(
                        normalizedGray,
                        layout
                );

        if (expectedSizes == null) {
            return BubbleContourExtractionResult
                    .failure(
                            "Não foi possível calcular"
                                    + " o tamanho esperado"
                                    + " das bolhas."
                    );
        }

        int adaptiveBlockSize =
                calculateAdaptiveBlockSize(
                        normalizedGray,
                        expectedSizes
                );

        if (adaptiveBlockSize < 3) {
            return BubbleContourExtractionResult
                    .failure(
                            "A imagem ficou pequena demais"
                                    + " para o threshold adaptativo."
                    );
        }

        Mat blurred = new Mat();
        Mat binary = new Mat();
        Mat hierarchy = new Mat();

        List<MatOfPoint> contours =
                new ArrayList<>();

        try {
            applyBlur(
                    normalizedGray,
                    blurred
            );

            Imgproc.adaptiveThreshold(
                    blurred,
                    binary,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY_INV,
                    adaptiveBlockSize,
                    config
                            .getAdaptiveThresholdConstant()
            );

            Imgproc.findContours(
                    binary,
                    contours,
                    hierarchy,
                    Imgproc.RETR_LIST,
                    Imgproc.CHAIN_APPROX_SIMPLE
            );

            List<BubbleContourCandidate>
                    candidates =
                    createCandidates(
                            contours,
                            expectedSizes
                    );

            int totalContourCount =
                    contours.size();

            int rejectedContourCount =
                    totalContourCount
                            - candidates.size();

            return BubbleContourExtractionResult
                    .success(
                            candidates,
                            totalContourCount,
                            rejectedContourCount,
                            adaptiveBlockSize
                    );

        } catch (RuntimeException exception) {
            return BubbleContourExtractionResult
                    .failure(
                            "Erro ao extrair contornos: "
                                    + safeMessage(exception)
                    );

        } finally {
            for (MatOfPoint contour : contours) {
                if (contour != null) {
                    contour.release();
                }
            }

            hierarchy.release();
            binary.release();
            blurred.release();
        }
    }

    private String validateInput(
            Mat normalizedGray,
            OmrLayoutDefinition layout
    ) {
        if (normalizedGray == null
                || normalizedGray.empty()) {

            return "A imagem normalizada está vazia.";
        }

        if (normalizedGray.channels() != 1) {
            return "BubbleContourExtractor exige"
                    + " imagem em escala de cinza.";
        }

        if (normalizedGray.cols() < 3
                || normalizedGray.rows() < 3) {

            return "A imagem normalizada é pequena demais.";
        }

        if (layout == null) {
            return "O layout é obrigatório.";
        }

        if (layout.getOptionCount() <= 0) {
            return "O layout não possui alternativas.";
        }

        return null;
    }

    private void applyBlur(
            Mat source,
            Mat destination
    ) {
        int kernelSize =
                config.getBlurKernelSize();

        if (kernelSize <= 1) {
            source.copyTo(destination);

            return;
        }

        Imgproc.GaussianBlur(
                source,
                destination,
                new Size(
                        kernelSize,
                        kernelSize
                ),
                0.0
        );
    }

    private ExpectedSizeStatistics
    calculateExpectedSizes(
            Mat image,
            OmrLayoutDefinition layout
    ) {
        List<Double> widths =
                new ArrayList<>();

        List<Double> heights =
                new ArrayList<>();

        for (OmrBlockDefinition block
                : layout.getBlocks()) {

            for (OmrQuestionDefinition question
                    : block.getQuestions()) {

                for (OmrOptionDefinition option
                        : question.getOptions()) {

                    double width =
                            option
                                    .getNormalizedWidth()
                                    * image.cols();

                    double height =
                            option
                                    .getNormalizedHeight()
                                    * image.rows();

                    if (Double.isFinite(width)
                            && Double.isFinite(height)
                            && width > 0.0
                            && height > 0.0) {

                        widths.add(width);
                        heights.add(height);
                    }
                }
            }
        }

        if (widths.isEmpty()
                || heights.isEmpty()) {

            return null;
        }

        Collections.sort(widths);
        Collections.sort(heights);

        double minimumWidth =
                widths.get(0);

        double maximumWidth =
                widths.get(
                        widths.size() - 1
                );

        double medianWidth =
                median(widths);

        double minimumHeight =
                heights.get(0);

        double maximumHeight =
                heights.get(
                        heights.size() - 1
                );

        double medianHeight =
                median(heights);

        return new ExpectedSizeStatistics(
                minimumWidth,
                maximumWidth,
                medianWidth,
                minimumHeight,
                maximumHeight,
                medianHeight
        );
    }

    private int calculateAdaptiveBlockSize(
            Mat image,
            ExpectedSizeStatistics sizes
    ) {
        double referenceDimension =
                Math.max(
                        sizes.medianWidth,
                        sizes.medianHeight
                );

        int requested =
                (int) Math.round(
                        referenceDimension
                                * config
                                .getAdaptiveBlockSizeScale()
                );

        requested = makeOdd(requested);

        int minimum =
                config
                        .getMinimumAdaptiveBlockSize();

        int maximum =
                config
                        .getMaximumAdaptiveBlockSize();

        int imageMaximum =
                Math.min(
                        image.cols(),
                        image.rows()
                );

        if (imageMaximum % 2 == 0) {
            imageMaximum--;
        }

        maximum =
                Math.min(
                        maximum,
                        imageMaximum
                );

        if (maximum < 3) {
            return 0;
        }

        if (minimum > maximum) {
            minimum = maximum;
        }

        int result =
                clamp(
                        requested,
                        minimum,
                        maximum
                );

        return makeOdd(result);
    }

    private List<BubbleContourCandidate>
    createCandidates(
            List<MatOfPoint> contours,
            ExpectedSizeStatistics sizes
    ) {
        List<BubbleContourCandidate>
                candidates =
                new ArrayList<>();

        double minimumWidth =
                sizes.minimumWidth
                        * config
                        .getMinimumCandidateWidthScale();

        double maximumWidth =
                sizes.maximumWidth
                        * config
                        .getMaximumCandidateWidthScale();

        double minimumHeight =
                sizes.minimumHeight
                        * config
                        .getMinimumCandidateHeightScale();

        double maximumHeight =
                sizes.maximumHeight
                        * config
                        .getMaximumCandidateHeightScale();

        int nextCandidateId = 0;

        for (MatOfPoint contour : contours) {
            BubbleContourCandidate candidate =
                    createCandidate(
                            nextCandidateId,
                            contour,
                            minimumWidth,
                            maximumWidth,
                            minimumHeight,
                            maximumHeight
                    );

            if (candidate != null) {
                candidates.add(candidate);
                nextCandidateId++;
            }
        }

        return candidates;
    }

    private BubbleContourCandidate createCandidate(
            int candidateId,
            MatOfPoint contour,
            double minimumWidth,
            double maximumWidth,
            double minimumHeight,
            double maximumHeight
    ) {
        if (contour == null
                || contour.empty()) {

            return null;
        }

        Rect bounds =
                Imgproc.boundingRect(contour);

        if (bounds.width < minimumWidth
                || bounds.width > maximumWidth
                || bounds.height < minimumHeight
                || bounds.height > maximumHeight) {

            return null;
        }

        double aspectRatio =
                bounds.width
                        / (double) bounds.height;

        if (aspectRatio
                < config.getMinimumAspectRatio()
                || aspectRatio
                > config.getMaximumAspectRatio()) {

            return null;
        }

        double contourArea =
                Math.abs(
                        Imgproc.contourArea(contour)
                );

        if (!Double.isFinite(contourArea)
                || contourArea <= 0.0) {

            return null;
        }

        double boundsArea =
                bounds.width
                        * (double) bounds.height;

        if (boundsArea <= 0.0) {
            return null;
        }

        double rectangularity =
                clamp01(
                        contourArea / boundsArea
                );

        if (rectangularity
                < config.getMinimumRectangularity()) {

            return null;
        }

        MatOfPoint2f contour2f = null;
        MatOfPoint2f approximation = null;

        try {
            contour2f =
                    new MatOfPoint2f(
                            contour.toArray()
                    );

            double perimeter =
                    Imgproc.arcLength(
                            contour2f,
                            true
                    );

            if (!Double.isFinite(perimeter)
                    || perimeter <= 0.0) {

                return null;
            }

            approximation =
                    new MatOfPoint2f();

            Imgproc.approxPolyDP(
                    contour2f,
                    approximation,
                    perimeter * 0.03,
                    true
            );

            Point[] contourPoints =
                    approximation.toArray();

            if (contourPoints.length < 2) {
                return null;
            }

            Point center =
                    calculateContourCenter(
                            contour,
                            bounds
                    );

            return new BubbleContourCandidate(
                    candidateId,

                    new PixelRectangle(
                            bounds.x,
                            bounds.y,
                            bounds.width,
                            bounds.height
                    ),

                    center.x,
                    center.y,

                    contourArea,
                    perimeter,
                    rectangularity,
                    aspectRatio,
                    contourPoints
            );

        } finally {
            if (approximation != null) {
                approximation.release();
            }

            if (contour2f != null) {
                contour2f.release();
            }
        }
    }

    private Point calculateContourCenter(
            MatOfPoint contour,
            Rect bounds
    ) {
        Moments moments =
                Imgproc.moments(contour);

        if (moments != null
                && Math.abs(moments.get_m00())
                > 0.000001) {

            return new Point(
                    moments.get_m10()
                            / moments.get_m00(),

                    moments.get_m01()
                            / moments.get_m00()
            );
        }

        return new Point(
                bounds.x
                        + (
                        bounds.width - 1
                ) / 2.0,

                bounds.y
                        + (
                        bounds.height - 1
                ) / 2.0
        );
    }

    private double median(
            List<Double> sortedValues
    ) {
        int size =
                sortedValues.size();

        int middle =
                size / 2;

        if (size % 2 == 1) {
            return sortedValues.get(middle);
        }

        return (
                sortedValues.get(middle - 1)
                        + sortedValues.get(middle)
        ) / 2.0;
    }

    private int makeOdd(
            int value
    ) {
        if (value < 3) {
            return 3;
        }

        if (value % 2 == 0) {
            return value + 1;
        }

        return value;
    }

    private int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(value, maximum)
        );
    }

    private double clamp01(
            double value
    ) {
        return Math.max(
                0.0,
                Math.min(1.0, value)
        );
    }

    private String safeMessage(
            RuntimeException exception
    ) {
        String message =
                exception.getMessage();

        if (message == null
                || message.trim().isEmpty()) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    private static final class
    ExpectedSizeStatistics {

        private final double minimumWidth;
        private final double maximumWidth;
        private final double medianWidth;

        private final double minimumHeight;
        private final double maximumHeight;
        private final double medianHeight;

        private ExpectedSizeStatistics(
                double minimumWidth,
                double maximumWidth,
                double medianWidth,
                double minimumHeight,
                double maximumHeight,
                double medianHeight
        ) {
            this.minimumWidth = minimumWidth;
            this.maximumWidth = maximumWidth;
            this.medianWidth = medianWidth;

            this.minimumHeight = minimumHeight;
            this.maximumHeight = maximumHeight;
            this.medianHeight = medianHeight;
        }
    }
}