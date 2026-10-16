package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class OmrAssessmentResultExportFormatter {

    private OmrAssessmentResultExportFormatter() {
    }

    static String status(OmrAssessmentResultReport.Row row) {
        switch (row.getDetailStatus()) {
            case FINAL:
                return "Corrigido - final";
            case ZERO:
                return "Corrigido - nota zero";
            case IN_REVIEW:
                return "Corrigido - requer revisão";
            case ABSENT:
                return "Correção sem histórico disponível";
            case PENDING:
                return "Pendente";
            case INCONSISTENT:
                return "Inconsistência entre correção e estrutura";
            default:
                throw new IllegalStateException(
                        "Situação de exportação desconhecida."
                );
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
