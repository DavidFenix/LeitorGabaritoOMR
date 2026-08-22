package com.example.leitorgabaritoomr.vision.registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conjunto imutavel de todas as regioes de bolha registradas na
 * imagem normalizada.
 *
 * Uma instancia valida representa uma folha mensuravel completa:
 *
 * - o registro geometrico terminou com sucesso;
 * - todos os blocos foram aceitos;
 * - existe exatamente uma regiao para cada alvo do layout;
 * - nenhuma posicao estrutural foi repetida;
 * - todas as regioes pertencem a mesma imagem e ao mesmo resultado
 *   de registro.
 *
 * Os indices estruturais sao independentes do numero impresso da
 * questao. Assim, layouts futuros poderao possuir sequencias como
 * 1 a 10 e depois 1 a 30, desde que cada questao conserve sua
 * posicao interna dentro do respectivo bloco.
 */
public final class RegisteredBubbleRegionSet {

    private final int imageWidth;
    private final int imageHeight;

    private final BubbleGridRegistrationResult
            registrationResult;

    private final List<RegisteredBubbleRegion> regions;

    private final Map<String, RegisteredBubbleRegion>
            regionByOptionId;

    private final Map<String, RegisteredBubbleRegion>
            regionByStructuralPosition;

    private final Map<Integer, List<RegisteredBubbleRegion>>
            regionsByBlockIndex;

    private final Map<String, List<RegisteredBubbleRegion>>
            regionsByBlockId;

    private final Map<String, List<RegisteredBubbleRegion>>
            regionsByQuestionPosition;

    private final int clippedRegionCount;

    private final double minimumPolygonArea;
    private final double maximumPolygonArea;
    private final double meanPolygonArea;

    private final double meanNominalWidth;
    private final double meanNominalHeight;

