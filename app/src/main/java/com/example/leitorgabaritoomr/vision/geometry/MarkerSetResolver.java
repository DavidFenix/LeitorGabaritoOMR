package com.example.leitorgabaritoomr.vision.geometry;

import com.example.leitorgabaritoomr.vision.model.DetectedMarker;

import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MarkerSetResolver {

    private final MarkerSetResolverConfig config;

    public MarkerSetResolver() {

        this(
                MarkerSetResolverConfig
                        .developmentDefaults()
        );
    }

    public MarkerSetResolver(
            MarkerSetResolverConfig config
    ) {

        if (config == null) {

            throw new IllegalArgumentException(
                    "A configuração do resolvedor é obrigatória."
            );
        }

        this.config = config;
    }

    public MarkerSetResolutionResult resolve(
            List<DetectedMarker> candidates,
            int frameWidth,
            int frameHeight
    ) {

        if (candidates == null
                || candidates.size() < 4) {

            return MarkerSetResolutionResult.rejected(
                    "Menos de quatro candidatos.",
                    0,
                    -1,
                    -1
            );
        }

        if (frameWidth <= 0 || frameHeight <= 0) {

            return MarkerSetResolutionResult.rejected(
                    "Dimensões inválidas do frame.",
                    0,
                    -1,
                    -1
            );
        }

        List<DetectedMarker> topLeftCandidates =
                selectExtremeCandidates(
                        candidates,
                        CornerRole.TOP_LEFT
                );

        List<DetectedMarker> topRightCandidates =
                selectExtremeCandidates(
                        candidates,
                        CornerRole.TOP_RIGHT
                );

        List<DetectedMarker> bottomRightCandidates =
                selectExtremeCandidates(
                        candidates,
                        CornerRole.BOTTOM_RIGHT
                );

        List<DetectedMarker> bottomLeftCandidates =
                selectExtremeCandidates(
                        candidates,
                        CornerRole.BOTTOM_LEFT
                );

        double candidateCloudArea =
                calculateCandidateCloudArea(
                        candidates
                );

        CandidateSet best = null;
        CandidateSet secondBest = null;

        int evaluatedCombinations = 0;

        for (DetectedMarker topLeft
                : topLeftCandidates) {

            for (DetectedMarker topRight
                    : topRightCandidates) {

                for (DetectedMarker bottomRight
                        : bottomRightCandidates) {

                    for (DetectedMarker bottomLeft
                            : bottomLeftCandidates) {

                        if (!areDistinct(
                                topLeft,
                                topRight,
                                bottomRight,
                                bottomLeft
                        )) {

                            continue;
                        }

                        CandidateSet evaluated =
                                evaluateSet(
                                        topLeft,
                                        topRight,
                                        bottomRight,
                                        bottomLeft,
                                        candidates,
                                        frameWidth,
                                        frameHeight,
                                        candidateCloudArea
                                );

                        if (evaluated == null) {
                            continue;
                        }

                        evaluatedCombinations++;

                        if (best == null
                                || evaluated.score
                                > best.score) {

                            secondBest = best;
                            best = evaluated;

                        } else if (secondBest == null
                                || evaluated.score
                                > secondBest.score) {

                            secondBest = evaluated;
                        }
                    }
                }
            }
        }

        if (best == null) {

            return MarkerSetResolutionResult.rejected(
                    "Nenhum quadrilátero válido encontrado.",
                    evaluatedCombinations,
                    -1,
                    -1
            );
        }

        double secondBestScore =
                secondBest == null
                        ? -1
                        : secondBest.score;

        if (best.regionAreaRatio
                < config.getMinimumRegionAreaRatio()) {

            return MarkerSetResolutionResult.rejected(
                    "A região encontrada é pequena demais.",
                    evaluatedCombinations,
                    best.score,
                    secondBestScore
            );
        }

        if (best.sizeSimilarity
                < config.getMinimumSizeSimilarity()) {

            return MarkerSetResolutionResult.rejected(
                    "Os marcadores possuem tamanhos incompatíveis.",
                    evaluatedCombinations,
                    best.score,
                    secondBestScore
            );
        }

        if (best.score
                < config.getMinimumAcceptedScore()) {

            return MarkerSetResolutionResult.rejected(
                    "A confiança do conjunto é insuficiente.",
                    evaluatedCombinations,
                    best.score,
                    secondBestScore
            );
        }

        if (secondBest != null
                && best.score - secondBest.score
                < config.getMinimumScoreDifference()) {

            return MarkerSetResolutionResult.rejected(
                    "Resultado geométrico ambíguo.",
                    evaluatedCombinations,
                    best.score,
                    secondBestScore
            );
        }

        return MarkerSetResolutionResult.accepted(
                best.toResolvedMarkerSet(),
                evaluatedCombinations,
                best.score,
                secondBestScore
        );
    }

    private List<DetectedMarker> selectExtremeCandidates(
            List<DetectedMarker> candidates,
            CornerRole role
    ) {

        List<DetectedMarker> sorted =
                new ArrayList<>(candidates);

        Comparator<DetectedMarker> comparator =
                Comparator.comparingDouble(
                        marker ->
                                calculatePositionMetric(
                                        marker,
                                        role
                                )
                );

        sorted.sort(comparator);

        int limit =
                Math.min(
                        config.getCandidatesPerCorner(),
                        sorted.size()
                );

        return new ArrayList<>(
                sorted.subList(0, limit)
        );
    }

    private double calculatePositionMetric(
            DetectedMarker marker,
            CornerRole role
    ) {

        Point center =
                marker.getCenter();

        switch (role) {

            case TOP_LEFT:

                return center.x + center.y;

            case TOP_RIGHT:

                /*
                 * Queremos o maior x - y.
                 * O sinal negativo permite ordenar
                 * crescentemente.
                 */
                return -(center.x - center.y);

            case BOTTOM_RIGHT:

                return -(center.x + center.y);

            case BOTTOM_LEFT:

                return center.x - center.y;

            default:

                throw new IllegalStateException(
                        "Papel geométrico desconhecido: "
                                + role
                );
        }
    }

    private CandidateSet evaluateSet(
            DetectedMarker topLeft,
            DetectedMarker topRight,
            DetectedMarker bottomRight,
            DetectedMarker bottomLeft,
            List<DetectedMarker> allCandidates,
            int frameWidth,
            int frameHeight,
            double candidateCloudArea
    ) {

        Point tl = topLeft.getCenter();
        Point tr = topRight.getCenter();
        Point br = bottomRight.getCenter();
        Point bl = bottomLeft.getCenter();

        Point[] polygon =
                new Point[]{
                        tl,
                        tr,
                        br,
                        bl
                };

        if (!isConvex(polygon)) {
            return null;
        }

        double regionArea =
                polygonArea(polygon);

        if (regionArea <= 0) {
            return null;
        }

        double frameArea =
                frameWidth
                        * (double) frameHeight;

        double regionAreaRatio =
                regionArea / frameArea;

        double sizeSimilarity =
                calculateMarkerSizeSimilarity(
                        topLeft,
                        topRight,
                        bottomRight,
                        bottomLeft
                );

        double sideCoherence =
                calculateSideCoherence(
                        tl,
                        tr,
                        br,
                        bl
                );

        double containmentRatio =
                calculateContainmentRatio(
                        polygon,
                        allCandidates
                );

        double averageMarkerConfidence =
                (
                        topLeft.getConfidence()
                                + topRight.getConfidence()
                                + bottomRight.getConfidence()
                                + bottomLeft.getConfidence()
                ) / 4.0;

        double cloudCoverage =
                candidateCloudArea <= 0
                        ? 0
                        : clamp01(
                        regionArea
                                / candidateCloudArea
                );

        /*
         * Pontuação total:
         *
         * 30% cobertura da área dos candidatos
         * 25% quantidade de candidatos dentro da região
         * 20% semelhança entre os marcadores
         * 15% coerência dos lados opostos
         * 10% confiança individual
         */
        double score =
                cloudCoverage * 0.30
                        + containmentRatio * 0.25
                        + sizeSimilarity * 0.20
                        + sideCoherence * 0.15
                        + averageMarkerConfidence * 0.10;

        return new CandidateSet(
                topLeft,
                topRight,
                bottomRight,
                bottomLeft,
                clamp01(score),
                regionAreaRatio,
                sizeSimilarity,
                containmentRatio
        );
    }

    private boolean areDistinct(
            DetectedMarker... markers
    ) {

        Set<DetectedMarker> unique =
                new HashSet<>();

        for (DetectedMarker marker : markers) {

            if (!unique.add(marker)) {
                return false;
            }
        }

        return true;
    }

    private boolean isConvex(
            Point[] polygon
    ) {

        if (polygon == null
                || polygon.length != 4) {

            return false;
        }

        double previousCross = 0;

        for (int index = 0;
             index < polygon.length;
             index++) {

            Point a =
                    polygon[index];

            Point b =
                    polygon[
                            (index + 1)
                                    % polygon.length
                            ];

            Point c =
                    polygon[
                            (index + 2)
                                    % polygon.length
                            ];

            double cross =
                    crossProduct(a, b, c);

            if (Math.abs(cross) < 0.0001) {
                return false;
            }

            if (previousCross != 0
                    && Math.signum(cross)
                    != Math.signum(previousCross)) {

                return false;
            }

            previousCross = cross;
        }

        return true;
    }

    private double crossProduct(
            Point a,
            Point b,
            Point c
    ) {

        return (b.x - a.x)
                * (c.y - b.y)
                - (b.y - a.y)
                * (c.x - b.x);
    }

    private double polygonArea(
            Point[] polygon
    ) {

        double sum = 0;

        for (int index = 0;
             index < polygon.length;
             index++) {

            Point current =
                    polygon[index];

            Point next =
                    polygon[
                            (index + 1)
                                    % polygon.length
                            ];

            sum +=
                    current.x * next.y
                            - next.x * current.y;
        }

        return Math.abs(sum) / 2.0;
    }

    private double calculateMarkerSizeSimilarity(
            DetectedMarker... markers
    ) {

        double minimumArea =
                Double.MAX_VALUE;

        double maximumArea = 0;

        for (DetectedMarker marker : markers) {

            double area =
                    polygonArea(
                            marker.getCorners()
                    );

            minimumArea =
                    Math.min(
                            minimumArea,
                            area
                    );

            maximumArea =
                    Math.max(
                            maximumArea,
                            area
                    );
        }

        if (maximumArea <= 0
                || minimumArea == Double.MAX_VALUE) {

            return 0;
        }

        return clamp01(
                minimumArea / maximumArea
        );
    }

    private double calculateSideCoherence(
            Point tl,
            Point tr,
            Point br,
            Point bl
    ) {

        double top =
                distance(tl, tr);

        double bottom =
                distance(bl, br);

        double left =
                distance(tl, bl);

        double right =
                distance(tr, br);

        double horizontalBalance =
                ratioBetween(
                        top,
                        bottom
                );

        double verticalBalance =
                ratioBetween(
                        left,
                        right
                );

        return clamp01(
                (
                        horizontalBalance
                                + verticalBalance
                ) / 2.0
        );
    }

    private double calculateContainmentRatio(
            Point[] polygon,
            List<DetectedMarker> candidates
    ) {

        if (candidates.isEmpty()) {
            return 0;
        }

        int contained = 0;

        for (DetectedMarker candidate
                : candidates) {

            if (isPointInsideConvexPolygon(
                    candidate.getCenter(),
                    polygon
            )) {

                contained++;
            }
        }

        return contained
                / (double) candidates.size();
    }

    private boolean isPointInsideConvexPolygon(
            Point point,
            Point[] polygon
    ) {

        double previousCross = 0;

        for (int index = 0;
             index < polygon.length;
             index++) {

            Point a =
                    polygon[index];

            Point b =
                    polygon[
                            (index + 1)
                                    % polygon.length
                            ];

            double cross =
                    (b.x - a.x)
                            * (point.y - a.y)
                            - (b.y - a.y)
                            * (point.x - a.x);

            if (Math.abs(cross) < 0.0001) {
                continue;
            }

            if (previousCross != 0
                    && Math.signum(cross)
                    != Math.signum(previousCross)) {

                return false;
            }

            previousCross = cross;
        }

        return true;
    }

    private double calculateCandidateCloudArea(
            List<DetectedMarker> candidates
    ) {

        double minimumX =
                Double.MAX_VALUE;

        double minimumY =
                Double.MAX_VALUE;

        double maximumX =
                -Double.MAX_VALUE;

        double maximumY =
                -Double.MAX_VALUE;

        for (DetectedMarker candidate
                : candidates) {

            Point center =
                    candidate.getCenter();

            minimumX =
                    Math.min(
                            minimumX,
                            center.x
                    );

            minimumY =
                    Math.min(
                            minimumY,
                            center.y
                    );

            maximumX =
                    Math.max(
                            maximumX,
                            center.x
                    );

            maximumY =
                    Math.max(
                            maximumY,
                            center.y
                    );
        }

        return Math.max(
                0,
                maximumX - minimumX
        ) * Math.max(
                0,
                maximumY - minimumY
        );
    }

    private double distance(
            Point first,
            Point second
    ) {

        return Math.hypot(
                second.x - first.x,
                second.y - first.y
        );
    }

    private double ratioBetween(
            double first,
            double second
    ) {

        double maximum =
                Math.max(first, second);

        double minimum =
                Math.min(first, second);

        if (maximum <= 0) {
            return 0;
        }

        return clamp01(
                minimum / maximum
        );
    }

    private double clamp01(
            double value
    ) {

        return Math.max(
                0,
                Math.min(1, value)
        );
    }

    private static final class CandidateSet {

        private final DetectedMarker topLeft;
        private final DetectedMarker topRight;
        private final DetectedMarker bottomRight;
        private final DetectedMarker bottomLeft;

        private final double score;
        private final double regionAreaRatio;
        private final double sizeSimilarity;
        private final double containmentRatio;

        private CandidateSet(
                DetectedMarker topLeft,
                DetectedMarker topRight,
                DetectedMarker bottomRight,
                DetectedMarker bottomLeft,
                double score,
                double regionAreaRatio,
                double sizeSimilarity,
                double containmentRatio
        ) {

            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomRight = bottomRight;
            this.bottomLeft = bottomLeft;

            this.score = score;
            this.regionAreaRatio =
                    regionAreaRatio;
            this.sizeSimilarity =
                    sizeSimilarity;
            this.containmentRatio =
                    containmentRatio;
        }

        private ResolvedMarkerSet toResolvedMarkerSet() {

            Map<CornerRole, ResolvedMarker> markers =
                    new EnumMap<>(
                            CornerRole.class
                    );

            markers.put(
                    CornerRole.TOP_LEFT,
                    new ResolvedMarker(
                            CornerRole.TOP_LEFT,
                            topLeft
                    )
            );

            markers.put(
                    CornerRole.TOP_RIGHT,
                    new ResolvedMarker(
                            CornerRole.TOP_RIGHT,
                            topRight
                    )
            );

            markers.put(
                    CornerRole.BOTTOM_RIGHT,
                    new ResolvedMarker(
                            CornerRole.BOTTOM_RIGHT,
                            bottomRight
                    )
            );

            markers.put(
                    CornerRole.BOTTOM_LEFT,
                    new ResolvedMarker(
                            CornerRole.BOTTOM_LEFT,
                            bottomLeft
                    )
            );

            return new ResolvedMarkerSet(
                    markers,
                    score,
                    regionAreaRatio,
                    sizeSimilarity,
                    containmentRatio
            );
        }
    }
}