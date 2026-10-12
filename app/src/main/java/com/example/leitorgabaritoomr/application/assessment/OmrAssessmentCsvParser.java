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
 * Analisa um CSV de provas versionadas sem persistir nenhuma linha.
 *
 * O formato canônico usa: {@code codigo_prova}, {@code versao_prova},
 * {@code titulo}, {@code disciplina}, {@code etapa}, {@code periodo},
 * {@code ano_letivo}, {@code gabarito} e {@code versao_gabarito}.
 */
public final class OmrAssessmentCsvParser {

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
                    "O CSV não possui linhas de provas."
            ));
        }

        List<CandidateRow> candidates = new ArrayList<>();

        for (int index = 1;
             index < readResult.records.size();
             index++) {

            RawRecord record = readResult.records.get(index);

            String assessmentCode = normalizeCode(
                    fieldAt(record.fields, indexes.assessmentCode)
            );
            Integer assessmentVersion = parsePositiveInteger(
                    fieldAt(record.fields, indexes.assessmentVersion)
            );
            String title = normalizeText(
                    fieldAt(record.fields, indexes.title)
            );
            String subject = normalizeText(
                    fieldAt(record.fields, indexes.subject)
            );
            String gradeLevel = normalizeText(
                    fieldAt(record.fields, indexes.gradeLevel)
            );
            String academicPeriod = normalizeText(
                    fieldAt(record.fields, indexes.academicPeriod)
            );
            Integer schoolYear = parsePositiveInteger(
                    fieldAt(record.fields, indexes.schoolYear)
            );
            String answerKeyReference = normalizeText(
                    fieldAt(record.fields, indexes.answerKeyReference)
            );
            Integer answerKeyVersion = parsePositiveInteger(
                    fieldAt(record.fields, indexes.answerKeyVersion)
            );

            boolean valid = true;

            if (assessmentCode.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O código da prova está vazio."
                ));
                valid = false;
            }

            if (assessmentVersion == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A versão da prova deve ser um inteiro positivo."
                ));
                valid = false;
            }

            if (title.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O título da prova está vazio."
                ));
                valid = false;
            }

            if (subject.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A disciplina da prova está vazia."
                ));
                valid = false;
            }

            if (gradeLevel.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A etapa da prova está vazia."
                ));
                valid = false;
            }

            if (academicPeriod.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O período acadêmico está vazio."
                ));
                valid = false;
            }

            if (schoolYear == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "O ano letivo deve ser um inteiro positivo."
                ));
                valid = false;
            }

            if (answerKeyReference.isEmpty()) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A referência do gabarito está vazia."
                ));
                valid = false;
            }

            if (answerKeyVersion == null) {
                issues.add(new Issue(
                        record.lineNumber,
                        "A versão do gabarito deve ser um inteiro positivo."
                ));
                valid = false;
            }

            if (valid) {
                candidates.add(new CandidateRow(
                        record.lineNumber,
                        assessmentCode,
                        assessmentVersion,
                        title,
                        subject,
                        gradeLevel,
                        academicPeriod,
                        schoolYear,
                        answerKeyReference,
                        answerKeyVersion
                ));
            }
        }

        Map<String, Integer> identityCounts =
                new LinkedHashMap<>();

        for (CandidateRow candidate : candidates) {
            String identity = candidate.assessmentCode
                    + "\u0000"
                    + candidate.assessmentVersion;

            Integer previous = identityCounts.get(identity);
            identityCounts.put(
                    identity,
                    previous == null ? 1 : previous + 1
            );
        }

        List<Row> rows = new ArrayList<>();

        for (CandidateRow candidate : candidates) {
            String identity = candidate.assessmentCode
                    + "\u0000"
                    + candidate.assessmentVersion;

            if (identityCounts.get(identity) > 1) {
                issues.add(new Issue(
                        candidate.lineNumber,
                        "Código e versão da prova estão repetidos no CSV: "
                                + candidate.assessmentCode
                                + " versão "
                                + candidate.assessmentVersion
                                + "."
                ));
                continue;
            }

            rows.add(candidate.toRow());
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
                        "O CSV de provas está vazio."
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

            if (isAssessmentCodeHeader(normalized)) {
                indexes.assessmentCode = assignHeader(
                        indexes.assessmentCode,
                        index,
                        header.lineNumber,
                        "código da prova",
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
            } else if (isTitleHeader(normalized)) {
                indexes.title = assignHeader(
                        indexes.title,
                        index,
                        header.lineNumber,
                        "título",
                        issues
                );
            } else if (isSubjectHeader(normalized)) {
                indexes.subject = assignHeader(
                        indexes.subject,
                        index,
                        header.lineNumber,
                        "disciplina",
                        issues
                );
            } else if (isGradeLevelHeader(normalized)) {
                indexes.gradeLevel = assignHeader(
                        indexes.gradeLevel,
                        index,
                        header.lineNumber,
                        "etapa",
                        issues
                );
            } else if (isAcademicPeriodHeader(normalized)) {
                indexes.academicPeriod = assignHeader(
                        indexes.academicPeriod,
                        index,
                        header.lineNumber,
                        "período",
                        issues
                );
            } else if (isSchoolYearHeader(normalized)) {
                indexes.schoolYear = assignHeader(
                        indexes.schoolYear,
                        index,
                        header.lineNumber,
                        "ano letivo",
                        issues
                );
            } else if (isAnswerKeyHeader(normalized)) {
                indexes.answerKeyReference = assignHeader(
                        indexes.answerKeyReference,
                        index,
                        header.lineNumber,
                        "gabarito",
                        issues
                );
            } else if (isAnswerKeyVersionHeader(normalized)) {
                indexes.answerKeyVersion = assignHeader(
                        indexes.answerKeyVersion,
                        index,
                        header.lineNumber,
                        "versão do gabarito",
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
            String label,
            List<Issue> issues
    ) {
        if (currentIndex >= 0) {
            issues.add(new Issue(
                    lineNumber,
                    "O cabeçalho possui mais de uma coluna de "
                            + label
                            + "."
            ));
            return currentIndex;
        }

        return candidateIndex;
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
            for (String value : fields) {
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        return fields.isEmpty()
                || fields.get(0) == null
                || fields.get(0).trim().isEmpty();
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

    private static Integer parsePositiveInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String normalizeCode(String value) {
        return normalizeText(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
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

    private static boolean isAssessmentCodeHeader(String value) {
        return "codigo_prova".equals(value)
                || "codigo".equals(value)
                || "id_prova".equals(value)
                || "assessment_code".equals(value);
    }

    private static boolean isAssessmentVersionHeader(String value) {
        return "versao_prova".equals(value)
                || "versao".equals(value)
                || "assessment_version".equals(value);
    }

    private static boolean isTitleHeader(String value) {
        return "titulo".equals(value)
                || "nome_prova".equals(value)
                || "nome_da_prova".equals(value)
                || "assessment_title".equals(value);
    }

    private static boolean isSubjectHeader(String value) {
        return "disciplina".equals(value)
                || "materia".equals(value)
                || "subject".equals(value);
    }

    private static boolean isGradeLevelHeader(String value) {
        return "etapa".equals(value)
                || "serie".equals(value)
                || "ano_serie".equals(value)
                || "grade_level".equals(value);
    }

    private static boolean isAcademicPeriodHeader(String value) {
        return "periodo".equals(value)
                || "bimestre".equals(value)
                || "academic_period".equals(value);
    }

    private static boolean isSchoolYearHeader(String value) {
        return "ano_letivo".equals(value)
                || "school_year".equals(value);
    }

    private static boolean isAnswerKeyHeader(String value) {
        return "gabarito".equals(value)
                || "gabarito_id".equals(value)
                || "nome_gabarito".equals(value)
                || "nome_do_gabarito".equals(value)
                || "answer_key".equals(value);
    }

    private static boolean isAnswerKeyVersionHeader(String value) {
        return "versao_gabarito".equals(value)
                || "versao_do_gabarito".equals(value)
                || "answer_key_version".equals(value);
    }

    private static void sortIssues(List<Issue> issues) {
        issues.sort(Comparator.comparingInt(Issue::getLineNumber));
    }

    public static final class Row {
        private final int lineNumber;
        private final String assessmentCode;
        private final int assessmentVersion;
        private final String title;
        private final String subject;
        private final String gradeLevel;
        private final String academicPeriod;
        private final int schoolYear;
        private final String answerKeyReference;
        private final int answerKeyVersion;

        private Row(
                int lineNumber,
                String assessmentCode,
                int assessmentVersion,
                String title,
                String subject,
                String gradeLevel,
                String academicPeriod,
                int schoolYear,
                String answerKeyReference,
                int answerKeyVersion
        ) {
            this.lineNumber = lineNumber;
            this.assessmentCode = assessmentCode;
            this.assessmentVersion = assessmentVersion;
            this.title = title;
            this.subject = subject;
            this.gradeLevel = gradeLevel;
            this.academicPeriod = academicPeriod;
            this.schoolYear = schoolYear;
            this.answerKeyReference = answerKeyReference;
            this.answerKeyVersion = answerKeyVersion;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getAssessmentCode() {
            return assessmentCode;
        }

        public int getAssessmentVersion() {
            return assessmentVersion;
        }

        public String getTitle() {
            return title;
        }

        public String getSubject() {
            return subject;
        }

        public String getGradeLevel() {
            return gradeLevel;
        }

        public String getAcademicPeriod() {
            return academicPeriod;
        }

        public int getSchoolYear() {
            return schoolYear;
        }

        public String getAnswerKeyReference() {
            return answerKeyReference;
        }

        public int getAnswerKeyVersion() {
            return answerKeyVersion;
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
        private final String assessmentCode;
        private final int assessmentVersion;
        private final String title;
        private final String subject;
        private final String gradeLevel;
        private final String academicPeriod;
        private final int schoolYear;
        private final String answerKeyReference;
        private final int answerKeyVersion;

        private CandidateRow(
                int lineNumber,
                String assessmentCode,
                int assessmentVersion,
                String title,
                String subject,
                String gradeLevel,
                String academicPeriod,
                int schoolYear,
                String answerKeyReference,
                int answerKeyVersion
        ) {
            this.lineNumber = lineNumber;
            this.assessmentCode = assessmentCode;
            this.assessmentVersion = assessmentVersion;
            this.title = title;
            this.subject = subject;
            this.gradeLevel = gradeLevel;
            this.academicPeriod = academicPeriod;
            this.schoolYear = schoolYear;
            this.answerKeyReference = answerKeyReference;
            this.answerKeyVersion = answerKeyVersion;
        }

        private Row toRow() {
            return new Row(
                    lineNumber,
                    assessmentCode,
                    assessmentVersion,
                    title,
                    subject,
                    gradeLevel,
                    academicPeriod,
                    schoolYear,
                    answerKeyReference,
                    answerKeyVersion
            );
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
        private int assessmentCode = -1;
        private int assessmentVersion = -1;
        private int title = -1;
        private int subject = -1;
        private int gradeLevel = -1;
        private int academicPeriod = -1;
        private int schoolYear = -1;
        private int answerKeyReference = -1;
        private int answerKeyVersion = -1;

        private boolean isComplete() {
            return assessmentCode >= 0
                    && assessmentVersion >= 0
                    && title >= 0
                    && subject >= 0
                    && gradeLevel >= 0
                    && academicPeriod >= 0
                    && schoolYear >= 0
                    && answerKeyReference >= 0
                    && answerKeyVersion >= 0;
        }

        private void reportMissing(
                int lineNumber,
                List<Issue> issues
        ) {
            reportMissing(
                    assessmentCode,
                    "codigo_prova",
                    lineNumber,
                    issues
            );
            reportMissing(
                    assessmentVersion,
                    "versao_prova",
                    lineNumber,
                    issues
            );
            reportMissing(title, "titulo", lineNumber, issues);
            reportMissing(subject, "disciplina", lineNumber, issues);
            reportMissing(gradeLevel, "etapa", lineNumber, issues);
            reportMissing(
                    academicPeriod,
                    "periodo",
                    lineNumber,
                    issues
            );
            reportMissing(
                    schoolYear,
                    "ano_letivo",
                    lineNumber,
                    issues
            );
            reportMissing(
                    answerKeyReference,
                    "gabarito",
                    lineNumber,
                    issues
            );
            reportMissing(
                    answerKeyVersion,
                    "versao_gabarito",
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
                                + headerName
                                + "."
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