    public RegisteredBubbleRegionSet(
            int imageWidth,
            int imageHeight,
            BubbleGridRegistrationResult registrationResult,
            List<RegisteredBubbleRegion> regions
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    "As dimensoes da imagem devem ser positivas."
            );
        }

        if (registrationResult == null) {
            throw new IllegalArgumentException(
                    "O resultado do registro e obrigatorio."
            );
        }

        if (!registrationResult.isSuccess()) {
            throw new IllegalArgumentException(
                    "O registro geometrico nao terminou"
                            + " com sucesso: "
                            + registrationResult.getMessage()
            );
        }

        if (!registrationResult.areAllBlocksAccepted()) {
            throw new IllegalArgumentException(
                    "Todas as regioes exigem todos os blocos"
                            + " aceitos."
            );
        }

        if (regions == null) {
            throw new IllegalArgumentException(
                    "A lista de regioes e obrigatoria."
            );
        }

        if (regions.size()
                != registrationResult.getTargetCount()) {

            throw new IllegalArgumentException(
                    "A quantidade de regioes ("
                            + regions.size()
                            + ") difere da quantidade de alvos ("
                            + registrationResult.getTargetCount()
                            + ")."
            );
        }

        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.registrationResult = registrationResult;

        Aggregation aggregation =
                aggregate(
                        regions,
                        imageWidth,
                        imageHeight,
                        registrationResult
                );

        this.regions =
                Collections.unmodifiableList(
                        new ArrayList<>(regions)
                );

        this.regionByOptionId =
                immutableMap(
                        aggregation.regionByOptionId
                );

        this.regionByStructuralPosition =
                immutableMap(
                        aggregation
                                .regionByStructuralPosition
                );

        this.regionsByBlockIndex =
                immutableListMap(
                        aggregation.regionsByBlockIndex
                );

        this.regionsByBlockId =
                immutableListMap(
                        aggregation.regionsByBlockId
                );

        this.regionsByQuestionPosition =
                immutableListMap(
                        aggregation
                                .regionsByQuestionPosition
                );

        this.clippedRegionCount =
                aggregation.clippedRegionCount;

        this.minimumPolygonArea =
                aggregation.minimumPolygonArea;

        this.maximumPolygonArea =
                aggregation.maximumPolygonArea;

        this.meanPolygonArea =
                regions.isEmpty()
                        ? 0.0
                        : aggregation.polygonAreaSum
                        / regions.size();

        this.meanNominalWidth =
                regions.isEmpty()
                        ? 0.0
                        : aggregation.nominalWidthSum
                        / regions.size();

        this.meanNominalHeight =
                regions.isEmpty()
                        ? 0.0
                        : aggregation.nominalHeightSum
                        / regions.size();
    }

    private Aggregation aggregate(
            List<RegisteredBubbleRegion> source,
            int expectedImageWidth,
            int expectedImageHeight,
            BubbleGridRegistrationResult expectedResult
    ) {
        Map<String, RegisteredBubbleRegion> byOptionId =
                new HashMap<>();

        Map<String, RegisteredBubbleRegion>
                byStructuralPosition =
                new HashMap<>();

        Map<Integer, List<RegisteredBubbleRegion>>
                byBlockIndex =
                new HashMap<>();

        Map<String, List<RegisteredBubbleRegion>>
                byBlockId =
                new HashMap<>();

        Map<String, List<RegisteredBubbleRegion>>
                byQuestionPosition =
                new HashMap<>();

        Set<String> optionIds = new HashSet<>();
        Set<String> structuralPositions = new HashSet<>();

        int clippedCount = 0;

        double minimumArea =
                Double.POSITIVE_INFINITY;

        double maximumArea = 0.0;
        double areaSum = 0.0;
        double widthSum = 0.0;
        double heightSum = 0.0;

        for (RegisteredBubbleRegion region : source) {
            if (region == null) {
                throw new IllegalArgumentException(
                        "A lista possui regiao nula."
                );
            }

            validateImageIdentity(
                    region,
                    expectedImageWidth,
                    expectedImageHeight
            );

            BubbleBlockRegistration expectedRegistration =
                    expectedResult.findByBlockIndex(
                            region.getBlockIndex()
                    );

            if (expectedRegistration == null) {
                throw new IllegalArgumentException(
                        "A regiao "
                                + region.getOptionId()
                                + " referencia bloco inexistente."
                );
            }

            if (region.getRegistration()
                    != expectedRegistration) {

                throw new IllegalArgumentException(
                        "A regiao "
                                + region.getOptionId()
                                + " nao veio do mesmo resultado"
                                + " de registro."
                );
            }

            String optionId = region.getOptionId();

            if (optionId == null
                    || optionId.trim().isEmpty()) {

                throw new IllegalArgumentException(
                        "Toda regiao deve possuir optionId."
                );
            }

            if (!optionIds.add(optionId)) {
                throw new IllegalArgumentException(
                        "optionId repetido: " + optionId
                );
            }

            String structuralPosition =
                    createStructuralPositionKey(
                            region.getBlockIndex(),
                            region.getQuestionIndex(),
                            region.getOptionIndex()
                    );

            if (!structuralPositions.add(
                    structuralPosition
            )) {

                throw new IllegalArgumentException(
                        "Posicao estrutural repetida: "
                                + structuralPosition
                );
            }

            byOptionId.put(optionId, region);

            byStructuralPosition.put(
                    structuralPosition,
                    region
            );

            addToListMap(
                    byBlockIndex,
                    region.getBlockIndex(),
                    region
            );

            addToListMap(
                    byBlockId,
                    region.getBlockId(),
                    region
            );

            addToListMap(
                    byQuestionPosition,
                    createQuestionPositionKey(
                            region.getBlockIndex(),
                            region.getQuestionIndex()
                    ),
                    region
            );

            if (region.isClippedByImage()) {
                clippedCount++;
            }

            double area = region.getPolygonArea();

            minimumArea = Math.min(
                    minimumArea,
                    area
            );

            maximumArea = Math.max(
                    maximumArea,
                    area
            );

            areaSum += area;
            widthSum += region.getNominalWidth();
            heightSum += region.getNominalHeight();
        }

        validateBlockRegionCounts(
                expectedResult,
                byBlockIndex
        );

        if (source.isEmpty()) {
            minimumArea = 0.0;
        }

        return new Aggregation(
                byOptionId,
                byStructuralPosition,
                byBlockIndex,
                byBlockId,
                byQuestionPosition,
                clippedCount,
                minimumArea,
                maximumArea,
                areaSum,
                widthSum,
                heightSum
        );
    }

    private void validateImageIdentity(
            RegisteredBubbleRegion region,
            int expectedWidth,
            int expectedHeight
    ) {
        if (region.getImageWidth() != expectedWidth
                || region.getImageHeight()
                != expectedHeight) {

            throw new IllegalArgumentException(
                    "A regiao "
                            + region.getOptionId()
                            + " pertence a imagem "
                            + region.getImageWidth()
                            + "x"
                            + region.getImageHeight()
                            + ", mas o conjunto usa "
                            + expectedWidth
                            + "x"
                            + expectedHeight
                            + "."
            );
        }
    }

    private void validateBlockRegionCounts(
            BubbleGridRegistrationResult result,
            Map<Integer, List<RegisteredBubbleRegion>>
                    byBlockIndex
    ) {
        for (BubbleBlockRegistration registration
                : result.getBlockRegistrations()) {

            List<RegisteredBubbleRegion> blockRegions =
                    byBlockIndex.get(
                            registration.getBlockIndex()
                    );

            int actualCount =
                    blockRegions == null
                            ? 0
                            : blockRegions.size();

            if (actualCount
                    != registration.getTargetCount()) {

                throw new IllegalArgumentException(
                        "O bloco "
                                + registration.getBlockId()
                                + " possui "
                                + actualCount
                                + " regioes, mas esperava "
                                + registration.getTargetCount()
                                + "."
                );
            }
        }
    }

    private String createStructuralPositionKey(
            int blockIndex,
            int questionIndex,
            int optionIndex
    ) {
        return blockIndex
                + ":"
                + questionIndex
                + ":"
                + optionIndex;
    }

    private String createQuestionPositionKey(
            int blockIndex,
            int questionIndex
    ) {
        return blockIndex
                + ":"
                + questionIndex;
    }

    private <K> void addToListMap(
            Map<K, List<RegisteredBubbleRegion>> map,
            K key,
            RegisteredBubbleRegion region
    ) {
        List<RegisteredBubbleRegion> values =
                map.get(key);

        if (values == null) {
            values = new ArrayList<>();
            map.put(key, values);
        }

        values.add(region);
    }

    private <K, V> Map<K, V> immutableMap(
            Map<K, V> source
    ) {
        return Collections.unmodifiableMap(
                new HashMap<>(source)
        );
    }

    private <K> Map<K, List<RegisteredBubbleRegion>>
    immutableListMap(
            Map<K, List<RegisteredBubbleRegion>> source
    ) {
        Map<K, List<RegisteredBubbleRegion>> copy =
                new HashMap<>();

        for (Map.Entry<K, List<RegisteredBubbleRegion>> entry
                : source.entrySet()) {

            copy.put(
                    entry.getKey(),
                    Collections.unmodifiableList(
                            new ArrayList<>(
                                    entry.getValue()
                            )
                    )
            );
        }

        return Collections.unmodifiableMap(copy);
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public BubbleGridRegistrationResult
    getRegistrationResult() {
        return registrationResult;
    }

    public List<RegisteredBubbleRegion> getRegions() {
        return regions;
    }

    public int getRegionCount() {
        return regions.size();
    }

    public RegisteredBubbleRegion findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return regionByOptionId.get(optionId);
    }

    public RegisteredBubbleRegion findByPosition(
            int blockIndex,
            int questionIndex,
            int optionIndex
    ) {
        return regionByStructuralPosition.get(
                createStructuralPositionKey(
                        blockIndex,
                        questionIndex,
                        optionIndex
                )
        );
    }

    public List<RegisteredBubbleRegion> getRegionsForBlock(
            int blockIndex
    ) {
        List<RegisteredBubbleRegion> values =
                regionsByBlockIndex.get(blockIndex);

        return values == null
                ? Collections
                .<RegisteredBubbleRegion>emptyList()
                : values;
    }

    public List<RegisteredBubbleRegion> getRegionsForBlock(
            String blockId
    ) {
        if (blockId == null) {
            return Collections.emptyList();
        }

        List<RegisteredBubbleRegion> values =
                regionsByBlockId.get(blockId);

        return values == null
                ? Collections
                .<RegisteredBubbleRegion>emptyList()
                : values;
    }

    public List<RegisteredBubbleRegion>
    getRegionsForQuestion(
            int blockIndex,
            int questionIndex
    ) {
        List<RegisteredBubbleRegion> values =
                regionsByQuestionPosition.get(
                        createQuestionPositionKey(
                                blockIndex,
                                questionIndex
                        )
                );

        return values == null
                ? Collections
                .<RegisteredBubbleRegion>emptyList()
                : values;
    }

    public int getBlockCount() {
        return regionsByBlockIndex.size();
    }

    public int getClippedRegionCount() {
        return clippedRegionCount;
    }

    public boolean hasClippedRegions() {
        return clippedRegionCount > 0;
    }

    public double getMinimumPolygonArea() {
        return minimumPolygonArea;
    }

    public double getMaximumPolygonArea() {
        return maximumPolygonArea;
    }

    public double getMeanPolygonArea() {
        return meanPolygonArea;
    }

    public double getMeanNominalWidth() {
        return meanNominalWidth;
    }

    public double getMeanNominalHeight() {
        return meanNominalHeight;
    }

    public boolean isComplete() {
        return getRegionCount()
                == registrationResult.getTargetCount()
                && registrationResult.areAllBlocksAccepted();
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "registeredRegions=%d blocks=%d image=%dx%d"
                        + " clipped=%d meanSize=(%.2f, %.2f)"
                        + " meanArea=%.2f registrationConfidence=%.3f",
                getRegionCount(),
                getBlockCount(),
                imageWidth,
                imageHeight,
                clippedRegionCount,
                meanNominalWidth,
                meanNominalHeight,
                meanPolygonArea,
                registrationResult.getSheetConfidence()
        );
    }

    private static final class Aggregation {

        private final Map<String, RegisteredBubbleRegion>
                regionByOptionId;

        private final Map<String, RegisteredBubbleRegion>
                regionByStructuralPosition;

        private final Map<Integer, List<RegisteredBubbleRegion>>
                regionsByBlockIndex;

        private final Map<String, List<RegisteredBubbleRegion>>
                regionsByBlockId;

        private final Map<String, List<RegisteredBubbleRegion>>
                regionsByQuestionPosition;

        private final int clippedRegionCount;

        private final double minimumPolygonArea;
        private final double maximumPolygonArea;
        private final double polygonAreaSum;

        private final double nominalWidthSum;
        private final double nominalHeightSum;

        private Aggregation(
                Map<String, RegisteredBubbleRegion>
                        regionByOptionId,
                Map<String, RegisteredBubbleRegion>
                        regionByStructuralPosition,
                Map<Integer, List<RegisteredBubbleRegion>>
                        regionsByBlockIndex,
                Map<String, List<RegisteredBubbleRegion>>
                        regionsByBlockId,
                Map<String, List<RegisteredBubbleRegion>>
                        regionsByQuestionPosition,
                int clippedRegionCount,
                double minimumPolygonArea,
                double maximumPolygonArea,
                double polygonAreaSum,
                double nominalWidthSum,
                double nominalHeightSum
        ) {
            this.regionByOptionId = regionByOptionId;
            this.regionByStructuralPosition =
                    regionByStructuralPosition;
            this.regionsByBlockIndex =
                    regionsByBlockIndex;
            this.regionsByBlockId = regionsByBlockId;
            this.regionsByQuestionPosition =
                    regionsByQuestionPosition;
            this.clippedRegionCount = clippedRegionCount;
            this.minimumPolygonArea = minimumPolygonArea;
            this.maximumPolygonArea = maximumPolygonArea;
            this.polygonAreaSum = polygonAreaSum;
            this.nominalWidthSum = nominalWidthSum;
            this.nominalHeightSum = nominalHeightSum;
        }
    }
}
