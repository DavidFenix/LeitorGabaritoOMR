package com.example.leitorgabaritoomr.vision.measurement;

import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegion;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegionSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conjunto imutavel das geometrias usadas para medir todas as
 * alternativas da folha normalizada.
 *
 * O conjunto nasce diretamente de um RegisteredBubbleRegionSet
 * completo. Para cada regiao registrada e criada exatamente uma
 * BubbleSamplingGeometry, na mesma ordem recebida.
 *
 * Esta classe estabelece a ligacao unica entre tres camadas:
 *
 * - registro geometrico das bolhas;
 * - visualizacao no Laboratorio OMR;
 * - medicao de preenchimento.
 *
 * O renderizador e o medidor devem receber esta mesma instancia.
 * Assim, nao podem reconstruir retangulos, arredondar centros ou
 * aplicar escalas diferentes de forma independente.
 */
public final class BubbleSamplingGeometrySet {

    private final RegisteredBubbleRegionSet
            registeredRegionSet;

    private final BubbleMeasurementConfig config;

    private final List<BubbleSamplingGeometry> geometries;

    private final Map<String, BubbleSamplingGeometry>
            geometryByOptionId;

    private final Map<String, BubbleSamplingGeometry>
            geometryByStructuralPosition;

    private final Map<Integer, List<BubbleSamplingGeometry>>
            geometriesByBlockIndex;

    private final Map<String, List<BubbleSamplingGeometry>>
            geometriesByBlockId;

    private final Map<String, List<BubbleSamplingGeometry>>
            geometriesByQuestionPosition;

    private final int clippedBackgroundCount;

    public BubbleSamplingGeometrySet(
            RegisteredBubbleRegionSet registeredRegionSet,
            BubbleMeasurementConfig config
    ) {
        if (registeredRegionSet == null) {
            throw new IllegalArgumentException(
                    "O conjunto de regioes registradas"
                            + " e obrigatorio."
            );
        }

        if (!registeredRegionSet.isComplete()) {
            throw new IllegalArgumentException(
                    "As geometrias de amostragem exigem um"
                            + " conjunto completo de regioes"
                            + " registradas."
            );
        }

        if (config == null) {
            throw new IllegalArgumentException(
                    "A configuracao de medicao e obrigatoria."
            );
        }

        this.registeredRegionSet =
                registeredRegionSet;

        this.config = config;

        List<BubbleSamplingGeometry> mutableGeometries =
                new ArrayList<>(
                        registeredRegionSet.getRegionCount()
                );

        Map<String, BubbleSamplingGeometry> byOptionId =
                new HashMap<>();

        Map<String, BubbleSamplingGeometry>
                byStructuralPosition =
                new HashMap<>();

        Map<Integer, List<BubbleSamplingGeometry>>
                byBlockIndex =
                new HashMap<>();

        Map<String, List<BubbleSamplingGeometry>>
                byBlockId =
                new HashMap<>();

        Map<String, List<BubbleSamplingGeometry>>
                byQuestionPosition =
                new HashMap<>();

        int clippedBackgrounds = 0;

        for (RegisteredBubbleRegion registeredRegion
                : registeredRegionSet.getRegions()) {

            BubbleSamplingGeometry geometry =
                    new BubbleSamplingGeometry(
                            registeredRegion,
                            config
                    );

            validateSourceIdentity(
                    registeredRegion,
                    geometry
            );

            String optionId = geometry.getOptionId();

            if (byOptionId.put(optionId, geometry)
                    != null) {

                throw new IllegalArgumentException(
                        "optionId repetido nas geometrias: "
                                + optionId
                );
            }

            String structuralPosition =
                    createStructuralPositionKey(
                            geometry.getBlockIndex(),
                            geometry.getQuestionIndex(),
                            geometry.getOptionIndex()
                    );

            if (byStructuralPosition.put(
                    structuralPosition,
                    geometry
            ) != null) {

                throw new IllegalArgumentException(
                        "Posicao estrutural repetida nas"
                                + " geometrias: "
                                + structuralPosition
                );
            }

            mutableGeometries.add(geometry);

            addToListMap(
                    byBlockIndex,
                    geometry.getBlockIndex(),
                    geometry
            );

            addToListMap(
                    byBlockId,
                    geometry.getBlockId(),
                    geometry
            );

            addToListMap(
                    byQuestionPosition,
                    createQuestionPositionKey(
                            geometry.getBlockIndex(),
                            geometry.getQuestionIndex()
                    ),
                    geometry
            );

            if (geometry.isBackgroundClippedByImage()) {
                clippedBackgrounds++;
            }
        }

        validateCompleteness(
                registeredRegionSet,
                mutableGeometries,
                byOptionId,
                byStructuralPosition
        );

        this.geometries =
                Collections.unmodifiableList(
                        new ArrayList<>(mutableGeometries)
                );

        this.geometryByOptionId =
                immutableMap(byOptionId);

        this.geometryByStructuralPosition =
                immutableMap(byStructuralPosition);

        this.geometriesByBlockIndex =
                immutableListMap(byBlockIndex);

        this.geometriesByBlockId =
                immutableListMap(byBlockId);

        this.geometriesByQuestionPosition =
                immutableListMap(byQuestionPosition);

        this.clippedBackgroundCount =
                clippedBackgrounds;
    }

