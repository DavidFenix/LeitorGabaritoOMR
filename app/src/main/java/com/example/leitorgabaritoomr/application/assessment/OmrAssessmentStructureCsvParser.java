package com.example.leitorgabaritoomr.application.assessment;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pre-visualiza o CSV de blocos curriculares sem alterar o banco.
 *
 * O formato canonico possui uma linha por bloco e as colunas:
 * {@code codigo_prova}, {@code versao_prova}, {@code ordem_bloco},
 * {@code codigo_bloco}, {@code area}, {@code codigo_componente},
 * {@code componente} e {@code itens}. As tres colunas de codigo/area sao
 * opcionais; as demais identificam a prova e descrevem a sequencia.
 */
public final class OmrAssessmentStructureCsvParser {

    private static final Charset WINDOWS_1252 =
            Charset.forName("windows-1252");

    public Preview parse(String csvText) {
        return parseDecoded(csvText, "Texto");
    }

    public Preview parse(byte[] csvBytes) {
        if (csvBytes == null || csvBytes.length == 0) {
            return emptyPreview("Arquivo");
        }

        DecodedText decoded = decode(csvBytes);
        return parseDecoded(decoded.text, decoded.encoding);
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
            return new Preview(
                    delimiter,
                    encoding,
                    Collections.emptyList(),
                    issues
            );
        }

        RawRecord header = read.records.get(0);
        HeaderIndexes indexes = resolveHeader(header, issues);

        if (!indexes.isComplete()) {
            sortIssues(issues);
            return new Preview(
                    delimiter,
                    encoding,
                    Collections.emptyList(),
                    issues
            );
        }

