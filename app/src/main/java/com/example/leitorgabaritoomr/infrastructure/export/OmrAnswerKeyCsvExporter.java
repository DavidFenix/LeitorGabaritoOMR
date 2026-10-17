package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyLayoutValidator;
import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyCsvParser;
import com.example.leitorgabaritoomr.application.layout.OmrPublishedLayoutResolver;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exporta gabaritos para o formato CSV canonico aceito pelo importador.
 *
 * O arquivo usa UTF-8 com BOM, separador de ponto e virgula e CRLF para abrir
 * corretamente no Excel em pt-BR. Identidade e layout tecnicos sao sempre
 * incluidos, tornando a exportacao adequada tambem para backup e restauracao.
 */
public final class OmrAnswerKeyCsvExporter {

    private static final byte[] UTF8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private static final String[] HEADERS = {
            "codigo_gabarito",
            "versao",
            "nome",
            "questoes",
            "alternativas",
            "itens_por_coluna",
            "respostas",
            "pesos",
            "cabecalho",
            "instrucoes",
            "rodape",
            "nome_arquivo",
            "id_gabarito",
            "layout_id",
            "layout_versao"
    };

    private final OmrPublishedLayoutResolver layoutResolver;
    private final OmrAnswerKeyLayoutValidator layoutValidator;

    public OmrAnswerKeyCsvExporter() {
        layoutResolver = new OmrPublishedLayoutResolver();
        layoutValidator = new OmrAnswerKeyLayoutValidator();
    }

    public void write(
            List<OmrAnswerKeyDefinition> answerKeys,
            OutputStream output
    ) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(build(answerKeys).getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public void writeTemplate(OutputStream output) throws IOException {
        requireOutput(output);
        output.write(UTF8_BOM);
        output.write(buildTemplate().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public String build(List<OmrAnswerKeyDefinition> answerKeys) {
        if (answerKeys == null) {
            throw new IllegalArgumentException(
                    "A lista de gabaritos e obrigatoria."
            );
        }

        StringBuilder csv = new StringBuilder(4096);
        append(csv, (Object[]) HEADERS);
        Set<String> identities = new HashSet<>(answerKeys.size());

        for (OmrAnswerKeyDefinition answerKey : answerKeys) {
            if (answerKey == null) {
                throw new IllegalArgumentException(
                        "A lista de gabaritos nao pode conter valores nulos."
                );
            }
            String identity = answerKey.getId()
                    + "\u0000" + answerKey.getVersion();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "A lista possui uma identidade de gabarito repetida."
                );
            }

            OmrLayoutDefinition layout = layoutResolver.resolveForAnswerKey(
                    answerKey
            );
            layoutValidator.validateOrThrow(layout, answerKey);
            String code = displayCode(answerKey.getId());

            append(
                    csv,
                    code,
                    answerKey.getVersion(),
                    answerKey.getName(),
                    answerKey.getQuestionCount(),
                    optionCount(layout),
                    questionsPerColumn(layout),
                    answers(answerKey, layout),
                    weights(answerKey),
                    "",
                    "",
                    "",
                    suggestedFileName(code, answerKey.getVersion()),
                    answerKey.getId(),
                    answerKey.getLayoutId(),
                    answerKey.getLayoutVersion()
            );
        }
        return csv.toString();
    }

    /**
     * Modelo baixavel com duas linhas validas: uma simples e outra com
     * resposta multipla, pesos diferentes e cinco alternativas.
     */
    public String buildTemplate() {
        StringBuilder csv = new StringBuilder(2048);
        append(csv, (Object[]) HEADERS);
        append(
                csv,
                "MAT-A",
                1,
                "Matematica - Modelo A",
                10,
                4,
                "automatico",
                "A|B|C|D|A|B|C|D|A|B",
                1,
                "MATEMATICA - MODELO A",
                "Marque apenas uma alternativa por questao.",
                "Turma: __________",
                "mat-a-v1",
                "",
                "",
                ""
        );
        append(
                csv,
                "NAT-B",
                1,
                "Natureza - Modelo B",
                10,
                5,
                5,
                "A+C|B|C|D|E|A|B|C|D|E",
                "1|1|2|1|1|1|1|2|1|1",
                "NATUREZA - MODELO B",
                "Preencha completamente a bolha escolhida.",
                OmrAnswerKeyCsvParser.TEXT_OMISSION_MARKER,
                "nat-b-v1",
                "",
                "",
                ""
        );
        return csv.toString();
    }

    private static String answers(
            OmrAnswerKeyDefinition answerKey,
            OmrLayoutDefinition layout
    ) {
        StringBuilder result = new StringBuilder();
        List<OmrQuestionDefinition> questions = layout.getAllQuestions();
        for (int questionIndex = 0;
             questionIndex < questions.size();
             questionIndex++) {
            if (questionIndex > 0) {
                result.append('|');
            }
            OmrQuestionDefinition question = questions.get(questionIndex);
            OmrAnswerKeyEntry entry = answerKey.findEntryByQuestionId(
                    question.getId()
            );
            if (entry == null) {
                throw new IllegalArgumentException(
                        "O gabarito nao possui a questao "
                                + question.getId() + "."
                );
            }

            int acceptedCount = 0;
            for (OmrOptionDefinition option : question.getOptions()) {
                if (!entry.acceptsOption(option.getId())) {
                    continue;
                }
                if (acceptedCount > 0) {
                    result.append('+');
                }
                result.append(option.getLabel());
                acceptedCount++;
            }
            if (acceptedCount != entry.getAcceptedOptionCount()) {
                throw new IllegalArgumentException(
                        "O gabarito possui alternativa que nao pertence a questao "
                                + question.getId() + "."
                );
            }
        }
        return result.toString();
    }

    private static String weights(OmrAnswerKeyDefinition answerKey) {
        List<OmrAnswerKeyEntry> entries = answerKey.getEntries();
        double first = entries.get(0).getWeight();
        boolean uniform = true;
        for (OmrAnswerKeyEntry entry : entries) {
            if (Double.compare(first, entry.getWeight()) != 0) {
                uniform = false;
                break;
            }
        }
        if (uniform) {
            return decimal(first);
        }

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                result.append('|');
            }
            result.append(decimal(entries.get(index).getWeight()));
        }
        return result.toString();
    }

    private static int optionCount(OmrLayoutDefinition layout) {
        List<OmrQuestionDefinition> questions = layout.getAllQuestions();
        int count = questions.get(0).getOptionCount();
        for (OmrQuestionDefinition question : questions) {
            if (question.getOptionCount() != count) {
                throw new IllegalArgumentException(
                        "O layout possui quantidades diferentes de alternativas."
                );
            }
        }
        return count;
    }

    private static int questionsPerColumn(OmrLayoutDefinition layout) {
        int maximum = 0;
        for (OmrBlockDefinition block : layout.getBlocks()) {
            maximum = Math.max(maximum, block.getQuestionCount());
        }
        return maximum;
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String displayCode(String answerKeyId) {
        String prefix = "gabarito-csv:";
        return answerKeyId.startsWith(prefix)
                ? answerKeyId.substring(prefix.length())
                : answerKeyId;
    }

    private static String suggestedFileName(String code, int version) {
        String normalized = Normalizer.normalize(
                code,
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            normalized = "gabarito";
        }
        return normalized + "-v" + version;
    }

    private static void append(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                target.append(';');
            }
            String value = values[index] == null
                    ? "" : String.valueOf(values[index]);
            value = preventSpreadsheetFormula(value);
            target.append('"')
                    .append(value.replace("\"", "\"\""))
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
