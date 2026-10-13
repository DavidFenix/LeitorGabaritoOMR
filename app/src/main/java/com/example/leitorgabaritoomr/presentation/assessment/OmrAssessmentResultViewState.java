package com.example.leitorgabaritoomr.presentation.assessment;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;

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
        String searchable = row.getParticipant().getStudentName() + " "
                + row.getParticipant().getRegistration() + " "
                + row.getParticipant().getStudentId() + " "
                + row.getParticipant().getRosterPosition() + " "
                + row.getStatus().name() + " "
                + statusLabel(row) + " revisão " + row.getRevision();
        return searchable(searchable).contains(query);
    }

    private static String statusLabel(OmrAssessmentResultReport.Row row) {
        if (row.getStatus() == OmrAssessmentResultReport.Status.PENDING) {
            return "pendente";
        }
        if (row.getStatus()
                == OmrAssessmentResultReport.Status.MISSING_HISTORY) {
            return "inconsistente histórico indisponível";
        }
        return row.requiresReview()
                ? "corrigido revisão manual"
                : "corrigido final";
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
