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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pré-visualiza CSVs que vinculam versões exatas de prova a turmas.
 *
 * Colunas obrigatórias: {@code codigo_aplicacao}, {@code prova},
 * {@code versao_prova} e {@code turma}. A coluna opcional
 * {@code id_interno_aplicacao} permite reimportar uma exportação sem trocar
 * a identidade persistida. Vírgula e ponto e vírgula são aceitos, assim
 * como UTF-8 e Windows-1252.
 */
public final class OmrAssessmentApplicationCsvParser {

    private static final Charset WINDOWS_1252 =
            Charset.forName("windows-1252");

    public Preview parse(String csvText) {
        return parseDecodedText(csvText, "Texto");
    }

    public Preview parse(byte[] csvBytes) {
        if (csvBytes == null || csvBytes.length == 0) {
            return emptyPreview("Arquivo");
        }

        DecodedText decoded = decode(csvBytes);
        return parseDecodedText(decoded.text, decoded.encoding);
    }

    private Preview parseDecodedText(
            String csvText,
            String encoding
    ) {
        if (csvText == null || csvText.trim().isEmpty()) {
            return emptyPreview(encoding);
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

        if (readResult.records.size() == 1) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O CSV não possui aplicações de prova."
            ));
        }

        List<CandidateRow> candidates = new ArrayList<>();

        for (int index = 1;
             index < readResult.records.size();
             index++) {
            RawRecord record = readResult.records.get(index);
            String applicationCode = normalizeWords(fieldAt(
                    record.fields,
                    indexes.applicationCode
            ));
            String assessmentReference = normalizeWords(fieldAt(
                    record.fields,
                    indexes.assessmentReference
            ));
            String assessmentVersionText = normalizeWords(fieldAt(
                    record.fields,
                    indexes.assessmentVersion
            ));
            String classReference = normalizeWords(fieldAt(
                    record.fields,
                    indexes.classReference
            ));
            String applicationInternalId = normalizeWords(fieldAt(
                    record.fields,
                    indexes.applicationInternalId
            ));
            boolean valid = true;

            if (applicationCode.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O código da aplicação está vazio."
                ));
                valid = false;
            }
            if (assessmentReference.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A referência da prova está vazia."
                ));
                valid = false;
            }

            Integer assessmentVersion = positiveIntegerOrNull(
                    assessmentVersionText
            );
            if (assessmentVersion == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A versão da prova deve ser um inteiro positivo."
                ));
                valid = false;
            }

            if (classReference.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A referência da turma está vazia."
                ));
                valid = false;
            }

            if (valid) {
                candidates.add(new CandidateRow(
                        record.lineNumber,
                        applicationCode,
                        assessmentReference,
                        assessmentVersion,
                        classReference,
                        applicationInternalId.isEmpty()
                                ? null : applicationInternalId
                ));
            }
        }

        Map<String, Integer> codeCounts = new LinkedHashMap<>();
        for (CandidateRow candidate : candidates) {
            String key = canonical(candidate.applicationCode);
            Integer previous = codeCounts.get(key);
            codeCounts.put(key, previous == null ? 1 : previous + 1);
        }

        Map<String, Integer> internalIdCounts = new LinkedHashMap<>();
        for (CandidateRow candidate : candidates) {
            if (candidate.applicationInternalId == null) {
                continue;
            }
            Integer previous = internalIdCounts.get(
                    candidate.applicationInternalId
            );
            internalIdCounts.put(
                    candidate.applicationInternalId,
                    previous == null ? 1 : previous + 1
            );
        }

        List<Row> rows = new ArrayList<>();
        for (CandidateRow candidate : candidates) {
            if (codeCounts.get(canonical(
                    candidate.applicationCode
            )) > 1) {
                issues.add(new Issue(
                        candidate.lineNumber,
                        "Código de aplicação repetido no CSV: "
                                + candidate.applicationCode + "."
                ));
                continue;
            }
            if (candidate.applicationInternalId != null
                    && internalIdCounts.get(
                    candidate.applicationInternalId
            ) > 1) {
                issues.add(new Issue(
                        candidate.lineNumber,
                        "ID interno da aplicação repetido no CSV: "
                                + candidate.applicationInternalId + "."
                ));
                continue;
            }
            rows.add(candidate.toRow());
        }

        sortIssues(issues);
        return new Preview(delimiter, encoding, rows, issues);
    }

    private static Preview emptyPreview(String encoding) {
        return new Preview(
                ',',
                encoding,
                Collections.emptyList(),
                Collections.singletonList(new Issue(
                        1,
                        "O CSV de aplicações está vazio."
                ))
        );
    }

    private static HeaderIndexes resolveHeader(
            RawRecord header,
            List<Issue> issues
    ) {
        HeaderIndexes indexes = new HeaderIndexes();

        for (int index = 0;
             index < header.fields.size();
             index++) {
            String normalized = normalizeHeader(
                    header.fields.get(index)
            );

            if (isApplicationCodeHeader(normalized)) {
                indexes.applicationCode = assignHeader(
                        indexes.applicationCode,
                        index,
                        header.lineNumber,
                        "código da aplicação",
                        issues
                );
            } else if (isAssessmentReferenceHeader(normalized)) {
                indexes.assessmentReference = assignHeader(
                        indexes.assessmentReference,
                        index,
                        header.lineNumber,
                        "prova",
                        issues
                );
            } else if (isAssessmentVersionHeader(normalized)) {
                indexes.assessmentVersion = assignHeader(
                        indexes.assessmentVersion,
                        index,
                        header.lineNumber,
                        "versão da prova",
                        issues
                );
            } else if (isClassReferenceHeader(normalized)) {
                indexes.classReference = assignHeader(
                        indexes.classReference,
                        index,
                        header.lineNumber,
                        "turma",
                        issues
                );
            } else if (isApplicationInternalIdHeader(normalized)) {
                indexes.applicationInternalId = assignHeader(
                        indexes.applicationInternalId,
                        index,
                        header.lineNumber,
                        "ID interno da aplicação",
                        issues
                );
            }
        }

        indexes.reportMissing(header.lineNumber, issues);
        return indexes;
    }

    private static int assignHeader(
            int currentIndex,
            int candidateIndex,
            int lineNumber,
            String description,
            List<Issue> issues
    ) {
        if (currentIndex >= 0) {
            issues.add(new Issue(
                    lineNumber,
                    "O cabeçalho possui mais de uma coluna de "
                            + description + "."
            ));
            return currentIndex;
        }
        return candidateIndex;
    }

    private static boolean isApplicationCodeHeader(String value) {
        return value.equals("codigo_aplicacao")
                || value.equals("aplicacao")
                || value.equals("id_aplicacao");
    }

    private static boolean isAssessmentReferenceHeader(String value) {
        return value.equals("prova")
                || value.equals("codigo_prova")
                || value.equals("id_prova");
    }

    private static boolean isAssessmentVersionHeader(String value) {
        return value.equals("versao_prova")
                || value.equals("versao_da_prova")
                || value.equals("versao");
    }

    private static boolean isClassReferenceHeader(String value) {
        return value.equals("turma")
                || value.equals("codigo_turma")
                || value.equals("id_turma");
    }

    private static boolean isApplicationInternalIdHeader(String value) {
        return value.equals("id_interno_aplicacao")
                || value.equals("application_id");
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
                    addRecord(
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
                afterClosingQuote = false;
                field.append(character);
                continue;
            }

            if (character == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (isLineBreak(character)) {
                fields.add(field.toString());
                addRecord(
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
            } else if (character == '"' && field.length() == 0) {
                insideQuotes = true;
            } else {
                field.append(character);
            }
        }

        if (insideQuotes) {
            issues.add(new Issue(
                    recordLineNumber,
                    "Há uma célula com aspas não encerradas."
            ));
            recordInvalid = true;
        }

        if (field.length() > 0 || !fields.isEmpty()
                || afterClosingQuote) {
            fields.add(field.toString());
            addRecord(
                    records,
                    fields,
                    recordLineNumber,
                    recordInvalid
            );
        }

        return new RecordReadResult(records, issues);
    }

    private static void addRecord(
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
        for (String value : fields) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static char detectDelimiter(String text) {
        int commaCount = 0;
        int semicolonCount = 0;
        boolean insideQuotes = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                if (insideQuotes
                        && index + 1 < text.length()
                        && text.charAt(index + 1) == '"') {
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (!insideQuotes && character == ',') {
                commaCount++;
            } else if (!insideQuotes && character == ';') {
                semicolonCount++;
            } else if (!insideQuotes && isLineBreak(character)) {
                break;
            }
        }

        return semicolonCount > commaCount ? ';' : ',';
    }

    private static DecodedText decode(byte[] bytes) {
        try {
            String utf8 = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new DecodedText(
                    removeLeadingBom(utf8),
                    "UTF-8"
            );
        } catch (CharacterCodingException exception) {
            return new DecodedText(
                    removeLeadingBom(new String(bytes, WINDOWS_1252)),
                    "Windows-1252"
            );
        }
    }

    private static String removeLeadingBom(String value) {
        return value != null
                && !value.isEmpty()
                && value.charAt(0) == '\uFEFF'
                ? value.substring(1)
                : value;
    }

    private static String fieldAt(
            List<String> fields,
            int index
    ) {
        return index >= 0 && index < fields.size()
                ? fields.get(index)
                : "";
    }

    private static Integer positiveIntegerOrNull(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String normalizeWords(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeHeader(String value) {
        String withoutAccents = Normalizer.normalize(
                value == null ? "" : value.trim(),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");

        return withoutAccents
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String canonical(String value) {
        String withoutAccents = Normalizer.normalize(
                normalizeWords(value),
                Normalizer.Form.NFD
        ).replaceAll("\\p{M}+", "");
        return withoutAccents.toUpperCase(Locale.ROOT);
    }

    private static boolean isLineBreak(char character) {
        return character == '\r' || character == '\n';
    }

    private static void sortIssues(List<Issue> issues) {
        issues.sort(
                Comparator.comparingInt(Issue::getLineNumber)
                        .thenComparing(Issue::getMessage)
        );
    }

    public static final class Row {
        private final int lineNumber;
        private final String applicationCode;
        private final String assessmentReference;
        private final int assessmentVersion;
        private final String classReference;
        private final String applicationInternalId;

        private Row(
                int lineNumber,
                String applicationCode,
                String assessmentReference,
                int assessmentVersion,
                String classReference,
                String applicationInternalId
        ) {
            this.lineNumber = lineNumber;
            this.applicationCode = applicationCode;
            this.assessmentReference = assessmentReference;
            this.assessmentVersion = assessmentVersion;
            this.classReference = classReference;
            this.applicationInternalId = applicationInternalId;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getApplicationCode() {
            return applicationCode;
        }

        public String getAssessmentReference() {
            return assessmentReference;
        }

        public int getAssessmentVersion() {
            return assessmentVersion;
        }

        public String getClassReference() {
            return classReference;
        }

        public String getApplicationInternalId() {
            return applicationInternalId;
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
        private final String applicationCode;
        private final String assessmentReference;
        private final int assessmentVersion;
        private final String classReference;
        private final String applicationInternalId;

        private CandidateRow(
                int lineNumber,
                String applicationCode,
                String assessmentReference,
                int assessmentVersion,
                String classReference,
                String applicationInternalId
        ) {
            this.lineNumber = lineNumber;
            this.applicationCode = applicationCode;
            this.assessmentReference = assessmentReference;
            this.assessmentVersion = assessmentVersion;
            this.classReference = classReference;
            this.applicationInternalId = applicationInternalId;
        }

        private Row toRow() {
            return new Row(
                    lineNumber,
                    applicationCode,
                    assessmentReference,
                    assessmentVersion,
                    classReference,
                    applicationInternalId
            );
        }
    }

    private static final class RawRecord {
        private final int lineNumber;
        private final List<String> fields;

        private RawRecord(int lineNumber, List<String> fields) {
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
        private int applicationCode = -1;
        private int assessmentReference = -1;
        private int assessmentVersion = -1;
        private int classReference = -1;
        private int applicationInternalId = -1;

        private boolean isComplete() {
            return applicationCode >= 0
                    && assessmentReference >= 0
                    && assessmentVersion >= 0
                    && classReference >= 0;
        }

        private void reportMissing(
                int lineNumber,
                List<Issue> issues
        ) {
            reportMissing(
                    applicationCode,
                    "codigo_aplicacao",
                    lineNumber,
                    issues
            );
            reportMissing(
                    assessmentReference,
                    "prova",
                    lineNumber,
                    issues
            );
            reportMissing(
                    assessmentVersion,
                    "versao_prova",
                    lineNumber,
                    issues
            );
            reportMissing(
                    classReference,
                    "turma",
                    lineNumber,
                    issues
            );
        }

        private static void reportMissing(
                int index,
                String headerName,
                int lineNumber,
                List<Issue> issues
        ) {
            if (index < 0) {
                issues.add(new Issue(
                        lineNumber,
                        "O cabeçalho deve possuir a coluna "
                                + headerName + "."
                ));
            }
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
