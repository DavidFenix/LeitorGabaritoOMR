package com.example.leitorgabaritoomr.presentation.assessment;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockResult;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Estado imutável da tela de resultados consolidados. */
public final class OmrAssessmentResultViewState {

    private final OmrAssessmentResultReport report;
    private final String query;
    private final List<OmrAssessmentResultReport.Row> visibleRows;

    private OmrAssessmentResultViewState(
            OmrAssessmentResultReport report,
            String query,
            List<OmrAssessmentResultReport.Row> visibleRows
    ) {
        this.report = report;
        this.query = query;
        this.visibleRows = Collections.unmodifiableList(
                new ArrayList<>(visibleRows)
        );
    }

    public static OmrAssessmentResultViewState from(
            OmrAssessmentResultReport report,
            String query
    ) {
        if (report == null) {
            throw new IllegalArgumentException(
                    "O relatório de resultados é obrigatório."
            );
        }
        String normalizedQuery = query == null ? "" : query.trim();
        String searchableQuery = searchable(normalizedQuery);
        List<OmrAssessmentResultReport.Row> visible = new ArrayList<>();
        for (OmrAssessmentResultReport.Row row : report.getRows()) {
            if (searchableQuery.isEmpty() || matches(row, searchableQuery)) {
                visible.add(row);
            }
        }
        return new OmrAssessmentResultViewState(
                report,
                normalizedQuery,
                visible
        );
    }

    private static boolean matches(
            OmrAssessmentResultReport.Row row,
            String query
    ) {
        StringBuilder searchable = new StringBuilder()
                .append(row.getParticipant().getStudentName()).append(' ')
                .append(row.getParticipant().getRegistration()).append(' ')
                .append(row.getParticipant().getStudentId()).append(' ')
                .append(row.getParticipant().getRosterPosition()).append(' ')
                .append(row.getStatus().name()).append(' ')
                .append(row.getDetailStatus().name()).append(' ')
                .append(statusLabel(row)).append(" revisão ")
                .append(row.getRevision());
        if (row.hasBlockBreakdown()) {
            for (OmrAssessmentBlockResult result
                    : row.getBlockBreakdownOrNull().getBlockResults()) {
                searchable.append(' ')
                        .append(result.getBlock().getBlockId()).append(' ')
                        .append(result.getBlock().getComponentName());
                if (result.getBlock().hasAreaName()) {
                    searchable.append(' ')
                            .append(result.getBlock().getAreaName());
                }
                if (result.getBlock().hasComponentCode()) {
                    searchable.append(' ')
                            .append(result.getBlock().getComponentCode());
                }
            }
        }
        return searchable(searchable.toString()).contains(query);
    }

    private static String statusLabel(OmrAssessmentResultReport.Row row) {
        switch (row.getDetailStatus()) {
            case PENDING:
                return "pendente";
            case ABSENT:
                return "ausente histórico indisponível";
            case INCONSISTENT:
                return "inconsistente estrutura inválida";
            case IN_REVIEW:
                return "corrigido em revisão provisório";
            case ZERO:
                return "corrigido zero nota zero";
            case FINAL:
                return "corrigido final";
            default:
                throw new IllegalStateException("Situação desconhecida.");
        }
    }

    private static String searchable(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public OmrAssessmentResultReport getReport() {
        return report;
    }

    public String getQuery() {
        return query;
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public List<OmrAssessmentResultReport.Row> getVisibleRows() {
        return visibleRows;
    }

    public int getVisibleCount() {
        return visibleRows.size();
    }

    public boolean hasNoMatches() {
        return hasQuery() && visibleRows.isEmpty();
    }
}
