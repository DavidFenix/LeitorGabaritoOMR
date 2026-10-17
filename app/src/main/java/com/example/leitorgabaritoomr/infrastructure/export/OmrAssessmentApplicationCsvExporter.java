package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exporta aplicações no formato canônico aceito pelo importador. */
public final class OmrAssessmentApplicationCsvExporter {

    private static final byte[] UTF8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private static final String CSV_ID_PREFIX = "application-csv:";

    private static final String[] HEADERS = {
            "codigo_aplicacao",
            "prova",
            "versao_prova",
            "turma",
            "id_interno_aplicacao"
    };

    public void write(
            List<OmrAssessmentApplication> applications,
            OutputStream output
    ) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(build(applications).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public void writeTemplate(OutputStream output) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(buildTemplate().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public String build(List<OmrAssessmentApplication> applications) {
        if (applications == null) {
            throw new IllegalArgumentException(
                    "A lista de aplicações é obrigatória."
            );
        }

        StringBuilder csv = new StringBuilder(2048);
        append(csv, (Object[]) HEADERS);
        Set<String> identities = new HashSet<>();

        for (OmrAssessmentApplication application : applications) {
            if (application == null) {
                throw new IllegalArgumentException(
                        "A lista de aplicações não pode conter valores nulos."
                );
            }
            if (!identities.add(application.getApplicationId())) {
                throw new IllegalArgumentException(
                        "A lista possui uma aplicação repetida."
                );
            }

            append(
                    csv,
                    displayCode(application.getApplicationId()),
                    application.getAssessmentId(),
                    application.getAssessmentVersion(),
                    application.getClassGroupId(),
                    application.getApplicationId()
            );
        }
        return csv.toString();
    }

    public String buildTemplate() {
        StringBuilder csv = new StringBuilder(512);
        append(csv, (Object[]) HEADERS);
        append(
                csv,
                "NAT-1-1A",
                "ID-DA-PROVA",
                1,
                "ID-DA-TURMA",
                ""
        );
        return csv.toString();
    }

    private static String displayCode(String applicationId) {
        return applicationId.startsWith(CSV_ID_PREFIX)
                ? applicationId.substring(CSV_ID_PREFIX.length())
                : applicationId;
    }

    private static void append(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                target.append(';');
            }
            String value = values[index] == null
                    ? "" : String.valueOf(values[index]);
            target.append('"')
                    .append(preventSpreadsheetFormula(value)
                            .replace("\"", "\"\""))
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
        if (first == '=' || first == '+' || first == '-'
                || first == '@') {
            return value.substring(0, index)
                    + "'" + value.substring(index);
        }
        return value;
    }

    private static void requireOutput(OutputStream output) {
        if (output == null) {
            throw new IllegalArgumentException(
                    "O destino do CSV é obrigatório."
            );
        }
    }
}
