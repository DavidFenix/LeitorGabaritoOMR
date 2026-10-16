package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockResult;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Gera uma pasta de trabalho OOXML (.xlsx) sem dependências externas. */
public final class OmrAssessmentResultXlsxExporter {

    public void write(
            OmrAssessmentResultReport report,
            OutputStream output
    ) throws IOException {
        if (report == null || output == null) {
            throw new IllegalArgumentException(
                    "O relatório e o destino são obrigatórios."
            );
        }

        OmrAssessmentResultExportTable table =
                OmrAssessmentResultExportTable.from(report);
        boolean includeComponents = table.hasBlockColumns();
        ZipOutputStream zip = new ZipOutputStream(output);
        entry(zip, "[Content_Types].xml",
                contentTypes(includeComponents));
        entry(zip, "_rels/.rels", rootRelationships());
        entry(zip, "docProps/app.xml",
                appProperties(includeComponents));
        entry(zip, "docProps/core.xml", coreProperties(report));
        entry(zip, "xl/workbook.xml", workbook(includeComponents));
        entry(zip, "xl/_rels/workbook.xml.rels",
                workbookRelationships(includeComponents));
        entry(zip, "xl/styles.xml", styles());
        entry(zip, "xl/worksheets/sheet1.xml",
                worksheet(report, table));
        if (includeComponents) {
            entry(zip, "xl/worksheets/sheet2.xml",
                    componentsWorksheet(table));
        }
        zip.finish();
        zip.flush();
    }

