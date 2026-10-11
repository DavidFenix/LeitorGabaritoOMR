package com.example.leitorgabaritoomr.application.student;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê uma planilha CSV de alunos sem converter matrículas para números.
 *
 * O formato mínimo possui as colunas {@code matricula} e {@code nome}. Tanto
 * vírgula quanto ponto e vírgula são aceitos para permitir arquivos gerados
 * por diferentes versões do Excel. O resultado é apenas uma pré-visualização:
 * nenhuma linha é persistida durante a leitura.
 */
public final class OmrStudentCsvParser {

    private static final Charset WINDOWS_1252 =
            Charset.forName("windows-1252");

    public Preview parse(String csvText) {
        return parseDecodedText(csvText, "Texto");
    }

    public Preview parse(byte[] csvBytes) {
        if (csvBytes == null || csvBytes.length == 0) {
            return invalidEmptyPreview("Arquivo");
        }

        DecodedText decoded = decode(csvBytes);
        return parseDecodedText(decoded.text, decoded.encoding);
    }

    private Preview parseDecodedText(
            String csvText,
            String encoding
    ) {
        if (csvText == null || csvText.trim().isEmpty()) {
            return invalidEmptyPreview(encoding);
        }

        String normalizedText = removeLeadingBom(csvText);
        char delimiter = detectDelimiter(normalizedText);
        RecordReadResult readResult = readRecords(
                normalizedText,
                delimiter
        );

        List<Issue> issues = new ArrayList<>(readResult.issues);

        if (readResult.records.isEmpty()) {
            if (issues.isEmpty()) {
                issues.add(new Issue(
                        1,
                        "O CSV não possui cabeçalho."
                ));
            }

            return new Preview(
                    delimiter,
                    encoding,
                    Collections.emptyList(),
                    issues
            );
        }

        RawRecord header = readResult.records.get(0);
        HeaderIndexes indexes = resolveHeader(
                header,
                issues
        );

        if (!indexes.isComplete()) {
            sortIssues(issues);
            return new Preview(
                    delimiter,
                    encoding,
                    Collections.emptyList(),
                    issues
            );
        }

        if (readResult.records.size() == 1) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O CSV não possui linhas de alunos."
            ));
        }

        List<CandidateRow> candidates = new ArrayList<>();

        for (int index = 1;
             index < readResult.records.size();
             index++) {

            RawRecord record = readResult.records.get(index);
            String registration = normalizeRegistration(
                    fieldAt(record.fields, indexes.registrationIndex)
            );
            String name = normalizeName(
                    fieldAt(record.fields, indexes.nameIndex)
            );

            boolean valid = true;

            if (registration.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A matrícula do aluno está vazia."
                ));
                valid = false;
            }

            if (name.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O nome do aluno está vazio."
                ));
                valid = false;
            }

            if (valid) {
                candidates.add(new CandidateRow(
                        record.lineNumber,
                        registration,
                        name
                ));
            }
        }

        Map<String, Integer> registrationCounts =
                new LinkedHashMap<>();

        for (CandidateRow candidate : candidates) {
            Integer previous = registrationCounts.get(
                    candidate.registration
            );
            registrationCounts.put(
                    candidate.registration,
                    previous == null ? 1 : previous + 1
            );
        }

        List<Row> rows = new ArrayList<>();

        for (CandidateRow candidate : candidates) {
            if (registrationCounts.get(candidate.registration) > 1) {
                issues.add(new Issue(
                        candidate.lineNumber,
                        "Matrícula repetida no CSV: "
                                + candidate.registration + "."
                ));
                continue;
            }

            rows.add(new Row(
                    candidate.lineNumber,
                    candidate.registration,
                    candidate.name
            ));
        }

        sortIssues(issues);

        return new Preview(
                delimiter,
                encoding,
                rows,
                issues
        );
    }

    private static Preview invalidEmptyPreview(String encoding) {
        return new Preview(
                ',',
                encoding,
                Collections.emptyList(),
                Collections.singletonList(new Issue(
                        1,
                        "O CSV de alunos está vazio."
                ))
        );
    }

    private static HeaderIndexes resolveHeader(
            RawRecord header,
            List<Issue> issues
    ) {
        int registrationIndex = -1;
        int nameIndex = -1;

        for (int index = 0;
             index < header.fields.size();
             index++) {

            String normalized = normalizeHeader(
                    header.fields.get(index)
            );

            if (isRegistrationHeader(normalized)) {
                if (registrationIndex >= 0) {
                    issues.add(new Issue(
                            header.lineNumber,
                            "O cabeçalho possui mais de uma coluna de matrícula."
                    ));
                } else {
                    registrationIndex = index;
                }
            }

            if (isNameHeader(normalized)) {
                if (nameIndex >= 0) {
                    issues.add(new Issue(
                            header.lineNumber,
                            "O cabeçalho possui mais de uma coluna de nome."
                    ));
                } else {
                    nameIndex = index;
                }
            }
        }

        if (registrationIndex < 0) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O cabeçalho deve possuir a coluna matricula."
            ));
        }

        if (nameIndex < 0) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O cabeçalho deve possuir a coluna nome."
            ));
        }

        return new HeaderIndexes(
                registrationIndex,
                nameIndex
        );
    }

    private static RecordReadResult readRecords(
            String text,
            char delimiter
    ) {
        List<RawRecord> records = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();

        int lineNumber = 1;
        int recordLineNumber = 1;
        boolean insideQuotes = false;
        boolean afterClosingQuote = false;
        boolean recordInvalid = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (insideQuotes) {
                if (character == '"') {
                    if (index + 1 < text.length()
                            && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        insideQuotes = false;
                        afterClosingQuote = true;
                    }
                } else if (isLineBreak(character)) {
                    if (character == '\r'
                            && index + 1 < text.length()
                            && text.charAt(index + 1) == '\n') {
                        index++;
                    }
                    field.append('\n');
                    lineNumber++;
                } else {
                    field.append(character);
                }

                continue;
            }

            if (afterClosingQuote) {
                if (character == delimiter) {
                    fields.add(field.toString());
                    field.setLength(0);
                    afterClosingQuote = false;
                    continue;
                }

                if (isLineBreak(character)) {
                    fields.add(field.toString());
                    addRecordIfNotBlank(
                            records,
                            fields,
                            recordLineNumber,
                            recordInvalid
                    );
                    fields = new ArrayList<>();
                    field.setLength(0);
                    afterClosingQuote = false;
                    recordInvalid = false;

                    if (character == '\r'
                            && index + 1 < text.length()
                            && text.charAt(index + 1) == '\n') {
                        index++;
                    }

                    lineNumber++;
                    recordLineNumber = lineNumber;
                    continue;
                }

                if (Character.isWhitespace(character)) {
                    continue;
                }

                if (!recordInvalid) {
                    issues.add(new Issue(
                            recordLineNumber,
                            "Há conteúdo inesperado após uma célula entre aspas."
                    ));
                }
                recordInvalid = true;
                field.append(character);
                afterClosingQuote = false;
                continue;
            }

            if (character == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                continue;
            }

            if (isLineBreak(character)) {
                fields.add(field.toString());
                addRecordIfNotBlank(
                        records,
                        fields,
                        recordLineNumber,
                        recordInvalid
                );
                fields = new ArrayList<>();
                field.setLength(0);
                recordInvalid = false;

                if (character == '\r'
                        && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') {
                    index++;
                }

                lineNumber++;
                recordLineNumber = lineNumber;
                continue;
            }

            if (character == '"') {
                if (field.length() == 0) {
                    insideQuotes = true;
                } else {
                    if (!recordInvalid) {
                        issues.add(new Issue(
                                recordLineNumber,
                                "Há aspas em posição inválida no CSV."
                        ));
                    }
                    recordInvalid = true;
                    field.append(character);
                }
                continue;
            }

            field.append(character);
        }

        if (insideQuotes) {
            issues.add(new Issue(
                    recordLineNumber,
                    "Há uma célula entre aspas que não foi encerrada."
            ));
            recordInvalid = true;
        }

        if (!insideQuotes
                && (field.length() > 0
                || !fields.isEmpty()
                || afterClosingQuote)) {
            fields.add(field.toString());
            addRecordIfNotBlank(
                    records,
                    fields,
                    recordLineNumber,
                    recordInvalid
            );
        }

        return new RecordReadResult(records, issues);
    }

    private static void addRecordIfNotBlank(
            List<RawRecord> records,
            List<String> fields,
            int lineNumber,
            boolean invalid
    ) {
        if (invalid || isBlankRecord(fields)) {
            return;
        }

        records.add(new RawRecord(
                lineNumber,
                new ArrayList<>(fields)
        ));
    }

    private static boolean isBlankRecord(List<String> fields) {
        if (fields.size() > 1) {
            return false;
        }

        for (String value : fields) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLineBreak(char character) {
        return character == '\n' || character == '\r';
    }

    private static char detectDelimiter(String text) {
        int lineStart = 0;

        while (lineStart < text.length()) {
            int lineEnd = lineStart;
            while (lineEnd < text.length()
                    && !isLineBreak(text.charAt(lineEnd))) {
                lineEnd++;
            }

            String line = text.substring(lineStart, lineEnd);
            if (!line.trim().isEmpty()) {
                int commas = countOutsideQuotes(line, ',');
                int semicolons = countOutsideQuotes(line, ';');
                return semicolons > commas ? ';' : ',';
            }

            if (lineEnd < text.length()
                    && text.charAt(lineEnd) == '\r'
                    && lineEnd + 1 < text.length()
                    && text.charAt(lineEnd + 1) == '\n') {
                lineEnd++;
            }
            lineStart = lineEnd + 1;
        }

        return ',';
    }

    private static int countOutsideQuotes(
            String line,
            char candidate
    ) {
        int count = 0;
        boolean insideQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (character == '"') {
                if (insideQuotes
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (!insideQuotes && character == candidate) {
                count++;
            }
        }

        return count;
    }

    private static DecodedText decode(byte[] bytes) {
        if (hasPrefix(bytes, 0xEF, 0xBB, 0xBF)) {
            return new DecodedText(
                    new String(
                            bytes,
                            3,
                            bytes.length - 3,
                            StandardCharsets.UTF_8
                    ),
                    "UTF-8 BOM"
            );
        }

        if (hasPrefix(bytes, 0xFF, 0xFE)) {
            return new DecodedText(
                    new String(
                            bytes,
                            2,
                            bytes.length - 2,
                            StandardCharsets.UTF_16LE
                    ),
                    "UTF-16LE"
            );
        }

        if (hasPrefix(bytes, 0xFE, 0xFF)) {
            return new DecodedText(
                    new String(
                            bytes,
                            2,
                            bytes.length - 2,
                            StandardCharsets.UTF_16BE
                    ),
                    "UTF-16BE"
            );
        }

        try {
            String utf8 = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();

            return new DecodedText(utf8, "UTF-8");

        } catch (CharacterCodingException exception) {
            return new DecodedText(
                    new String(bytes, WINDOWS_1252),
                    "Windows-1252"
            );
        }
    }

    private static boolean hasPrefix(
            byte[] bytes,
            int... prefix
    ) {
        if (bytes.length < prefix.length) {
            return false;
        }

        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }

        return true;
    }

    private static String removeLeadingBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF'
                ? text.substring(1)
                : text;
    }

    private static String fieldAt(
            List<String> fields,
            int index
    ) {
        return index >= 0 && index < fields.size()
                ? fields.get(index)
                : "";
    }

    private static String normalizeRegistration(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeName(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }

        String withoutAccents = Normalizer.normalize(
                value.trim(),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");

        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static boolean isRegistrationHeader(String value) {
        return "matricula".equals(value)
                || "registration".equals(value)
                || "numero_matricula".equals(value)
                || "numero_de_matricula".equals(value);
    }

    private static boolean isNameHeader(String value) {
        return "nome".equals(value)
                || "name".equals(value)
                || "nome_aluno".equals(value)
                || "nome_do_aluno".equals(value)
                || "aluno".equals(value);
    }

    private static void sortIssues(List<Issue> issues) {
        issues.sort(Comparator.comparingInt(Issue::getLineNumber));
    }

    public static final class Row {
        private final int lineNumber;
        private final String registration;
        private final String name;

        private Row(
                int lineNumber,
                String registration,
                String name
        ) {
            this.lineNumber = lineNumber;
            this.registration = registration;
            this.name = name;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getRegistration() {
            return registration;
        }

        public String getName() {
            return name;
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

        @Override
        public String toString() {
            return "Linha " + lineNumber + ": " + message;
        }
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

    private static final class CandidateRow {
        private final int lineNumber;
        private final String registration;
        private final String name;

        private CandidateRow(
                int lineNumber,
                String registration,
                String name
        ) {
            this.lineNumber = lineNumber;
            this.registration = registration;
            this.name = name;
        }
    }

    private static final class RawRecord {
        private final int lineNumber;
        private final List<String> fields;

        private RawRecord(
                int lineNumber,
                List<String> fields
        ) {
            this.lineNumber = lineNumber;
            this.fields = fields;
        }
    }

    private static final class RecordReadResult {
        private final List<RawRecord> records;
        private final List<Issue> issues;

        private RecordReadResult(
                List<RawRecord> records,
                List<Issue> issues
        ) {
            this.records = records;
            this.issues = issues;
        }
    }

    private static final class HeaderIndexes {
        private final int registrationIndex;
        private final int nameIndex;

        private HeaderIndexes(
                int registrationIndex,
                int nameIndex
        ) {
            this.registrationIndex = registrationIndex;
            this.nameIndex = nameIndex;
        }

        private boolean isComplete() {
            return registrationIndex >= 0 && nameIndex >= 0;
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
