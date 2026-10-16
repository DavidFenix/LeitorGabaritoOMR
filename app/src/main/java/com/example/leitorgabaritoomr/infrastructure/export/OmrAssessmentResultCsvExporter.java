package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockResult;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exporta um CSV UTF-8 com BOM e separador compatível com Excel em pt-BR. */
public final class OmrAssessmentResultCsvExporter {

    public void write(
            OmrAssessmentResultReport report,
            OutputStream output
    ) throws IOException {
        if (report == null || output == null) {
            throw new IllegalArgumentException(
                    "O relatório e o destino são obrigatórios."
            );
        }
        output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        output.write(build(report).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public String build(OmrAssessmentResultReport report) {
        if (report == null) {
            throw new IllegalArgumentException(
                    "O relatório é obrigatório."
            );
        }
        OmrAssessmentResultExportTable table =
                OmrAssessmentResultExportTable.from(report);
        StringBuilder csv = new StringBuilder(4096);
        append(csv, "Relatório", "Resultados da aplicação");
        append(csv, "Aplicação", report.getApplication().getApplicationId());
        append(csv, "Prova", report.getAssessment().getTitle());
        append(csv, "Versão", report.getAssessment().getVersion());
        append(csv, "Disciplina", report.getAssessment().getSubject());
        append(csv, "Turma", report.getFrozenClassName());
        append(csv, "Ano letivo", report.getAssessment().getSchoolYear());
        append(csv, "Gerado em", OmrAssessmentResultExportFormatter.dateTime(
                report.getGeneratedAtEpochMillis()
        ));
        append(csv, "Participantes", report.getParticipantCount());
        append(csv, "Corrigidos", report.getCorrectedCount());
        append(csv, "Pendentes", report.getPendingCount());
        append(csv, "Inconsistências",
                report.getDataIntegrityIssueCount());
        append(csv, "Média (%)", OmrAssessmentResultExportFormatter.decimal(
                report.getAveragePercentage()
        ));
        if (table.hasBlockColumns()) {
            append(csv,
                    "Detalhamento curricular",
                    "Ordem",
                    "Área",
                    "Código",
                    "Componente",
                    "Questões",
                    "Política",
                    "Nota máxima"
            );
            for (OmrAssessmentResultExportTable.BlockColumn column
                    : table.getBlockColumns()) {
                OmrAssessmentBlock block = column.getBlock();
                append(csv,
                        "Componente",
                        block.getOrder(),
                        block.hasAreaName() ? block.getAreaName() : "",
                        block.hasComponentCode()
                                ? block.getComponentCode() : "",
                        block.getComponentName(),
                        column.getQuestionRange(),
                        column.getPolicyIdentity(),
                        OmrAssessmentResultExportFormatter.decimal(
                                block.getScoringPolicy()
                                        .getMaximumScore().doubleValue()
                        )
                );
            }
        }
        csv.append("\r\n");
        List<Object> headers = new ArrayList<>();
        Collections.addAll(headers,
                "Posição", "Matrícula", "Aluno", "ID do aluno",
                "Situação", "Revisão", "Pontos", "Total",
                "Percentual", "Acertos", "Erros", "Em branco",
                "Para revisão", "Registrado em"
        );
        for (OmrAssessmentResultExportTable.BlockColumn column
                : table.getBlockColumns()) {
            headers.add(column.getCorrectHeader());
            headers.add(column.getScoreHeader());
        }
        append(csv, headers.toArray());

        for (OmrAssessmentResultReport.Row row : report.getRows()) {
            OmrAssessmentParticipant participant = row.getParticipant();
            List<Object> values = new ArrayList<>();
            Collections.addAll(values,
                    participant.getRosterPosition(),
                    participant.getRegistration(),
                    participant.getStudentName(),
                    participant.getStudentId(),
                    OmrAssessmentResultExportFormatter.status(row),
                    row.getRevision() == 0 ? "" : row.getRevision(),
                    row.isCorrected()
                            ? OmrAssessmentResultExportFormatter.decimal(
                            row.getAwardedPoints()) : "",
                    row.isCorrected()
                            ? OmrAssessmentResultExportFormatter.decimal(
                            row.getPossiblePoints()) : "",
                    row.isCorrected()
                            ? OmrAssessmentResultExportFormatter.decimal(
                            row.getAwardedPercentage()) : "",
                    row.isCorrected() ? row.getCorrectCount() : "",
                    row.isCorrected() ? row.getIncorrectCount() : "",
                    row.isCorrected() ? row.getBlankCount() : "",
                    row.isCorrected() ? row.getReviewRequiredCount() : "",
                    OmrAssessmentResultExportFormatter.dateTime(
                            row.getRecordedAtEpochMillis())
            );
            for (OmrAssessmentResultExportTable.BlockColumn column
                    : table.getBlockColumns()) {
                OmrAssessmentBlockResult result = table.findResult(
                        row,
                        column
                );
                if (result == null) {
                    values.add("");
                    values.add("");
                } else {
                    values.add(result.getCorrectCount());
                    values.add(OmrAssessmentResultExportFormatter.decimal(
                            result.getCalculatedScoreAsDouble()
                    ));
                }
            }
            append(csv, values.toArray());
        }
        return csv.toString();
    }

    private static void append(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                target.append(';');
            }
            String value = values[index] == null
                    ? "" : String.valueOf(values[index]);
            value = preventSpreadsheetFormula(value);
            target.append('"').append(value.replace("\"", "\"\""))
                    .append('"');
        }
        target.append("\r\n");
    }

    private static String preventSpreadsheetFormula(String value) {
        int index = 0;
        while (index < value.length()
                && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index >= value.length()) {
            return value;
        }
        char first = value.charAt(index);
        if (first == '=' || first == '+'
                || first == '-' || first == '@') {
            return value.substring(0, index)
                    + "'" + value.substring(index);
        }
        return value;
    }
}