    private static String worksheet(
            OmrAssessmentResultReport report,
            OmrAssessmentResultExportTable table
    ) {
        StringBuilder xml = new StringBuilder(16384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>")
                .append("<cols>")
                .append("<col min=\"1\" max=\"1\" width=\"10\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"2\" width=\"18\" customWidth=\"1\"/>")
                .append("<col min=\"3\" max=\"3\" width=\"34\" customWidth=\"1\"/>")
                .append("<col min=\"4\" max=\"4\" width=\"24\" customWidth=\"1\"/>")
                .append("<col min=\"5\" max=\"5\" width=\"31\" customWidth=\"1\"/>")
                .append("<col min=\"6\" max=\"13\" width=\"13\" customWidth=\"1\"/>")
                .append("<col min=\"14\" max=\"14\" width=\"22\" customWidth=\"1\"/>");
        int dynamicColumn = 15;
        for (int index = 0; index < table.getBlockCount(); index++) {
            xml.append("<col min=\"").append(dynamicColumn)
                    .append("\" max=\"").append(dynamicColumn)
                    .append("\" width=\"30\" customWidth=\"1\"/>");
            dynamicColumn++;
            xml.append("<col min=\"").append(dynamicColumn)
                    .append("\" max=\"").append(dynamicColumn)
                    .append("\" width=\"28\" customWidth=\"1\"/>");
            dynamicColumn++;
        }
        xml.append("</cols><sheetData>");

        List<String> headers = new ArrayList<>();
        Collections.addAll(headers,
                "Posição", "Matrícula", "Aluno", "ID do aluno",
                "Situação", "Revisão", "Pontos", "Total",
                "Percentual", "Acertos", "Erros", "Em branco",
                "Para revisão", "Registrado em"
        );
        for (OmrAssessmentResultExportTable.BlockColumn blockColumn
                : table.getBlockColumns()) {
            headers.add(blockColumn.getCorrectHeader());
            headers.add(blockColumn.getScoreHeader());
        }
        int row = 1;
        xml.append(rowStart(row, table.hasBlockColumns() ? 42 : 24));
        for (int index = 0; index < headers.size(); index++) {
            xml.append(stringCell(
                    column(index + 1),
                    row,
                    headers.get(index),
                    3
            ));
        }
        xml.append("</row>");
        int headerRow = row;
        row++;

        for (OmrAssessmentResultReport.Row resultRow : report.getRows()) {
            OmrAssessmentParticipant participant = resultRow.getParticipant();
            int statusStyle = statusStyle(resultRow);
            xml.append(rowStart(row, 21))
                    .append(numberCell("A", row,
                            participant.getRosterPosition(), 2))
                    .append(stringCell("B", row,
                            participant.getRegistration(), 2))
                    .append(stringCell("C", row,
                            participant.getStudentName(), 2))
                    .append(stringCell("D", row,
                            participant.getStudentId(), 2))
                    .append(stringCell("E", row,
                            OmrAssessmentResultExportFormatter.status(resultRow),
                            statusStyle));
            if (resultRow.getRevision() > 0) {
                xml.append(numberCell("F", row,
                        resultRow.getRevision(), 2));
            } else {
                xml.append(stringCell("F", row, "", 2));
            }
            if (resultRow.isCorrected()) {
                xml.append(numberCell("G", row,
                                resultRow.getAwardedPoints(), 4))
                        .append(numberCell("H", row,
                                resultRow.getPossiblePoints(), 4))
                        .append(numberCell("I", row,
                                resultRow.getAwardedPercentage() / 100.0, 8))
                        .append(numberCell("J", row,
                                resultRow.getCorrectCount(), 2))
                        .append(numberCell("K", row,
                                resultRow.getIncorrectCount(), 2))
                        .append(numberCell("L", row,
                                resultRow.getBlankCount(), 2))
                        .append(numberCell("M", row,
                                resultRow.getReviewRequiredCount(), 2));
            } else {
                for (String column : new String[]{
                        "G", "H", "I", "J", "K", "L", "M"}) {
                    xml.append(stringCell(column, row, "", 2));
                }
            }
            xml.append(stringCell("N", row,
                    OmrAssessmentResultExportFormatter.dateTime(
                            resultRow.getRecordedAtEpochMillis()), 2));
            int blockColumnIndex = 15;
            for (OmrAssessmentResultExportTable.BlockColumn blockColumn
                    : table.getBlockColumns()) {
                OmrAssessmentBlockResult blockResult = table.findResult(
                        resultRow,
                        blockColumn
                );
                String correctColumn = column(blockColumnIndex++);
                String scoreColumn = column(blockColumnIndex++);
                if (blockResult == null) {
                    xml.append(stringCell(correctColumn, row, "", 2))
                            .append(stringCell(scoreColumn, row, "", 2));
                } else {
                    xml.append(numberCell(
                                    correctColumn,
                                    row,
                                    blockResult.getCorrectCount(),
                                    2
                            ))
                            .append(numberCell(
                                    scoreColumn,
                                    row,
                                    blockResult.getCalculatedScoreAsDouble(),
                                    4
                            ));
                }
            }
            xml.append("</row>");
            row++;
        }

        int finalDataRow = Math.max(headerRow, row - 1);
        String finalColumn = column(headers.size());
        xml.append(rowStart(row, 8)).append("</row>");
        row++;
        int firstSummaryRow = row;
        xml.append(summaryRow(
                row,
                "Resultados da aplicação • Aplicação: "
                        + report.getApplication().getApplicationId()
                        + " • Prova: " + report.getAssessment().getTitle()
                        + " • Versão: "
                        + report.getAssessment().getVersion(),
                9
        ));
        row++;
        xml.append(summaryRow(
                row,
                "Disciplina: " + report.getAssessment().getSubject()
                        + " • Turma: " + report.getFrozenClassName()
                        + " • Ano letivo: "
                        + report.getAssessment().getSchoolYear()
                        + " • Gerado em: "
                        + OmrAssessmentResultExportFormatter.dateTime(
                        report.getGeneratedAtEpochMillis()),
                2
        ));
        row++;
        xml.append(summaryRow(
                row,
                "Participantes: " + report.getParticipantCount()
                        + " • Corrigidos: " + report.getCorrectedCount()
                        + " • Pendentes: " + report.getPendingCount()
                        + " • Inconsistências: "
                        + report.getDataIntegrityIssueCount()
                        + " • Média: "
                        + OmrAssessmentResultExportFormatter.decimal(
                        report.getAveragePercentage()) + "%",
                2
        ));
        int lastSummaryRow = row;
        xml.append("</sheetData>")
                .append("<autoFilter ref=\"A").append(headerRow)
                .append(":").append(finalColumn).append(finalDataRow)
                .append("\"/>")
                .append("<mergeCells count=\"3\">");
        for (int summaryRow = firstSummaryRow;
                summaryRow <= lastSummaryRow;
                summaryRow++) {
            xml.append("<mergeCell ref=\"A").append(summaryRow)
                    .append(":").append(finalColumn)
                    .append(summaryRow).append("\"/>");
        }
        xml.append("</mergeCells>")
                .append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>")
                .append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
                .append("</worksheet>");
        return xml.toString();
    }

    private static String componentsWorksheet(
            OmrAssessmentResultExportTable table
    ) {
        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews><cols>")
                .append("<col min=\"1\" max=\"1\" width=\"9\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"2\" width=\"30\" customWidth=\"1\"/>")
                .append("<col min=\"3\" max=\"3\" width=\"18\" customWidth=\"1\"/>")
                .append("<col min=\"4\" max=\"4\" width=\"32\" customWidth=\"1\"/>")
                .append("<col min=\"5\" max=\"7\" width=\"13\" customWidth=\"1\"/>")
                .append("<col min=\"8\" max=\"8\" width=\"40\" customWidth=\"1\"/>")
                .append("<col min=\"9\" max=\"9\" width=\"16\" customWidth=\"1\"/>")
                .append("</cols><sheetData>");

        String[] headers = {
                "Ordem", "Área", "Código", "Componente",
                "Questão inicial", "Questão final", "Itens",
                "Política de nota", "Nota máxima"
        };
        int row = 1;
        xml.append(rowStart(row, 32));
        for (int index = 0; index < headers.length; index++) {
            xml.append(stringCell(column(index + 1), row, headers[index], 3));
        }
        xml.append("</row>");

        for (OmrAssessmentResultExportTable.BlockColumn blockColumn
                : table.getBlockColumns()) {
            row++;
            OmrAssessmentBlock block = blockColumn.getBlock();
            xml.append(rowStart(row, 21))
                    .append(numberCell("A", row, block.getOrder(), 2))
                    .append(stringCell("B", row,
                            block.hasAreaName()
                                    ? block.getAreaName() : "", 2))
                    .append(stringCell("C", row,
                            block.hasComponentCode()
                                    ? block.getComponentCode() : "", 2))
                    .append(stringCell("D", row,
                            block.getComponentName(), 2))
                    .append(numberCell("E", row,
                            block.getFirstQuestionPosition(), 2))
                    .append(numberCell("F", row,
                            block.getLastQuestionPosition(), 2))
                    .append(numberCell("G", row,
                            block.getQuestionCount(), 2))
                    .append(stringCell("H", row,
                            blockColumn.getPolicyIdentity(), 2))
                    .append(numberCell("I", row,
                            block.getScoringPolicy()
                                    .getMaximumScore().doubleValue(), 4))
                    .append("</row>");
        }

        xml.append("</sheetData><autoFilter ref=\"A1:I")
                .append(Math.max(1, row))
                .append("\"/><pageMargins left=\"0.25\" right=\"0.25\"")
                .append(" top=\"0.5\" bottom=\"0.5\" header=\"0.2\"")
                .append(" footer=\"0.2\"/><pageSetup orientation=\"landscape\"")
                .append(" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>");
        return xml.toString();
    }

    private static String summaryRow(
            int row,
            String value,
            int style
    ) {
        return rowStart(row, 20)
                + stringCell("A", row, value, style)
                + "</row>";
    }

    private static int statusStyle(
            OmrAssessmentResultReport.Row row
    ) {
        switch (row.getDetailStatus()) {
            case FINAL:
            case ZERO:
                return 5;
            case PENDING:
            case IN_REVIEW:
                return 6;
            case ABSENT:
            case INCONSISTENT:
                return 10;
            default:
                throw new IllegalStateException(
                        "Situação de resultado desconhecida."
                );
        }
    }

    private static String rowStart(int row, int height) {
        return "<row r=\"" + row + "\" ht=\"" + height
                + "\" customHeight=\"1\">";
    }

    private static String stringCell(
            String column,
            int row,
            String value,
            int style
    ) {
        return "<c r=\"" + column + row + "\" s=\"" + style
                + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + escape(value) + "</t></is></c>";
    }

    private static String numberCell(
            String column,
            int row,
            double value,
            int style
    ) {
        return "<c r=\"" + column + row + "\" s=\"" + style
                + "\"><v>" + String.format(Locale.ROOT, "%.10f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "")
                + "</v></c>";
    }

    private static String column(int oneBasedIndex) {
        StringBuilder value = new StringBuilder();
        int current = oneBasedIndex;
        while (current > 0) {
            current--;
            value.insert(0, (char) ('A' + current % 26));
            current /= 26;
        }
        return value.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '&') {
                escaped.append("&amp;");
            } else if (character == '<') {
                escaped.append("&lt;");
            } else if (character == '>') {
                escaped.append("&gt;");
            } else if (character == '"') {
                escaped.append("&quot;");
            } else if (character == '\'') {
                escaped.append("&apos;");
            } else if (character == '\n') {
                escaped.append("&#10;");
            } else if (character == '\r') {
                escaped.append("&#13;");
            } else if (character == '\t') {
                escaped.append("&#9;");
            } else if (character >= 0x20) {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static void entry(
            ZipOutputStream zip,
            String name,
            String value
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(boolean includeComponents) {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        if (includeComponents) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append(
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "</Types>")
                .toString();
    }

    private static String rootRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook(boolean includeComponents) {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<sheets><sheet name=\"Resultados\" sheetId=\"1\" r:id=\"rId1\"/>");
        if (includeComponents) {
            xml.append("<sheet name=\"Componentes\" sheetId=\"2\" r:id=\"rId2\"/>");
        }
        return xml.append("</sheets><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>")
                .toString();
    }

    private static String workbookRelationships(
            boolean includeComponents
    ) {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                .append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>");
        if (includeComponents) {
            xml.append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>")
                    .append("<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        } else {
            xml.append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        }
        return xml.append("</Relationships>").toString();
    }

    private static String appProperties(boolean includeComponents) {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">")
                .append("<Application>Leitor Gabarito OMR</Application>")
                .append("<TitlesOfParts><vt:vector size=\"")
                .append(includeComponents ? 2 : 1)
                .append("\" baseType=\"lpstr\"><vt:lpstr>Resultados</vt:lpstr>");
        if (includeComponents) {
            xml.append("<vt:lpstr>Componentes</vt:lpstr>");
        }
        return xml.append("</vt:vector></TitlesOfParts></Properties>")
                .toString();
    }

    private static String coreProperties(OmrAssessmentResultReport report) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT
        );
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String created = format.format(new Date(
                report.getGeneratedAtEpochMillis()
        ));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>Resultados da aplicação</dc:title>"
                + "<dc:creator>Leitor Gabarito OMR</dc:creator>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">"
                + created + "</dcterms:created></cp:coreProperties>";
    }

    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"2\"><numFmt numFmtId=\"164\" formatCode=\"0.00\"/><numFmt numFmtId=\"165\" formatCode=\"0.00%\"/></numFmts>"
                + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"18\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"7\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F2937\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2563EB\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCFCE7\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFEF3C7\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFEE2E2\"/></patternFill></fill></fills>"
                + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"><color rgb=\"FFD1D5DB\"/></left><right style=\"thin\"><color rgb=\"FFD1D5DB\"/></right><top style=\"thin\"><color rgb=\"FFD1D5DB\"/></top><bottom style=\"thin\"><color rgb=\"FFD1D5DB\"/></bottom><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"11\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"><alignment vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"4\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"><alignment wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"6\" borderId=\"1\" xfId=\"0\" applyFill=\"1\" applyBorder=\"1\"><alignment wrapText=\"1\"/></xf>"
                + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }
}
