package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.application.layout.OmrPublishedLayoutResolver;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Valida previamente um CSV para a geração de cartões PNG em ZIP.
 *
 * O planejamento impede arquivos duplicados, caminhos dentro do ZIP,
 * modelos não publicáveis e lotes excessivos antes de o usuário escolher o
 * destino. Célula de texto vazia usa o padrão; [vazio] já chega do parser
 * como uma substituição explicitamente vazia.
 */
public final class OmrAnswerKeyPngZipPlanner {

    public static final int MAX_CARD_COUNT = 200;

    private static final int MAX_OUTPUT_FILE_NAME_LENGTH = 164;
    private static final int MAX_RENDERED_TEXT_LENGTH = 80;

    private final OmrPublishedLayoutResolver layoutResolver;

    public OmrAnswerKeyPngZipPlanner() {
        this(new OmrPublishedLayoutResolver());
    }

    OmrAnswerKeyPngZipPlanner(
            OmrPublishedLayoutResolver layoutResolver
    ) {
        if (layoutResolver == null) {
            throw new IllegalArgumentException(
                    "O resolvedor de modelos é obrigatório."
            );
        }
        this.layoutResolver = layoutResolver;
    }

    public OmrAnswerKeyPngZipPlan plan(
            OmrAnswerKeyCsvParser.Preview preview
    ) {
        if (preview == null) {
            throw new IllegalArgumentException(
                    "A prévia do CSV é obrigatória."
            );
        }

        List<OmrAnswerKeyPngZipPlan.Card> cards = new ArrayList<>();
        List<OmrAnswerKeyPngZipPlan.Issue> issues = new ArrayList<>();

        for (OmrAnswerKeyCsvParser.Issue issue : preview.getIssues()) {
            issues.add(new OmrAnswerKeyPngZipPlan.Issue(
                    issue.getLineNumber(),
                    issue.getMessage()
            ));
        }

        if (!issues.isEmpty()) {
            return new OmrAnswerKeyPngZipPlan(cards, issues);
        }

        List<OmrAnswerKeyCsvParser.Row> rows = preview.getRows();
        if (rows.size() > MAX_CARD_COUNT) {
            int lineNumber = rows.get(MAX_CARD_COUNT).getLineNumber();
            issues.add(new OmrAnswerKeyPngZipPlan.Issue(
                    lineNumber,
                    "O lote de cartões excede o limite seguro de "
                            + MAX_CARD_COUNT
                            + " arquivos. Divida o CSV em mais de um lote."
            ));
            return new OmrAnswerKeyPngZipPlan(cards, issues);
        }

        Set<String> normalizedFileNames = new HashSet<>();

        for (OmrAnswerKeyCsvParser.Row row : rows) {
            try {
                OmrAnswerKeyDefinition answerKey =
                        row.getAnswerKeyDefinition();

                layoutResolver.resolveTemplateForAnswerKey(answerKey);

                validateTextOverrides(row);

                String outputFileName = outputFileName(row);
                String normalizedFileName = outputFileName
                        .toLowerCase(Locale.ROOT);

                if (!normalizedFileNames.add(normalizedFileName)) {
                    throw new IllegalArgumentException(
                            "O nome de arquivo "
                                    + outputFileName
                                    + " está repetido no lote."
                    );
                }

                cards.add(new OmrAnswerKeyPngZipPlan.Card(
                        row.getLineNumber(),
                        row.getCode(),
                        answerKey,
                        outputFileName,
                        row.getHeaderText(),
                        row.getInstructionsText(),
                        row.getFooterText()
                ));
            } catch (IllegalArgumentException exception) {
                issues.add(new OmrAnswerKeyPngZipPlan.Issue(
                        row.getLineNumber(),
                        safeMessage(exception)
                ));
            }
        }

        return new OmrAnswerKeyPngZipPlan(cards, issues);
    }

    private static void validateTextOverrides(
            OmrAnswerKeyCsvParser.Row row
    ) {
        validateTextOverride("cabeçalho", row.getHeaderText());
        validateTextOverride("instruções", row.getInstructionsText());
        validateTextOverride("rodapé", row.getFooterText());
    }

    private static void validateTextOverride(
            String label,
            String value
    ) {
        if (value != null && value.length() > MAX_RENDERED_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "O " + label + " aceita até "
                            + MAX_RENDERED_TEXT_LENGTH
                            + " caracteres para gerar o cartão."
            );
        }
    }

    private static String outputFileName(
            OmrAnswerKeyCsvParser.Row row
    ) {
        String requested = row.getFileName();
        String baseName;

        if (requested == null) {
            baseName = slug(row.getCode())
                    + "-v"
                    + row.getAnswerKeyDefinition().getVersion();
        } else {
            baseName = requested.trim();
            if (baseName.toLowerCase(Locale.ROOT).endsWith(".png")) {
                baseName = baseName.substring(
                        0,
                        baseName.length() - 4
                );
            }
        }

        validatePortableBaseName(baseName);

        String outputFileName = baseName + ".png";
        if (outputFileName.length() > MAX_OUTPUT_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "O nome final do PNG excede "
                            + MAX_OUTPUT_FILE_NAME_LENGTH
                            + " caracteres."
            );
        }
        return outputFileName;
    }

    private static void validatePortableBaseName(String baseName) {
        if (baseName.isEmpty()
                || ".".equals(baseName)
                || baseName.endsWith(".")
                || baseName.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")) {
            throw new IllegalArgumentException(
                    "O nome do arquivo PNG não é portátil."
            );
        }

        String upper = baseName.toUpperCase(Locale.ROOT);
        int extensionSeparator = upper.indexOf('.');
        String deviceName = extensionSeparator < 0
                ? upper
                : upper.substring(0, extensionSeparator);
        if (deviceName.matches(
                "CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"
        )) {
            throw new IllegalArgumentException(
                    "O nome do arquivo PNG é reservado pelo sistema."
            );
        }
    }

    private static String slug(String value) {
        String normalized = Normalizer.normalize(
                value == null ? "" : value,
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return normalized.isEmpty() ? "cartao-resposta" : normalized;
    }

    private static String safeMessage(
            IllegalArgumentException exception
    ) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Não foi possível preparar o cartão desta linha."
                : message.trim();
    }
}
