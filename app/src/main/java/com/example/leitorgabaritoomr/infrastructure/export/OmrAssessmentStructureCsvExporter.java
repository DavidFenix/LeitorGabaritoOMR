package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exporta blocos no formato canonico aceito pelo importador. */
public final class OmrAssessmentStructureCsvExporter {

    private static final byte[] UTF8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private static final String BLOCK_ID_PREFIX = "bloco:";

    private static final String[] HEADERS = {
            "codigo_prova",
            "versao_prova",
            "ordem_bloco",
            "codigo_bloco",
            "area",
            "codigo_componente",
            "componente",
            "itens"
    };

    public void write(
            List<OmrAssessmentStructure> structures,
            OutputStream output
    ) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(build(structures).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public void writeTemplate(OutputStream output) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(buildTemplate().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public String build(List<OmrAssessmentStructure> structures) {
        if (structures == null) {
            throw new IllegalArgumentException(
                    "A lista de estruturas e obrigatoria."
            );
        }

        StringBuilder csv = new StringBuilder(2048);
        append(csv, (Object[]) HEADERS);
        Set<String> identities = new HashSet<>();

        for (OmrAssessmentStructure structure : structures) {
            if (structure == null) {
                throw new IllegalArgumentException(
                        "A lista de estruturas nao pode conter nulos."
                );
            }
            String identity = structure.getAssessmentId()
                    + "\u0000" + structure.getAssessmentVersion();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "A lista possui uma estrutura repetida."
                );
            }

            for (OmrAssessmentBlock block : structure.getBlocks()) {
                append(
                        csv,
                        structure.getAssessmentId(),
                        structure.getAssessmentVersion(),
                        block.getOrder(),
                        displayBlockCode(block.getBlockId()),
                        block.getAreaName(),
                        block.getComponentCode(),
                        block.getComponentName(),
                        block.getQuestionCount()
                );
            }
        }

        return csv.toString();
    }

    public String buildTemplate() {
        StringBuilder csv = new StringBuilder(1024);
        append(csv, (Object[]) HEADERS);
        append(csv, "NAT-1", 1, 1, "QUI",
                "Ciencias da Natureza", "QUI", "Quimica", 15);
        append(csv, "NAT-1", 1, 2, "FIS",
                "Ciencias da Natureza", "FIS", "Fisica", 15);
        append(csv, "NAT-1", 1, 3, "BIO",
                "Ciencias da Natureza", "BIO", "Biologia", 15);
        return csv.toString();
    }

    private static String displayBlockCode(String blockId) {
        return blockId.startsWith(BLOCK_ID_PREFIX)
                ? blockId.substring(BLOCK_ID_PREFIX.length())
                : blockId;
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
