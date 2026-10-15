package com.example.leitorgabaritoomr.domain.assessment;

import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detalhamento completo de uma correcao pelos blocos da prova.
 *
 * O resultado OMR original permanece anexado para auditoria. A nota aqui e
 * calculada; uma futura nota ajustada para o SIGE sera outro dado, sem
 * sobrescrever este valor.
 */
public final class OmrAssessmentBlockBreakdown
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private final OmrAssessmentStructure structure;
    private final OmrGradingResult gradingResult;
    private final List<OmrAssessmentBlockResult> blockResults;
    private final Map<String, OmrAssessmentBlockResult> resultsByBlockId;

    OmrAssessmentBlockBreakdown(
            OmrAssessmentStructure structure,
            OmrGradingResult gradingResult,
            List<OmrAssessmentBlockResult> blockResults
    ) {
        if (structure == null) {
            throw new IllegalArgumentException(
                    "A estrutura da prova e obrigatoria."
            );
        }

        if (gradingResult == null) {
            throw new IllegalArgumentException(
                    "O resultado da correcao e obrigatorio."
            );
        }

        if (blockResults == null
                || blockResults.size() != structure.getBlockCount()) {
            throw new IllegalArgumentException(
                    "Deve existir um resultado para cada bloco."
            );
        }

        List<OmrAssessmentBlockResult> resultCopy =
                new ArrayList<>(blockResults.size());

        Map<String, OmrAssessmentBlockResult> resultMap =
                new LinkedHashMap<>(blockResults.size());

        for (int index = 0;
             index < blockResults.size();
             index++) {

            OmrAssessmentBlockResult result =
                    blockResults.get(index);

            if (result == null) {
                throw new IllegalArgumentException(
                        "A lista nao pode conter resultados nulos."
                );
            }

            OmrAssessmentBlock expectedBlock =
                    structure.getBlocks().get(index);

            if (!expectedBlock.equals(result.getBlock())) {
                throw new IllegalArgumentException(
                        "O resultado nao corresponde ao bloco"
                                + " esperado na posicao "
                                + (index + 1)
                                + "."
                );
            }

            resultCopy.add(result);
            resultMap.put(expectedBlock.getBlockId(), result);
        }

        this.structure = structure;
        this.gradingResult = gradingResult;
        this.blockResults = Collections.unmodifiableList(resultCopy);
        this.resultsByBlockId = Collections.unmodifiableMap(resultMap);
    }

    public OmrAssessmentStructure getStructure() {
        return structure;
    }

    public OmrGradingResult getGradingResult() {
        return gradingResult;
    }

    public List<OmrAssessmentBlockResult> getBlockResults() {
        return blockResults;
    }

    public int getBlockCount() {
        return blockResults.size();
    }

    public OmrAssessmentBlockResult findResultByBlockId(
            String blockId
    ) {
        if (blockId == null) {
            return null;
        }

        return resultsByBlockId.get(blockId.trim());
    }

    public OmrAssessmentBlockResult
    findResultByQuestionPosition(int questionPosition) {
        OmrAssessmentBlock block = structure
                .findBlockByQuestionPosition(questionPosition);

        return block == null
                ? null
                : resultsByBlockId.get(block.getBlockId());
    }

    public boolean requiresReview() {
        return gradingResult.requiresReview();
    }

    public boolean isComplete() {
        return gradingResult.isComplete();
    }

    public boolean isFinal() {
        return gradingResult.isFinal();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof OmrAssessmentBlockBreakdown)) {
            return false;
        }

        OmrAssessmentBlockBreakdown that =
                (OmrAssessmentBlockBreakdown) other;

        return structure.equals(that.structure)
                && gradingResult.equals(that.gradingResult)
                && blockResults.equals(that.blockResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                structure,
                gradingResult,
                blockResults
        );
    }
}
