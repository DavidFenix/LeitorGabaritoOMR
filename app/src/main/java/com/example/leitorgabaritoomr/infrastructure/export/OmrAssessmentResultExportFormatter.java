package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class OmrAssessmentResultExportFormatter {

    private OmrAssessmentResultExportFormatter() {
    }

    static String status(OmrAssessmentResultReport.Row row) {
        switch (row.getStatus()) {
            case CORRECTED:
                if (row.requiresReview()) {
                    return "Corrigido - requer revisão";
                }
                return row.isFinal()
                        ? "Corrigido - final"
                        : "Corrigido - provisório";
            case MISSING_HISTORY:
                return "Correção sem histórico disponível";
            case PENDING:
            default:
                return "Pendente";
        }
    }

    static String dateTime(long epochMillis) {
        if (epochMillis <= 0L) {
            return "";
        }
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.ROOT
        ).format(new Date(epochMillis));
    }

    static String decimal(double value) {
        return String.format(new Locale("pt", "BR"), "%.2f", value);
    }
}
