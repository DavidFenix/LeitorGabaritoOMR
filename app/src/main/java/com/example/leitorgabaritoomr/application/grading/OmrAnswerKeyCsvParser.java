package com.example.leitorgabaritoomr.application.grading;

import com.example.leitorgabaritoomr.application.layout.OmrPublishedLayoutResolver;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;
import com.example.leitorgabaritoomr.vision.layout.factory.OmrDynamicLayoutFactory;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateCatalog;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Le e valida o CSV canonico de gabaritos sem alterar o repositorio.
 *
 * Cada linha representa uma versao completa de gabarito. As respostas usam
 * {@code |} entre questoes e {@code +} entre alternativas simultaneamente
 * aceitas. Os campos tecnicos de identidade e layout sao opcionais para uma
 * criacao nova e sao preenchidos pelo exportador para permitir restauracao
 * fiel de gabaritos existentes.
 */
public final class OmrAnswerKeyCsvParser {

    public static final int MAX_CSV_BYTE_COUNT = 5 * 1024 * 1024;
    public static final int MAX_ROW_COUNT = 1_000;

    private static final int MAX_CODE_LENGTH = 80;
    private static final int MAX_ID_LENGTH = 240;
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_HEADER_LENGTH = 300;
    private static final int MAX_INSTRUCTIONS_LENGTH = 1_500;
    private static final int MAX_FOOTER_LENGTH = 500;
    private static final int MAX_FILE_NAME_LENGTH = 160;

    private static final Charset WINDOWS_1252 =
            Charset.forName("windows-1252");

    private final OmrPublishedLayoutResolver layoutResolver;
    private final OmrAnswerKeyDefinitionFactory answerKeyFactory;

    public OmrAnswerKeyCsvParser() {
        this(
                new OmrPublishedLayoutResolver(),
                new OmrAnswerKeyDefinitionFactory()
        );
    }

    OmrAnswerKeyCsvParser(
            OmrPublishedLayoutResolver layoutResolver,
            OmrAnswerKeyDefinitionFactory answerKeyFactory
    ) {
        if (layoutResolver == null || answerKeyFactory == null) {
            throw new IllegalArgumentException(
                    "O resolvedor e a fabrica de gabaritos sao obrigatorios."
            );
        }
        this.layoutResolver = layoutResolver;
        this.answerKeyFactory = answerKeyFactory;
    }

    public Preview parse(String csvText) {
        if (csvText != null
                && csvText.getBytes(StandardCharsets.UTF_8).length
                > MAX_CSV_BYTE_COUNT) {
            return oversizedPreview("Texto");
        }
        return parseDecoded(csvText, "Texto");
    }

    public Preview parse(byte[] csvBytes) {
        if (csvBytes == null || csvBytes.length == 0) {
            return emptyPreview("Arquivo");
        }
        if (csvBytes.length > MAX_CSV_BYTE_COUNT) {
            return oversizedPreview("Arquivo");
        }
        DecodedText decoded = decode(csvBytes);
        return parseDecoded(decoded.text, decoded.encoding);
    }