    private void validateSourceIdentity(
            RegisteredBubbleRegion registeredRegion,
            BubbleSamplingGeometry geometry
    ) {
        if (geometry.getRegisteredRegion()
                != registeredRegion) {

            throw new IllegalStateException(
                    "A geometria de "
                            + registeredRegion.getOptionId()
                            + " perdeu a identidade da regiao"
                            + " registrada."
            );
        }

        if (geometry.getConfig() != config) {
            throw new IllegalStateException(
                    "A geometria de "
                            + registeredRegion.getOptionId()
                            + " nao preservou a configuracao"
                            + " compartilhada."
            );
        }
    }

    private void validateCompleteness(
            RegisteredBubbleRegionSet source,
            List<BubbleSamplingGeometry> created,
            Map<String, BubbleSamplingGeometry> byOptionId,
            Map<String, BubbleSamplingGeometry>
                    byStructuralPosition
    ) {
        int expectedCount = source.getRegionCount();

        if (created.size() != expectedCount
                || byOptionId.size() != expectedCount
                || byStructuralPosition.size()
                != expectedCount) {

            throw new IllegalStateException(
                    "O conjunto de amostragem ficou"
                            + " incompleto: esperado="
                            + expectedCount
                            + ", criado="
                            + created.size()
                            + ", ids="
                            + byOptionId.size()
                            + ", posicoes="
                            + byStructuralPosition.size()
            );
        }

        for (int index = 0;
             index < expectedCount;
             index++) {

            if (created.get(index)
                    .getRegisteredRegion()
                    != source.getRegions().get(index)) {

                throw new IllegalStateException(
                        "A ordem das geometrias divergiu"
                                + " na posicao "
                                + index
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
            Map<K, List<BubbleSamplingGeometry>> map,
            K key,
            BubbleSamplingGeometry geometry
    ) {
        List<BubbleSamplingGeometry> values =
                map.get(key);

        if (values == null) {
            values = new ArrayList<>();
            map.put(key, values);
        }

        values.add(geometry);
    }

    private <K, V> Map<K, V> immutableMap(
            Map<K, V> source
    ) {
        return Collections.unmodifiableMap(
                new HashMap<>(source)
        );
    }

    private <K> Map<K, List<BubbleSamplingGeometry>>
    immutableListMap(
            Map<K, List<BubbleSamplingGeometry>> source
    ) {
        Map<K, List<BubbleSamplingGeometry>> copy =
                new HashMap<>();

        for (Map.Entry<K, List<BubbleSamplingGeometry>> entry
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

    public RegisteredBubbleRegionSet
    getRegisteredRegionSet() {
        return registeredRegionSet;
    }

    public BubbleMeasurementConfig getConfig() {
        return config;
    }

    public int getImageWidth() {
        return registeredRegionSet.getImageWidth();
    }

    public int getImageHeight() {
        return registeredRegionSet.getImageHeight();
    }

    public List<BubbleSamplingGeometry> getGeometries() {
        return geometries;
    }

    public int getGeometryCount() {
        return geometries.size();
    }

    public BubbleSamplingGeometry findByOptionId(
            String optionId
    ) {
        if (optionId == null) {
            return null;
        }

        return geometryByOptionId.get(optionId);
    }

    public BubbleSamplingGeometry findByPosition(
            int blockIndex,
            int questionIndex,
            int optionIndex
    ) {
        return geometryByStructuralPosition.get(
                createStructuralPositionKey(
                        blockIndex,
                        questionIndex,
                        optionIndex
                )
        );
    }

    public List<BubbleSamplingGeometry>
    getGeometriesForBlock(int blockIndex) {
        List<BubbleSamplingGeometry> values =
                geometriesByBlockIndex.get(blockIndex);

        return values == null
                ? Collections
                .<BubbleSamplingGeometry>emptyList()
                : values;
    }

    public List<BubbleSamplingGeometry>
    getGeometriesForBlock(String blockId) {
        if (blockId == null) {
            return Collections.emptyList();
        }

        List<BubbleSamplingGeometry> values =
                geometriesByBlockId.get(blockId);

        return values == null
                ? Collections
                .<BubbleSamplingGeometry>emptyList()
                : values;
    }

    public List<BubbleSamplingGeometry>
    getGeometriesForQuestion(
            int blockIndex,
            int questionIndex
    ) {
        List<BubbleSamplingGeometry> values =
                geometriesByQuestionPosition.get(
                        createQuestionPositionKey(
                                blockIndex,
                                questionIndex
                        )
                );

        return values == null
                ? Collections
                .<BubbleSamplingGeometry>emptyList()
                : values;
    }

    public int getBlockCount() {
        return geometriesByBlockIndex.size();
    }

    public int getClippedBackgroundCount() {
        return clippedBackgroundCount;
    }

    public boolean hasClippedBackgrounds() {
        return clippedBackgroundCount > 0;
    }

    public boolean isComplete() {
        return registeredRegionSet.isComplete()
                && getGeometryCount()
                == registeredRegionSet.getRegionCount()
                && geometryByOptionId.size()
                == getGeometryCount()
                && geometryByStructuralPosition.size()
                == getGeometryCount();
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "samplingGeometries=%d blocks=%d"
                        + " image=%dx%d clippedBackgrounds=%d"
                        + " complete=%s",
                getGeometryCount(),
                getBlockCount(),
                getImageWidth(),
                getImageHeight(),
                clippedBackgroundCount,
                isComplete()
        );
    }
}
