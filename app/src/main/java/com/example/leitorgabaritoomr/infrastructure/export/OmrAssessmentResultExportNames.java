package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;

import java.text.Normalizer;
import java.util.Locale;

/** Nomes e tipos públicos usados pelo seletor de documentos. */
public final class OmrAssessmentResultExportNames {

    public static final String CSV_MIME_TYPE = "text/csv";
    public static final String XLSX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String PDF_MIME_TYPE = "application/pdf";

    private OmrAssessmentResultExportNames() {
    }

    public static String csv(OmrAssessmentResultReport report) {
        return base(report) + ".csv";
    }

    public static String xlsx(OmrAssessmentResultReport report) {
        return base(report) + ".xlsx";
    }

    public static String pdf(OmrAssessmentResultReport report) {
        return base(report) + ".pdf";
    }

    private static String base(OmrAssessmentResultReport report) {
        if (report == null) {
            throw new IllegalArgumentException(
                    "O relatório é obrigatório."
            );
        }
        String raw = report.getAssessment().getTitle()
                + "_" + report.getFrozenClassName()
                + "_resultados";
        String ascii = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (ascii.isEmpty()) {
            ascii = "resultados_aplicacao";
        }
        return ascii.length() <= 80
                ? ascii : ascii.substring(0, 80);
    }
}
