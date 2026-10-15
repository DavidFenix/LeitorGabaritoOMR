package com.example.leitorgabaritoomr.domain.assessment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Estrutura curricular de uma versao exata de prova.
 *
 * Os blocos devem cobrir, sem lacunas nem sobreposicoes, todas as posicoes
 * do gabarito. A geometria do cartao nao e alterada: o vinculo e somente
 * pela posicao logica da questao ja presente no resultado OMR.
 */
public final class OmrAssessmentStructure
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String LEGACY_BLOCK_ID =
            "resultado-geral";

    public static final String LEGACY_BLOCK_NAME =
            "Resultado geral";

    private final String assessmentId;
    private final int assessmentVersion;
    private final int questionCount;
    private final List<OmrAssessmentBlock> blocks;
    private final Map<String, OmrAssessmentBlock> blocksById;

    public OmrAssessmentStructure(
            String assessmentId,
            int assessmentVersion,
            int questionCount,
            List<OmrAssessmentBlock> blocks
    ) {
        this.assessmentId = requireText(
                "assessmentId",
                assessmentId
        );

        if (assessmentVersion <= 0) {
            throw new IllegalArgumentException(
                    "A versao da prova deve ser positiva."
            );
        }

        if (questionCount <= 0) {
            throw new IllegalArgumentException(
                    "A prova deve possuir pelo menos uma questao."
            );
        }

        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "A estrutura deve possuir pelo menos um bloco."
            );
        }

        List<OmrAssessmentBlock> blockCopy =
                new ArrayList<>(blocks.size());

        Map<String, OmrAssessmentBlock> blockMap =
                new LinkedHashMap<>(blocks.size());

        long expectedFirstPosition = 1L;

        for (int index = 0; index < blocks.size(); index++) {
            OmrAssessmentBlock block = blocks.get(index);

            if (block == null) {
                throw new IllegalArgumentException(
                        "A lista de blocos nao pode conter valores nulos."
                );
            }

            int expectedOrder = index + 1;

            if (block.getOrder() != expectedOrder) {
                throw new IllegalArgumentException(
                        "A ordem do bloco deve acompanhar a lista:"
                                + " esperado="
                                + expectedOrder
                                + ", recebido="
                                + block.getOrder()
                                + "."
                );
            }

            if (block.getFirstQuestionPosition()
                    != expectedFirstPosition) {
                throw new IllegalArgumentException(
                        "Os blocos devem ser contiguos e iniciar"
                                + " na questao esperada: esperado="
                                + expectedFirstPosition
                                + ", recebido="
                                + block.getFirstQuestionPosition()
                                + "."
                );
            }

            if (blockMap.put(block.getBlockId(), block) != null) {
                throw new IllegalArgumentException(
                        "Identificador de bloco repetido: "
                                + block.getBlockId()
                );
            }

            blockCopy.add(block);
            expectedFirstPosition =
                    (long) block.getLastQuestionPosition() + 1L;
        }

        long coveredQuestionCount = expectedFirstPosition - 1L;

        if (coveredQuestionCount != questionCount) {
            throw new IllegalArgumentException(
                    "Os blocos devem cobrir exatamente as questoes"
                            + " da prova: cobertas="
                            + coveredQuestionCount
                            + ", prova="
                            + questionCount
                            + "."
            );
        }

        this.assessmentVersion = assessmentVersion;
        this.questionCount = questionCount;
        this.blocks = Collections.unmodifiableList(blockCopy);
        this.blocksById = Collections.unmodifiableMap(blockMap);
    }

    /**
     * Compatibilidade para provas anteriores ao recurso multidisciplinar.
     *
     * O bloco unico usa pontos ponderados, reproduzindo a porcentagem que o
     * fluxo historico ja calculava a partir dos pesos do gabarito.
     */
    public static OmrAssessmentStructure legacySingleBlock(
            String assessmentId,
            int assessmentVersion,
            int questionCount
    ) {
        OmrAssessmentBlock block = new OmrAssessmentBlock(
                LEGACY_BLOCK_ID,
                1,
                null,
                null,
                LEGACY_BLOCK_NAME,
                1,
                questionCount,
                OmrProportionalScoringPolicy
                        .weightedPointsToTenV1()
        );

        return new OmrAssessmentStructure(
                assessmentId,
                assessmentVersion,
                questionCount,
                Collections.singletonList(block)
        );
    }

    private static String requireText(
            String fieldName,
            String value
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " nao pode ser vazio."
            );
        }

        return value.trim();
    }

    public String getAssessmentId() {
        return assessmentId;
    }

    public int getAssessmentVersion() {
        return assessmentVersion;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public List<OmrAssessmentBlock> getBlocks() {
        return blocks;
    }

    public int getBlockCount() {
        return blocks.size();
    }

    public boolean hasAssessmentIdentity(
            String candidateAssessmentId,
            int candidateVersion
    ) {
        if (candidateAssessmentId == null) {
            return false;
        }

        return assessmentVersion == candidateVersion
                && assessmentId.equals(
                candidateAssessmentId.trim()
        );
    }

    public OmrAssessmentBlock findBlockById(String blockId) {
        if (blockId == null) {
            return null;
        }

        return blocksById.get(blockId.trim());
    }

    public OmrAssessmentBlock findBlockByQuestionPosition(
            int questionPosition
    ) {
        if (questionPosition <= 0
                || questionPosition > questionCount) {
            return null;
        }

        for (OmrAssessmentBlock block : blocks) {
            if (block.containsQuestionPosition(questionPosition)) {
                return block;
            }
        }

        throw new IllegalStateException(
                "A estrutura validada deixou uma questao sem bloco."
        );
    }

    public boolean isLegacySingleBlock() {
        return blocks.size() == 1
                && LEGACY_BLOCK_ID.equals(
                blocks.get(0).getBlockId()
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentStructure)) {
            return false;
        }

        OmrAssessmentStructure that =
                (OmrAssessmentStructure) other;

        return assessmentVersion == that.assessmentVersion
                && questionCount == that.questionCount
                && assessmentId.equals(that.assessmentId)
                && blocks.equals(that.blocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                assessmentId,
                assessmentVersion,
                questionCount,
                blocks
        );
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%s@v%d[questions=%d, blocks=%d]",
                assessmentId,
                assessmentVersion,
                questionCount,
                blocks.size()
        );
    }
}