    /** Cria uma identidade curta e deterministica para uma linha nova. */
    public static String answerKeyIdForCsvCode(String code) {
        String normalized = normalizeInline(code);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "O codigo do gabarito e obrigatorio."
            );
        }
        String slug = Normalizer.normalize(
                normalized,
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            throw new IllegalArgumentException(
                    "O codigo do gabarito precisa conter letras ou numeros."
            );
        }
        return "gabarito-csv:" + slug;
    }

    private Preview parseDecoded(String csvText, String encoding) {
        if (csvText == null || csvText.trim().isEmpty()) {
            return emptyPreview(encoding);
        }

        String normalized = removeBom(csvText);
        char delimiter = detectDelimiter(normalized);
        ReadResult read = readRecords(normalized, delimiter);
        List<Issue> issues = new ArrayList<>(read.issues);

        if (read.records.isEmpty()) {
            if (issues.isEmpty()) {
                issues.add(new Issue(1, "O CSV nao possui cabecalho."));
            }
            return preview(delimiter, encoding, Collections.emptyList(), issues);
        }

        RawRecord header = read.records.get(0);
        HeaderIndexes indexes = resolveHeader(header, issues);
        if (!indexes.isComplete()) {
            sortIssues(issues);
            return preview(delimiter, encoding, Collections.emptyList(), issues);
        }

        int dataRowCount = read.records.size() - 1;
        if (dataRowCount == 0) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O CSV nao possui linhas de gabaritos."
            ));
        }
        if (dataRowCount > MAX_ROW_COUNT) {
            issues.add(new Issue(
                    read.records.get(MAX_ROW_COUNT + 1).lineNumber,
                    "O CSV excede o limite de " + MAX_ROW_COUNT
                            + " gabaritos."
            ));
        }

        List<Row> rows = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        int lastRecord = Math.min(read.records.size(), MAX_ROW_COUNT + 1);

        for (int index = 1; index < lastRecord; index++) {
            RawRecord record = read.records.get(index);
            int issueCountBefore = issues.size();

            String code = checkedRequiredInline(
                    record, indexes.code, "O codigo do gabarito esta vazio.",
                    MAX_CODE_LENGTH, "O codigo do gabarito", issues
            );
            Integer version = positiveInteger(fieldAt(
                    record.fields, indexes.version
            ));
            if (version == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A versao deve ser um inteiro positivo."
                ));
            }

            String name = checkedRequiredInline(
                    record, indexes.name, "O nome do gabarito esta vazio.",
                    MAX_NAME_LENGTH, "O nome do gabarito", issues
            );
            Integer questionCount = boundedPositiveInteger(
                    fieldAt(record.fields, indexes.questionCount),
                    OmrSheetTemplateCatalog.MIN_QUESTION_COUNT,
                    OmrSheetTemplateCatalog.MAX_QUESTION_COUNT
            );
            if (questionCount == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A quantidade de questoes deve estar entre 1 e 90."
                ));
            }

            Integer optionCount = boundedPositiveInteger(
                    fieldAt(record.fields, indexes.optionCount), 4, 5
            );
            if (optionCount == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A quantidade de alternativas deve ser 4 ou 5."
                ));
            }

            Integer questionsPerColumn = parseQuestionsPerColumn(
                    fieldAt(record.fields, indexes.questionsPerColumn),
                    record.lineNumber,
                    issues
            );

            String exactId = checkedOptionalInline(
                    record, indexes.answerKeyId,
                    MAX_ID_LENGTH, "O ID tecnico do gabarito", issues
            );
            String layoutId = checkedOptionalInline(
                    record, indexes.layoutId,
                    MAX_ID_LENGTH, "O ID do layout", issues
            );
            Integer layoutVersion = optionalPositiveInteger(
                    fieldAt(record.fields, indexes.layoutVersion)
            );
            boolean layoutVersionInformed = !normalizeInline(
                    fieldAt(record.fields, indexes.layoutVersion)
            ).isEmpty();
            if (layoutVersionInformed && layoutVersion == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A versao do layout deve ser um inteiro positivo."
                ));
            }
            if ((layoutId == null) != (layoutVersion == null)) {
                issues.add(new Issue(
                        record.lineNumber,
                        "layout_id e layout_versao devem ser informados juntos."
                ));
            }

            String headerText = checkedOptionalMultiline(
                    record, indexes.headerText, MAX_HEADER_LENGTH,
                    "O cabecalho", issues
            );
            String instructionsText = checkedOptionalMultiline(
                    record, indexes.instructionsText,
                    MAX_INSTRUCTIONS_LENGTH,
                    "As instrucoes", issues
            );
            String footerText = checkedOptionalMultiline(
                    record, indexes.footerText, MAX_FOOTER_LENGTH,
                    "O rodape", issues
            );
            String fileName = checkedOptionalInline(
                    record, indexes.fileName, MAX_FILE_NAME_LENGTH,
                    "O nome do arquivo", issues
            );
            if (fileName != null && !isSafeFileName(fileName)) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O nome do arquivo possui caracteres nao permitidos."
                ));
            }

            List<Collection<String>> acceptedLabels = null;
            List<Double> weights = null;
            if (questionCount != null && optionCount != null) {
                acceptedLabels = parseAnswers(
                        fieldAt(record.fields, indexes.answers),
                        questionCount,
                        optionCount,
                        record.lineNumber,
                        issues
                );
                weights = parseWeights(
                        fieldAt(record.fields, indexes.weights),
                        questionCount,
                        record.lineNumber,
                        issues
                );
            }

            if (issues.size() != issueCountBefore) {
                continue;
            }

            try {
                String answerKeyId = exactId == null
                        ? answerKeyIdForCsvCode(code)
                        : exactId;
                OmrLayoutDefinition layout = resolveLayout(
                        layoutId,
                        layoutVersion,
                        questionCount,
                        optionCount,
                        questionsPerColumn
                );
                int resolvedQuestionsPerColumn =
                        questionsPerColumn(layout);

                OmrAnswerKeyDefinition definition = answerKeyFactory.create(
                        answerKeyId,
                        version,
                        name,
                        layout,
                        acceptedLabels,
                        weights
                );
                String identity = identityOf(definition);
                if (!identities.add(identity)) {
                    issues.add(new Issue(
                            record.lineNumber,
                            "A identidade " + answerKeyId + "@v" + version
                                    + " esta repetida no CSV."
                    ));
                    continue;
                }

                rows.add(new Row(
                        record.lineNumber,
                        code,
                        definition,
                        optionCount,
                        resolvedQuestionsPerColumn,
                        headerText,
                        instructionsText,
                        footerText,
                        fileName
                ));
            } catch (IllegalArgumentException exception) {
                issues.add(new Issue(
                        record.lineNumber,
                        safeMessage(exception)
                ));
            }
        }

        sortIssues(issues);
        return preview(delimiter, encoding, rows, issues);
    }

    private OmrLayoutDefinition resolveLayout(
            String layoutId,
            Integer layoutVersion,
            int questionCount,
            int optionCount,
            Integer questionsPerColumn
    ) {
        if (layoutId != null) {
            OmrLayoutDefinition layout = layoutResolver.resolve(
                    layoutId,
                    layoutVersion,
                    questionCount
            );
            int resolvedOptionCount = optionCount(layout);
            if (resolvedOptionCount != optionCount) {
                throw new IllegalArgumentException(
                        "O layout informado possui " + resolvedOptionCount
                                + " alternativas, e nao " + optionCount + "."
                );
            }
            if (questionsPerColumn != null
                    && questionsPerColumn(layout) != questionsPerColumn) {
                throw new IllegalArgumentException(
                        "O layout informado nao corresponde aos itens por"
                                + " coluna declarados."
                );
            }
            return layout;
        }

        OmrSheetTemplateSpec spec = questionsPerColumn == null
                ? OmrSheetTemplateCatalog.flexibleOptionsV4(
                questionCount, optionCount
        )
                : OmrSheetTemplateCatalog.flexibleOptionsV4(
                questionCount, optionCount, questionsPerColumn
        );
        return OmrDynamicLayoutFactory.create(spec);
    }

    private static int optionCount(OmrLayoutDefinition layout) {
        List<OmrQuestionDefinition> questions = layout.getAllQuestions();
        int optionCount = questions.get(0).getOptionCount();
        for (OmrQuestionDefinition question : questions) {
            if (question.getOptionCount() != optionCount) {
                throw new IllegalArgumentException(
                        "O layout possui quantidades diferentes de alternativas."
                );
            }
        }
        return optionCount;
    }

    private static int questionsPerColumn(OmrLayoutDefinition layout) {
        int maximum = 0;
        for (OmrBlockDefinition block : layout.getBlocks()) {
            maximum = Math.max(maximum, block.getQuestionCount());
        }
        return maximum;
    }

    private static List<Collection<String>> parseAnswers(
            String value,
            int questionCount,
            int optionCount,
            int line,
            List<Issue> issues
    ) {
        String normalized = normalizeInline(unprotectSpreadsheetValue(value));
        if (normalized.isEmpty()) {
            issues.add(new Issue(line, "A sequencia de respostas esta vazia."));
            return null;
        }
        String[] tokens = normalized.split("\\|", -1);
        if (tokens.length != questionCount) {
            issues.add(new Issue(
                    line,
                    "A sequencia deve possuir " + questionCount
                            + " respostas, mas possui " + tokens.length + "."
            ));
            return null;
        }

        List<Collection<String>> result = new ArrayList<>(questionCount);
        for (int index = 0; index < tokens.length; index++) {
            String[] alternatives = tokens[index].split("\\+", -1);
            Set<String> accepted = new LinkedHashSet<>();
            for (String alternative : alternatives) {
                String label = normalizeInline(alternative)
                        .toUpperCase(Locale.ROOT);
                if (label.length() != 1
                        || label.charAt(0) < 'A'
                        || label.charAt(0) >= 'A' + optionCount) {
                    issues.add(new Issue(
                            line,
                            "Resposta invalida na questao " + (index + 1)
                                    + ": " + alternative.trim() + "."
                    ));
                    return null;
                }
                if (!accepted.add(label)) {
                    issues.add(new Issue(
                            line,
                            "Alternativa repetida na questao " + (index + 1)
                                    + ": " + label + "."
                    ));
                    return null;
                }
            }
            result.add(accepted);
        }
        return result;
    }

    private static List<Double> parseWeights(
            String value,
            int questionCount,
            int line,
            List<Issue> issues
    ) {
        String normalized = normalizeInline(unprotectSpreadsheetValue(value));
        if (normalized.isEmpty()) {
            return repeatedWeight(questionCount, 1.0);
        }
        String[] tokens = normalized.split("\\|", -1);
        if (tokens.length != 1 && tokens.length != questionCount) {
            issues.add(new Issue(
                    line,
                    "Informe um peso unico ou exatamente " + questionCount
                            + " pesos separados por |."
            ));
            return null;
        }
        List<Double> parsed = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            Double weight = positiveDecimal(token);
            if (weight == null) {
                issues.add(new Issue(
                        line,
                        "Todos os pesos devem ser numeros finitos maiores que zero."
                ));
                return null;
            }
            parsed.add(weight);
        }
        if (parsed.size() == 1) {
            return repeatedWeight(questionCount, parsed.get(0));
        }
        return parsed;
    }

    private static List<Double> repeatedWeight(int count, double weight) {
        List<Double> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(weight);
        }
        return result;
    }

    private static HeaderIndexes resolveHeader(
            RawRecord header,
            List<Issue> issues
    ) {
        Map<String, List<Integer>> positions = new HashMap<>();
        for (int index = 0; index < header.fields.size(); index++) {
            String canonical = canonicalHeader(header.fields.get(index));
            positions.computeIfAbsent(
                    canonical, ignored -> new ArrayList<>()
            ).add(index);
        }

        return new HeaderIndexes(
                requiredHeader(positions, header.lineNumber, issues,
                        "codigo_gabarito", "codigo_gabarito", "codigo"),
                requiredHeader(positions, header.lineNumber, issues,
                        "versao", "versao", "versao_gabarito"),
                requiredHeader(positions, header.lineNumber, issues,
                        "nome", "nome", "nome_gabarito"),
                requiredHeader(positions, header.lineNumber, issues,
                        "questoes", "questoes", "quantidade_questoes"),
                requiredHeader(positions, header.lineNumber, issues,
                        "alternativas", "alternativas",
                        "quantidade_alternativas"),
                requiredHeader(positions, header.lineNumber, issues,
                        "itens_por_coluna", "itens_por_coluna",
                        "questoes_por_coluna"),
                requiredHeader(positions, header.lineNumber, issues,
                        "respostas", "respostas"),
                optionalHeader(positions, header.lineNumber, issues,
                        "pesos", "pesos", "peso"),
                optionalHeader(positions, header.lineNumber, issues,
                        "cabecalho", "cabecalho"),
                optionalHeader(positions, header.lineNumber, issues,
                        "instrucoes", "instrucoes"),
                optionalHeader(positions, header.lineNumber, issues,
                        "rodape", "rodape"),
                optionalHeader(positions, header.lineNumber, issues,
                        "nome_arquivo", "nome_arquivo", "arquivo"),
                optionalHeader(positions, header.lineNumber, issues,
                        "id_gabarito", "id_gabarito"),
                optionalHeader(positions, header.lineNumber, issues,
                        "layout_id", "layout_id"),
                optionalHeader(positions, header.lineNumber, issues,
                        "layout_versao", "layout_versao", "versao_layout")
        );
    }

    private static int requiredHeader(
            Map<String, List<Integer>> positions,
            int line,
            List<Issue> issues,
            String displayName,
            String... aliases
    ) {
        int index = optionalHeader(
                positions, line, issues, displayName, aliases
        );
        if (index < 0) {
            issues.add(new Issue(
                    line,
                    "Coluna obrigatoria ausente: " + displayName + "."
            ));
        }
        return index;
    }

    private static int optionalHeader(
            Map<String, List<Integer>> positions,
            int line,
            List<Issue> issues,
            String displayName,
            String... aliases
    ) {
        Integer found = null;
        for (String alias : aliases) {
            List<Integer> matches = positions.get(alias);
            if (matches == null) {
                continue;
            }
            for (Integer match : matches) {
                if (found != null) {
                    issues.add(new Issue(
                            line,
                            "A coluna " + displayName
                                    + " foi informada mais de uma vez."
                    ));
                    return found;
                }
                found = match;
            }
        }
        return found == null ? -1 : found;
    }

    private static ReadResult readRecords(String text, char delimiter) {
        List<RawRecord> records = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        int line = 1;
        int recordLine = 1;

        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < text.length()
                            && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(value);
                    if (value == '\n') {
                        line++;
                    }
                }
                continue;
            }

            if (value == '"'
                    && field.toString().trim().isEmpty()
                    && !quoteClosed) {
                field.setLength(0);
                quoted = true;
            } else if (value == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (value == '\r' || value == '\n') {
                fields.add(field.toString());
                addIfNotBlank(records, fields, recordLine);
                fields = new ArrayList<>();
                field.setLength(0);
                quoteClosed = false;
                if (value == '\r' && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') {
                    index++;
                }
                line++;
                recordLine = line;
            } else if (quoteClosed && !Character.isWhitespace(value)) {
                issues.add(new Issue(
                        line,
                        "Ha texto inesperado depois do fechamento de aspas."
                ));
                field.append(value);
            } else if (!quoteClosed) {
                field.append(value);
            }
        }

        if (quoted) {
            issues.add(new Issue(
                    recordLine,
                    "Campo entre aspas nao foi encerrado."
            ));
        } else if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            addIfNotBlank(records, fields, recordLine);
        }
        return new ReadResult(records, issues);
    }

    private static void addIfNotBlank(
            List<RawRecord> records,
            List<String> fields,
            int line
    ) {
        for (String field : fields) {
            if (field != null && !field.trim().isEmpty()) {
                records.add(new RawRecord(line, fields));
                return;
            }
        }
    }

    private static char detectDelimiter(String text) {
        int semicolon = 0;
        int comma = 0;
        int tab = 0;
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '"') {
                if (quoted && index + 1 < text.length()
                        && text.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (value == '\r' || value == '\n')) {
                break;
            } else if (!quoted && value == ';') {
                semicolon++;
            } else if (!quoted && value == ',') {
                comma++;
            } else if (!quoted && value == '\t') {
                tab++;
            }
        }
        if (tab > semicolon && tab > comma) {
            return '\t';
        }
        return comma > semicolon ? ',' : ';';
    }

    private static Integer parseQuestionsPerColumn(
            String value,
            int line,
            List<Issue> issues
    ) {
        String normalized = canonical(unprotectSpreadsheetValue(value));
        if (normalized.isEmpty()
                || "automatico".equals(normalized)
                || "auto".equals(normalized)) {
            return null;
        }
        Integer parsed = boundedPositiveInteger(normalized, 1, 30);
        if (parsed == null) {
            issues.add(new Issue(
                    line,
                    "Itens por coluna deve ser automatico ou um inteiro de 1 a 30."
            ));
        }
        return parsed;
    }

    private static String checkedRequiredInline(
            RawRecord record,
            int index,
            String emptyMessage,
            int maximumLength,
            String label,
            List<Issue> issues
    ) {
        String value = normalizeInline(unprotectSpreadsheetValue(
                fieldAt(record.fields, index)
        ));
        if (value.isEmpty()) {
            issues.add(new Issue(record.lineNumber, emptyMessage));
        } else if (value.length() > maximumLength) {
            issues.add(new Issue(
                    record.lineNumber,
                    label + " excede " + maximumLength + " caracteres."
            ));
        }
        return value;
    }

    private static String checkedOptionalInline(
            RawRecord record,
            int index,
            int maximumLength,
            String label,
            List<Issue> issues
    ) {
        if (index < 0) {
            return null;
        }
        String value = normalizeInline(unprotectSpreadsheetValue(
                fieldAt(record.fields, index)
        ));
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maximumLength) {
            issues.add(new Issue(
                    record.lineNumber,
                    label + " excede " + maximumLength + " caracteres."
            ));
        }
        return value;
    }

    private static String checkedOptionalMultiline(
            RawRecord record,
            int index,
            int maximumLength,
            String label,
            List<Issue> issues
    ) {
        if (index < 0) {
            return null;
        }
        String value = normalizeMultiline(unprotectSpreadsheetValue(
                fieldAt(record.fields, index)
        ));
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maximumLength) {
            issues.add(new Issue(
                    record.lineNumber,
                    label + " excede " + maximumLength + " caracteres."
            ));
        }
        return value;
    }

    private static boolean isSafeFileName(String fileName) {
        return !fileName.contains("..")
                && !fileName.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*");
    }

    private static String safeMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "O gabarito desta linha e invalido."
                : message.trim();
    }

    private static String identityOf(OmrAnswerKeyDefinition definition) {
        return definition.getId() + "\u0000" + definition.getVersion();
    }

    private static Double positiveDecimal(String value) {
        try {
            double parsed = Double.parseDouble(
                    normalizeInline(value).replace(',', '.')
            );
            return Double.isFinite(parsed) && parsed > 0.0
                    ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Integer positiveInteger(String value) {
        return boundedPositiveInteger(value, 1, Integer.MAX_VALUE);
    }

    private static Integer optionalPositiveInteger(String value) {
        String normalized = normalizeInline(unprotectSpreadsheetValue(value));
        return normalized.isEmpty() ? null : positiveInteger(normalized);
    }

    private static Integer boundedPositiveInteger(
            String value,
            int minimum,
            int maximum
    ) {
        try {
            int parsed = Integer.parseInt(normalizeInline(
                    unprotectSpreadsheetValue(value)
            ));
            return parsed >= minimum && parsed <= maximum
                    ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String normalizeInline(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeMultiline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private static String unprotectSpreadsheetValue(String value) {
        if (value == null) {
            return "";
        }
        int index = 0;
        while (index < value.length()
                && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index + 1 < value.length()
                && value.charAt(index) == '\''
                && isFormulaPrefix(value.charAt(index + 1))) {
            return value.substring(0, index)
                    + value.substring(index + 1);
        }
        return value;
    }

    private static boolean isFormulaPrefix(char value) {
        return value == '=' || value == '+' || value == '-'
                || value == '@';
    }

    private static String canonicalHeader(String value) {
        return canonical(value)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String canonical(String value) {
        String normalized = value == null ? "" : value.trim();
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static String removeBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\ufeff'
                ? text.substring(1) : text;
    }

    private static String fieldAt(List<String> fields, int index) {
        return index < 0 || index >= fields.size() ? "" : fields.get(index);
    }

    private static DecodedText decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new DecodedText(text, "UTF-8");
        } catch (CharacterCodingException ignored) {
            return new DecodedText(
                    new String(bytes, WINDOWS_1252),
                    "Windows-1252"
            );
        }
    }

    private static Preview emptyPreview(String encoding) {
        return preview(
                ';', encoding, Collections.emptyList(),
                Collections.singletonList(new Issue(
                        1, "O CSV de gabaritos esta vazio."
                ))
        );
    }

    private static Preview oversizedPreview(String encoding) {
        return preview(
                ';', encoding, Collections.emptyList(),
                Collections.singletonList(new Issue(
                        1, "O CSV excede o limite de 5 MB."
                ))
        );
    }

    private static Preview preview(
            char delimiter,
            String encoding,
            List<Row> rows,
            List<Issue> issues
    ) {
        return new Preview(delimiter, encoding, rows, issues);
    }

    private static void sortIssues(List<Issue> issues) {
        issues.sort(Comparator
                .comparingInt(Issue::getLineNumber)
                .thenComparing(Issue::getMessage));
    }

    public static final class Preview {
        private final char delimiter;
        private final String encoding;
        private final List<Row> rows;
        private final List<Issue> issues;

        private Preview(
                char delimiter,
                String encoding,
                List<Row> rows,
                List<Issue> issues
        ) {
            this.delimiter = delimiter;
            this.encoding = encoding;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }

        public char getDelimiter() {
            return delimiter;
        }

        public String getEncoding() {
            return encoding;
        }

        public List<Row> getRows() {
            return rows;
        }

        public List<Issue> getIssues() {
            return issues;
        }

        public boolean isReadyToImport() {
            return !rows.isEmpty() && issues.isEmpty();
        }
    }

    public static final class Row {
        private final int lineNumber;
        private final String code;
        private final OmrAnswerKeyDefinition answerKeyDefinition;
        private final int optionCount;
        private final int questionsPerColumn;
        private final String headerText;
        private final String instructionsText;
        private final String footerText;
        private final String fileName;

        private Row(
                int lineNumber,
                String code,
                OmrAnswerKeyDefinition answerKeyDefinition,
                int optionCount,
                int questionsPerColumn,
                String headerText,
                String instructionsText,
                String footerText,
                String fileName
        ) {
            this.lineNumber = lineNumber;
            this.code = code;
            this.answerKeyDefinition = answerKeyDefinition;
            this.optionCount = optionCount;
            this.questionsPerColumn = questionsPerColumn;
            this.headerText = headerText;
            this.instructionsText = instructionsText;
            this.footerText = footerText;
            this.fileName = fileName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getCode() {
            return code;
        }

        public OmrAnswerKeyDefinition getAnswerKeyDefinition() {
            return answerKeyDefinition;
        }

        public int getOptionCount() {
            return optionCount;
        }

        public int getQuestionsPerColumn() {
            return questionsPerColumn;
        }

        public String getHeaderText() {
            return headerText;
        }

        public String getInstructionsText() {
            return instructionsText;
        }

        public String getFooterText() {
            return footerText;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public static final class Issue {
        private final int lineNumber;
        private final String message;

        private Issue(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final class HeaderIndexes {
        private final int code;
        private final int version;
        private final int name;
        private final int questionCount;
        private final int optionCount;
        private final int questionsPerColumn;
        private final int answers;
        private final int weights;
        private final int headerText;
        private final int instructionsText;
        private final int footerText;
        private final int fileName;
        private final int answerKeyId;
        private final int layoutId;
        private final int layoutVersion;

        private HeaderIndexes(
                int code,
                int version,
                int name,
                int questionCount,
                int optionCount,
                int questionsPerColumn,
                int answers,
                int weights,
                int headerText,
                int instructionsText,
                int footerText,
                int fileName,
                int answerKeyId,
                int layoutId,
                int layoutVersion
        ) {
            this.code = code;
            this.version = version;
            this.name = name;
            this.questionCount = questionCount;
            this.optionCount = optionCount;
            this.questionsPerColumn = questionsPerColumn;
            this.answers = answers;
            this.weights = weights;
            this.headerText = headerText;
            this.instructionsText = instructionsText;
            this.footerText = footerText;
            this.fileName = fileName;
            this.answerKeyId = answerKeyId;
            this.layoutId = layoutId;
            this.layoutVersion = layoutVersion;
        }

        private boolean isComplete() {
            return code >= 0 && version >= 0 && name >= 0
                    && questionCount >= 0 && optionCount >= 0
                    && questionsPerColumn >= 0 && answers >= 0;
        }
    }

    private static final class RawRecord {
        private final int lineNumber;
        private final List<String> fields;

        private RawRecord(int lineNumber, List<String> fields) {
            this.lineNumber = lineNumber;
            this.fields = new ArrayList<>(fields);
        }
    }

    private static final class ReadResult {
        private final List<RawRecord> records;
        private final List<Issue> issues;

        private ReadResult(List<RawRecord> records, List<Issue> issues) {
            this.records = records;
            this.issues = issues;
        }
    }

    private static final class DecodedText {
        private final String text;
        private final String encoding;

        private DecodedText(String text, String encoding) {
            this.text = text;
            this.encoding = encoding;
        }
    }
}
