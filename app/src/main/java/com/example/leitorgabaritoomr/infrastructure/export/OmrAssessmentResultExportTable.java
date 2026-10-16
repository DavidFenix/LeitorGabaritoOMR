package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockBreakdown;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockResult;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Colunas curriculares congeladas compartilhadas pelas exportacoes.
 *
 * Registros antigos, sem fotografia curricular na aplicacao, continuam sem
 * colunas dinamicas. Isso preserva o formato historico mesmo que a camada de
 * leitura consiga reconstruir um bloco legado para exibir o resultado.
 */
final class OmrAssessmentResultExportTable {

    static final class BlockColumn {
        private final OmrAssessmentBlock block;
        private final String label;

        private BlockColumn(OmrAssessmentBlock block) {
            this.block = block;
            this.label = buildLabel(block);
        }

        OmrAssessmentBlock getBlock() {
            return block;
        }

        String getLabel() {
            return label;
        }

        String getCorrectHeader() {
            return label + " | ACERTOS";
        }

        String getScoreHeader() {
            return label + " | NOTA";
        }

        String getQuestionRange() {
            return block.getFirstQuestionPosition()
                    + "-" + block.getLastQuestionPosition();
        }

        String getPolicyIdentity() {
            return block.getScoringPolicy().getPolicyId()
                    + "@v" + block.getScoringPolicy().getVersion();
        }

        private static String buildLabel(OmrAssessmentBlock block) {
            StringBuilder label = new StringBuilder();
            if (block.hasAreaName()) {
                label.append(block.getAreaName()).append(" | ");
            }
            if (block.hasComponentCode()) {
                label.append(block.getComponentCode()).append(" - ");
            }
            return label.append(block.getComponentName()).toString();
        }
    }

    private final OmrAssessmentStructure structure;
    private final List<BlockColumn> columns;

    private OmrAssessmentResultExportTable(
            OmrAssessmentStructure structure,
            List<BlockColumn> columns
    ) {
        this.structure = structure;
        this.columns = Collections.unmodifiableList(columns);
    }

    static OmrAssessmentResultExportTable from(
            OmrAssessmentResultReport report
    ) {
        if (report == null) {
            throw new IllegalArgumentException(
                    "O relatorio e obrigatorio."
            );
        }
        OmrAssessmentStructure structure = report.getApplication()
                .getFrozenStructureOrNull();
        if (structure == null || structure.isLegacySingleBlock()) {
            return new OmrAssessmentResultExportTable(
                    null,
                    Collections.emptyList()
            );
        }
        List<BlockColumn> columns = new ArrayList<>(
                structure.getBlockCount()
        );
        for (OmrAssessmentBlock block : structure.getBlocks()) {
            columns.add(new BlockColumn(block));
        }
        return new OmrAssessmentResultExportTable(structure, columns);
    }

    boolean hasBlockColumns() {
        return !columns.isEmpty();
    }

    List<BlockColumn> getBlockColumns() {
        return columns;
    }

    int getBlockCount() {
        return columns.size();
    }

    OmrAssessmentBlockResult findResult(
            OmrAssessmentResultReport.Row row,
            BlockColumn column
    ) {
        if (row == null || column == null || structure == null
                || !row.hasBlockBreakdown()) {
            return null;
        }
        OmrAssessmentBlockBreakdown breakdown =
                row.getBlockBreakdownOrNull();
        if (!structure.equals(breakdown.getStructure())) {
            return null;
        }
        return breakdown.findResultByBlockId(
                column.getBlock().getBlockId()
        );
    }
}
