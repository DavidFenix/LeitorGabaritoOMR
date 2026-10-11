package com.example.leitorgabaritoomr.application.classroom;

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
 * Analisa o CSV completo de turmas e listas de chamada sem persistir dados.
 * Matrículas são sempre texto, preservando zeros à esquerda.
 */
public final class OmrClassRosterCsvParser {

    private static final Charset WINDOWS_1252 =
            Charset.forName("windows-1252");

    public Preview parse(String csvText) {
        return parseText(csvText, "Texto");
    }

    public Preview parse(byte[] csvBytes) {
        if (csvBytes == null || csvBytes.length == 0) {
            return emptyPreview("Arquivo");
        }
        Decoded decoded = decode(csvBytes);
        return parseText(decoded.text, decoded.encoding);
    }

    private Preview parseText(String csvText, String encoding) {
        if (csvText == null || csvText.trim().isEmpty()) {
            return emptyPreview(encoding);
        }

        String text = stripBom(csvText);
        char delimiter = detectDelimiter(text);
        ReadResult read = readRecords(text, delimiter);
        List<Issue> issues = new ArrayList<>(read.issues);

        if (read.records.isEmpty()) {
            if (issues.isEmpty()) {
                issues.add(new Issue(1, "O CSV não possui cabeçalho."));
            }
            return preview(delimiter, encoding, null, issues);
        }

        RawRecord header = read.records.get(0);
        HeaderIndexes indexes = resolveHeader(header, issues);
        if (!indexes.isComplete()) {
            sortIssues(issues);
            return preview(delimiter, encoding, null, issues);
        }
        if (read.records.size() == 1) {
            issues.add(new Issue(
                    header.lineNumber,
                    "O CSV não possui linhas de matrícula em turma."
            ));
        }

        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < read.records.size(); index++) {
            Row row = parseRow(read.records.get(index), indexes, issues);
            if (row != null) {
                rows.add(row);
            }
        }