        if (read.records.size() == 1) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O CSV nao possui linhas de blocos."
            ));
        }

        List<Row> rows = new ArrayList<>();

        for (int index = 1; index < read.records.size(); index++) {
            RawRecord record = read.records.get(index);
            String assessmentReference = normalizeText(fieldAt(
                    record.fields,
                    indexes.assessmentReference
            ));
            Integer assessmentVersion = positiveInteger(fieldAt(
                    record.fields,
                    indexes.assessmentVersion
            ));
            Integer blockOrder = positiveInteger(fieldAt(
                    record.fields,
                    indexes.blockOrder
            ));
            String blockCode = optionalText(fieldAt(
                    record.fields,
                    indexes.blockCode
            ));
            String areaName = optionalText(fieldAt(
                    record.fields,
                    indexes.areaName
            ));
            String componentCode = optionalText(fieldAt(
                    record.fields,
                    indexes.componentCode
            ));
            String componentName = normalizeText(fieldAt(
                    record.fields,
                    indexes.componentName
            ));
            Integer questionCount = positiveInteger(fieldAt(
                    record.fields,
                    indexes.questionCount
            ));

            boolean valid = true;
            if (assessmentReference.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A referencia da prova esta vazia."
                ));
                valid = false;
            }
            if (assessmentVersion == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A versao da prova deve ser um inteiro positivo."
                ));
                valid = false;
            }
            if (blockOrder == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A ordem do bloco deve ser um inteiro positivo."
                ));
                valid = false;
            }
            if (componentName.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O componente curricular esta vazio."
                ));
                valid = false;
            }
            if (questionCount == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A quantidade de itens deve ser um inteiro positivo."
                ));
                valid = false;
            }

            if (valid) {
                rows.add(new Row(
                        record.lineNumber,
                        assessmentReference,
                        assessmentVersion,
                        blockOrder,
                        blockCode,
                        areaName,
                        componentCode,
                        componentName,
                        questionCount
                ));
            }
        }

        validateGroups(rows, issues);
        sortIssues(issues);

        return new Preview(delimiter, encoding, rows, issues);
    }

    private static void validateGroups(
            List<Row> rows,
            List<Issue> issues
    ) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();

        for (Row row : rows) {
            String identity = canonical(row.assessmentReference)
                    + "\u0000"
                    + row.assessmentVersion;
            List<Row> group = groups.get(identity);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(identity, group);
            }
            group.add(row);
        }

        for (List<Row> group : groups.values()) {
            List<Row> ordered = new ArrayList<>(group);
            ordered.sort(Comparator.comparingInt(Row::getBlockOrder));
            Set<Integer> orders = new HashSet<>();
            Set<String> blockCodes = new HashSet<>();

            for (Row row : ordered) {
                if (!orders.add(row.blockOrder)) {
                    issues.add(new Issue(
                            row.lineNumber,
                            "A ordem " + row.blockOrder
                                    + " esta repetida para esta prova."
                    ));
                }

                if (row.blockCode != null
                        && !blockCodes.add(canonical(row.blockCode))) {
                    issues.add(new Issue(
                            row.lineNumber,
                            "O codigo de bloco " + row.blockCode
                                    + " esta repetido para esta prova."
                    ));
                }
            }

            if (orders.size() != ordered.size()) {
                continue;
            }

            for (int index = 0; index < ordered.size(); index++) {
                int expectedOrder = index + 1;
                if (ordered.get(index).blockOrder != expectedOrder) {
                    issues.add(new Issue(
                            ordered.get(index).lineNumber,
                            "As ordens dos blocos devem formar a sequencia"
                                    + " de 1 a " + ordered.size() + "."
                    ));
                    break;
                }
            }
        }
    }

    private static HeaderIndexes resolveHeader(
            RawRecord header,
            List<Issue> issues
    ) {
        Map<String, List<Integer>> positions = new HashMap<>();

        for (int index = 0; index < header.fields.size(); index++) {
            String canonical = canonicalHeader(header.fields.get(index));
            List<Integer> indexes = positions.get(canonical);
            if (indexes == null) {
                indexes = new ArrayList<>();
                positions.put(canonical, indexes);
            }
            indexes.add(index);
        }

        return new HeaderIndexes(
                requiredHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "codigo_prova",
                        "codigo_prova", "prova", "id_prova",
                        "assessment_id"
                ),
                requiredHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "versao_prova",
                        "versao_prova", "versao", "assessment_version"
                ),
                requiredHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "ordem_bloco",
                        "ordem_bloco", "ordem", "block_order"
                ),
                optionalHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "codigo_bloco",
                        "codigo_bloco", "bloco", "block_id"
                ),
                optionalHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "area",
                        "area", "area_nome", "area_name"
                ),
                optionalHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "codigo_componente",
                        "codigo_componente", "componente_codigo",
                        "component_code"
                ),
                requiredHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "componente",
                        "componente", "disciplina", "component_name"
                ),
                requiredHeader(
                        positions,
                        header.lineNumber,
                        issues,
                        "itens",
                        "itens", "questoes", "quantidade_questoes",
                        "question_count"
                )
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
                positions,
                line,
                issues,
                displayName,
                aliases
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

                if (value == '\r'
                        && index + 1 < text.length()
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
        return new Preview(
                ';',
                encoding,
                Collections.emptyList(),
                Collections.singletonList(new Issue(
                        1,
                        "O CSV de blocos esta vazio."
                ))
        );
    }

    private static String removeBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\ufeff'
                ? text.substring(1)
                : text;
    }

    private static String fieldAt(List<String> fields, int index) {
        return index < 0 || index >= fields.size()
                ? ""
                : fields.get(index);
    }

    private static Integer positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String normalizeText(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    private static String optionalText(String value) {
        String normalized = normalizeText(value);
        return normalized.isEmpty() ? null : normalized;
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
            this.rows = Collections.unmodifiableList(
                    new ArrayList<>(rows)
            );
            this.issues = Collections.unmodifiableList(
                    new ArrayList<>(issues)
            );
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
        private final String assessmentReference;
        private final int assessmentVersion;
        private final int blockOrder;
        private final String blockCode;
        private final String areaName;
        private final String componentCode;
        private final String componentName;
        private final int questionCount;

        private Row(
                int lineNumber,
                String assessmentReference,
                int assessmentVersion,
                int blockOrder,
                String blockCode,
                String areaName,
                String componentCode,
                String componentName,
                int questionCount
        ) {
            this.lineNumber = lineNumber;
            this.assessmentReference = assessmentReference;
            this.assessmentVersion = assessmentVersion;
            this.blockOrder = blockOrder;
            this.blockCode = blockCode;
            this.areaName = areaName;
            this.componentCode = componentCode;
            this.componentName = componentName;
            this.questionCount = questionCount;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getAssessmentReference() {
            return assessmentReference;
        }

        public int getAssessmentVersion() {
            return assessmentVersion;
        }

        public int getBlockOrder() {
            return blockOrder;
        }

        public String getBlockCode() {
            return blockCode;
        }

        public String getAreaName() {
            return areaName;
        }

        public String getComponentCode() {
            return componentCode;
        }

        public String getComponentName() {
            return componentName;
        }

        public int getQuestionCount() {
            return questionCount;
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
        private final int assessmentReference;
        private final int assessmentVersion;
        private final int blockOrder;
        private final int blockCode;
        private final int areaName;
        private final int componentCode;
        private final int componentName;
        private final int questionCount;

        private HeaderIndexes(
                int assessmentReference,
                int assessmentVersion,
                int blockOrder,
                int blockCode,
                int areaName,
                int componentCode,
                int componentName,
                int questionCount
        ) {
            this.assessmentReference = assessmentReference;
            this.assessmentVersion = assessmentVersion;
            this.blockOrder = blockOrder;
            this.blockCode = blockCode;
            this.areaName = areaName;
            this.componentCode = componentCode;
            this.componentName = componentName;
            this.questionCount = questionCount;
        }

        private boolean isComplete() {
            return assessmentReference >= 0
                    && assessmentVersion >= 0
                    && blockOrder >= 0
                    && componentName >= 0
                    && questionCount >= 0;
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

        private ReadResult(
                List<RawRecord> records,
                List<Issue> issues
        ) {
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
