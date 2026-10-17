package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exporta provas no formato canonico aceito pelo importador. */
public final class OmrAssessmentCsvExporter {

    private static final byte[] UTF8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private static final String CSV_ID_PREFIX = "assessment-csv:";

    private static final String[] HEADERS = {
            "codigo_prova",
            "versao_prova",
            "titulo",
            "disciplina",
            "etapa",
            "periodo",
            "ano_letivo",
            "gabarito",
            "versao_gabarito",
            "id_interno_prova"
    };

    public void write(
            List<OmrAssessmentDefinition> assessments,
            OutputStream output
    ) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(build(assessments).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public void writeTemplate(OutputStream output) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(buildTemplate().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public String build(List<OmrAssessmentDefinition> assessments) {
        if (assessments == null) {
            throw new IllegalArgumentException(
                    "A lista de provas e obrigatoria."
            );
        }

        StringBuilder csv = new StringBuilder(2048);
        append(csv, (Object[]) HEADERS);
        Set<String> identities = new HashSet<>();

        for (OmrAssessmentDefinition assessment : assessments) {
            if (assessment == null) {
                throw new IllegalArgumentException(
                        "A lista de provas nao pode conter valores nulos."
                );
            }

            String identity = assessment.getAssessmentId()
                    + "\u0000" + assessment.getVersion();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "A lista possui uma versao de prova repetida."
                );
            }

            append(
                    csv,
                    displayCode(assessment.getAssessmentId()),
                    assessment.getVersion(),
                    assessment.getTitle(),
                    assessment.getSubject(),
                    assessment.getGradeLevel(),
                    assessment.getAcademicPeriod(),
                    assessment.getSchoolYear(),
                    assessment.getAnswerKeyId(),
                    assessment.getAnswerKeyVersion(),
                    assessment.getAssessmentId()
            );
        }

        return csv.toString();
    }

    public String buildTemplate() {
        StringBuilder csv = new StringBuilder(1024);
        append(csv, (Object[]) HEADERS);
        append(
                csv,
                "NAT-1",
                1,
                "Caderno de Ciencias da Natureza",
                "Natureza",
                "1a serie",
                "1o periodo",
                2026,
                "ID-DO-GABARITO",
                1,
                ""
        );
        return csv.toString();
    }

    private static String displayCode(String assessmentId) {
        return assessmentId.startsWith(CSV_ID_PREFIX)
                ? assessmentId.substring(CSV_ID_PREFIX.length())
                : assessmentId;
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
                    "O destino do CSV e obrigatorio."
            );
        }
    }
}