        validateRelationships(rows, issues);
        sortIssues(issues);
        return preview(delimiter, encoding, rows, issues);
    }

    private static Row parseRow(
            RawRecord record,
            HeaderIndexes indexes,
            List<Issue> issues
    ) {
        String className = words(field(record, indexes.className));
        String gradeLevel = words(field(record, indexes.gradeLevel));
        String yearText = words(field(record, indexes.schoolYear));
        String registration = registration(field(
                record,
                indexes.registration
        ));
        String studentName = words(field(record, indexes.studentName));
        String positionText = words(field(
                record,
                indexes.rosterPosition
        ));
        boolean valid = true;

        if (className.isEmpty()) {
            issues.add(new Issue(record.lineNumber, "O nome da turma está vazio."));
            valid = false;
        }
        if (gradeLevel.isEmpty()) {
            issues.add(new Issue(record.lineNumber, "A etapa da turma está vazia."));
            valid = false;
        }
        Integer year = positiveInteger(
                yearText,
                record.lineNumber,
                "O ano letivo deve ser um número inteiro positivo.",
                issues
        );
        if (year == null) {
            valid = false;
        }
        if (registration.isEmpty()) {
            issues.add(new Issue(
                    record.lineNumber,
                    "A matrícula do aluno está vazia."
            ));
            valid = false;
        }
        if (studentName.isEmpty()) {
            issues.add(new Issue(record.lineNumber, "O nome do aluno está vazio."));
            valid = false;
        }
        Integer position = positiveInteger(
                positionText,
                record.lineNumber,
                "A posição na chamada deve ser um número inteiro positivo.",
                issues
        );
        if (position == null) {
            valid = false;
        }

        return valid
                ? new Row(
                record.lineNumber,
                className,
                gradeLevel,
                year,
                registration,
                studentName,
                position
        )
                : null;
    }

    private static void validateRelationships(
            List<Row> rows,
            List<Issue> issues
    ) {
        Map<String, Row> classes = new LinkedHashMap<>();
        Map<String, Row> students = new LinkedHashMap<>();
        Map<String, Row> enrollmentKeys = new LinkedHashMap<>();
        Map<String, Row> positionKeys = new LinkedHashMap<>();
        Map<String, Row> studentYears = new LinkedHashMap<>();

        for (Row row : rows) {
            String classKey = classKey(row.className, row.schoolYear);
            Row earlierClass = classes.putIfAbsent(classKey, row);
            if (earlierClass != null
                    && !identity(earlierClass.gradeLevel).equals(
                    identity(row.gradeLevel)
            )) {
                issues.add(new Issue(
                        row.lineNumber,
                        "A mesma turma e ano letivo possuem etapas diferentes."
                ));
            }

            Row earlierStudent = students.putIfAbsent(
                    row.registration,
                    row
            );
            if (earlierStudent != null
                    && !identity(earlierStudent.studentName).equals(
                    identity(row.studentName)
            )) {
                issues.add(new Issue(
                        row.lineNumber,
                        "A matrícula " + row.registration
                                + " possui nomes diferentes no CSV."
                ));
            }

            String enrollmentKey = classKey + "\u0000" + row.registration;
            if (enrollmentKeys.putIfAbsent(enrollmentKey, row) != null) {
                issues.add(new Issue(
                        row.lineNumber,
                        "A matrícula " + row.registration
                                + " está repetida na mesma turma."
                ));
            }

            String positionKey = classKey + "\u0000" + row.rosterPosition;
            if (positionKeys.putIfAbsent(positionKey, row) != null) {
                issues.add(new Issue(
                        row.lineNumber,
                        "A posição " + row.rosterPosition
                                + " está repetida na mesma turma."
                ));
            }

            String studentYearKey = row.registration
                    + "\u0000" + row.schoolYear;
            Row earlierYear = studentYears.putIfAbsent(studentYearKey, row);
            if (earlierYear != null
                    && !classKey(
                    earlierYear.className,
                    earlierYear.schoolYear
            ).equals(classKey)) {
                issues.add(new Issue(
                        row.lineNumber,
                        "A matrícula " + row.registration
                                + " aparece em mais de uma turma no ano letivo "
                                + row.schoolYear + "."
                ));
            }
        }
    }

    private static HeaderIndexes resolveHeader(
            RawRecord header,
            List<Issue> issues
    ) {
        int[] values = {-1, -1, -1, -1, -1, -1};
        String[] labels = {
                "turma", "etapa", "ano letivo",
                "matrícula", "nome", "posição"
        };

        for (int index = 0; index < header.fields.size(); index++) {
            int kind = headerKind(normalizeHeader(header.fields.get(index)));
            if (kind < 0) {
                continue;
            }
            if (values[kind] >= 0) {
                issues.add(new Issue(
                        header.lineNumber,
                        "O cabeçalho possui mais de uma coluna de "
                                + labels[kind] + "."
                ));
            } else {
                values[kind] = index;
            }
        }

        String[] required = {
                "turma", "etapa", "ano_letivo",
                "matricula", "nome", "posicao"
        };
        for (int index = 0; index < values.length; index++) {
            if (values[index] < 0) {
                issues.add(new Issue(
                        header.lineNumber,
                        "O cabeçalho deve possuir a coluna "
                                + required[index] + "."
                ));
            }
        }
        return new HeaderIndexes(values);
    }

    private static int headerKind(String value) {
        if (oneOf(value, "turma", "class", "classe", "nome_turma",
                "nome_da_turma")) {
            return 0;
        }
        if (oneOf(value, "etapa", "serie", "grade", "grade_level",
                "nivel")) {
            return 1;
        }
        if (oneOf(value, "ano_letivo", "ano", "school_year")) {
            return 2;
        }
        if (oneOf(value, "matricula", "registration", "numero_matricula",
                "numero_de_matricula")) {
            return 3;
        }
        if (oneOf(value, "nome", "name", "nome_aluno", "nome_do_aluno",
                "aluno")) {
            return 4;
        }
        if (oneOf(value, "posicao", "numero_chamada", "numero_da_chamada",
                "chamada", "roster_position")) {
            return 5;
        }
        return -1;
    }

    private static boolean oneOf(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static ReadResult readRecords(String text, char delimiter) {
        List<RawRecord> records = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int line = 1;
        int recordLine = 1;
        boolean quoted = false;
        boolean afterQuote = false;
        boolean invalid = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < text.length()
                            && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else if (lineBreak(character)) {
                    if (character == '\r' && index + 1 < text.length()
                            && text.charAt(index + 1) == '\n') {
                        index++;
                    }
                    field.append('\n');
                    line++;
                } else {
                    field.append(character);
                }
                continue;
            }

            if (afterQuote) {
                if (character == delimiter) {
                    fields.add(field.toString());
                    field.setLength(0);
                    afterQuote = false;
                    continue;
                }
                if (lineBreak(character)) {
                    fields.add(field.toString());
                    addRecord(records, fields, recordLine, invalid);
                    fields = new ArrayList<>();
                    field.setLength(0);
                    afterQuote = false;
                    invalid = false;
                    if (character == '\r' && index + 1 < text.length()
                            && text.charAt(index + 1) == '\n') {
                        index++;
                    }
                    line++;
                    recordLine = line;
                    continue;
                }
                if (Character.isWhitespace(character)) {
                    continue;
                }
                if (!invalid) {
                    issues.add(new Issue(
                            recordLine,
                            "Há conteúdo inesperado após uma célula entre aspas."
                    ));
                }
                invalid = true;
                afterQuote = false;
                field.append(character);
                continue;
            }

            if (character == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (lineBreak(character)) {
                fields.add(field.toString());
                addRecord(records, fields, recordLine, invalid);
                fields = new ArrayList<>();
                field.setLength(0);
                invalid = false;
                if (character == '\r' && index + 1 < text.length()
                        && text.charAt(index + 1) == '\n') {
                    index++;
                }
                line++;
                recordLine = line;
            } else if (character == '"') {
                if (field.length() == 0) {
                    quoted = true;
                } else {
                    if (!invalid) {
                        issues.add(new Issue(
                                recordLine,
                                "Há aspas em posição inválida no CSV."
                        ));
                    }
                    invalid = true;
                    field.append(character);
                }
            } else {
                field.append(character);
            }
        }

        if (quoted) {
            issues.add(new Issue(
                    recordLine,
                    "Há uma célula entre aspas que não foi encerrada."
            ));
            invalid = true;
        }
        if (!quoted && (field.length() > 0
                || !fields.isEmpty() || afterQuote)) {
            fields.add(field.toString());
            addRecord(records, fields, recordLine, invalid);
        }
        return new ReadResult(records, issues);
    }

    private static void addRecord(
            List<RawRecord> records,
            List<String> fields,
            int line,
            boolean invalid
    ) {
        if (invalid || (fields.size() == 1
                && fields.get(0).trim().isEmpty())) {
            return;
        }
        records.add(new RawRecord(line, new ArrayList<>(fields)));
    }

    private static char detectDelimiter(String text) {
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length() && !lineBreak(text.charAt(end))) {
                end++;
            }
            String line = text.substring(start, end);
            if (!line.trim().isEmpty()) {
                return countOutsideQuotes(line, ';')
                        > countOutsideQuotes(line, ',') ? ';' : ',';
            }
            if (end < text.length() && text.charAt(end) == '\r'
                    && end + 1 < text.length()
                    && text.charAt(end + 1) == '\n') {
                end++;
            }
            start = end + 1;
        }
        return ',';
    }

    private static int countOutsideQuotes(String line, char delimiter) {
        int count = 0;
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && character == delimiter) {
                count++;
            }
        }
        return count;
    }

    private static Decoded decode(byte[] bytes) {
        if (prefix(bytes, 0xEF, 0xBB, 0xBF)) {
            return new Decoded(new String(
                    bytes, 3, bytes.length - 3, StandardCharsets.UTF_8
            ), "UTF-8 BOM");
        }
        if (prefix(bytes, 0xFF, 0xFE)) {
            return new Decoded(new String(
                    bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE
            ), "UTF-16LE");
        }
        if (prefix(bytes, 0xFE, 0xFF)) {
            return new Decoded(new String(
                    bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE
            ), "UTF-16BE");
        }
        try {
            String utf8 = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return new Decoded(utf8, "UTF-8");
        } catch (CharacterCodingException exception) {
            return new Decoded(
                    new String(bytes, WINDOWS_1252),
                    "Windows-1252"
            );
        }
    }

    private static boolean prefix(byte[] bytes, int... prefix) {
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

    private static Integer positiveInteger(
            String value,
            int line,
            String message,
            List<Issue> issues
    ) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Produz o mesmo problema de validação abaixo.
        }
        issues.add(new Issue(line, message));
        return null;
    }

    private static String field(RawRecord record, int index) {
        return index >= 0 && index < record.fields.size()
                ? record.fields.get(index) : "";
    }

    private static String words(String value) {
        return value == null ? ""
                : value.trim().replaceAll("\\s+", " ");
    }

    private static String registration(String value) {
        return value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    static String classKey(String className, int schoolYear) {
        return schoolYear + "\u0000" + identity(className);
    }

    private static String identity(String value) {
        return Normalizer.normalize(words(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeHeader(String value) {
        return identity(value)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static String stripBom(String value) {
        return !value.isEmpty() && value.charAt(0) == '\uFEFF'
                ? value.substring(1) : value;
    }

    private static boolean lineBreak(char value) {
        return value == '\n' || value == '\r';
    }

    private static void sortIssues(List<Issue> issues) {
        issues.sort(Comparator.comparingInt(Issue::getLineNumber));
    }

    private static Preview emptyPreview(String encoding) {
        return new Preview(
                ',', encoding, Collections.emptyList(),
                Collections.singletonList(new Issue(
                        1,
                        "O CSV de turmas e matrículas está vazio."
                ))
        );
    }

    private static Preview preview(
            char delimiter,
            String encoding,
            List<Row> rows,
            List<Issue> issues
    ) {
        return new Preview(
                delimiter,
                encoding,
                rows == null ? Collections.emptyList() : rows,
                issues
        );
    }

    public static final class Row {
        private final int lineNumber;
        private final String className;
        private final String gradeLevel;
        private final int schoolYear;
        private final String registration;
        private final String studentName;
        private final int rosterPosition;

        private Row(
                int lineNumber,
                String className,
                String gradeLevel,
                int schoolYear,
                String registration,
                String studentName,
                int rosterPosition
        ) {
            this.lineNumber = lineNumber;
            this.className = className;
            this.gradeLevel = gradeLevel;
            this.schoolYear = schoolYear;
            this.registration = registration;
            this.studentName = studentName;
            this.rosterPosition = rosterPosition;
        }

        public int getLineNumber() { return lineNumber; }
        public String getClassName() { return className; }
        public String getGradeLevel() { return gradeLevel; }
        public int getSchoolYear() { return schoolYear; }
        public String getRegistration() { return registration; }
        public String getStudentName() { return studentName; }
        public int getRosterPosition() { return rosterPosition; }
    }

    public static final class Issue {
        private final int lineNumber;
        private final String message;

        private Issue(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }

        public int getLineNumber() { return lineNumber; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "linha " + lineNumber + ": " + message;
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
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }

        public char getDelimiter() { return delimiter; }
        public String getEncoding() { return encoding; }
        public List<Row> getRows() { return rows; }
        public List<Issue> getIssues() { return issues; }
        public int getValidRowCount() { return rows.size(); }
        public int getIssueCount() { return issues.size(); }
        public boolean isReadyToImport() {
            return !rows.isEmpty() && issues.isEmpty();
        }
    }

    private static final class HeaderIndexes {
        private final int className;
        private final int gradeLevel;
        private final int schoolYear;
        private final int registration;
        private final int studentName;
        private final int rosterPosition;

        private HeaderIndexes(int[] values) {
            className = values[0];
            gradeLevel = values[1];
            schoolYear = values[2];
            registration = values[3];
            studentName = values[4];
            rosterPosition = values[5];
        }

        private boolean isComplete() {
            return className >= 0 && gradeLevel >= 0 && schoolYear >= 0
                    && registration >= 0 && studentName >= 0
                    && rosterPosition >= 0;
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

    private static final class ReadResult {
        private final List<RawRecord> records;
        private final List<Issue> issues;

        private ReadResult(List<RawRecord> records, List<Issue> issues) {
            this.records = records;
            this.issues = issues;
        }
    }

    private static final class Decoded {
        private final String text;
        private final String encoding;

        private Decoded(String text, String encoding) {
            this.text = text;
            this.encoding = encoding;
        }
    }
}
