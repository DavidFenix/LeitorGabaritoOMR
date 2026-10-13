package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
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

        ZipOutputStream zip = new ZipOutputStream(output);
        entry(zip, "[Content_Types].xml", contentTypes());
        entry(zip, "_rels/.rels", rootRelationships());
        entry(zip, "docProps/app.xml", appProperties());
        entry(zip, "docProps/core.xml", coreProperties(report));
        entry(zip, "xl/workbook.xml", workbook());
        entry(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
        entry(zip, "xl/styles.xml", styles());
        entry(zip, "xl/worksheets/sheet1.xml", worksheet(report));
        zip.finish();
        zip.flush();
    }

    private static String worksheet(OmrAssessmentResultReport report) {
        StringBuilder xml = new StringBuilder(16384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\">")
                .append("<pane ySplit=\"15\" topLeftCell=\"A16\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("</sheetView></sheetViews>")
                .append("<cols>")
                .append("<col min=\"1\" max=\"1\" width=\"10\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"2\" width=\"18\" customWidth=\"1\"/>")
                .append("<col min=\"3\" max=\"3\" width=\"34\" customWidth=\"1\"/>")
                .append("<col min=\"4\" max=\"4\" width=\"24\" customWidth=\"1\"/>")
                .append("<col min=\"5\" max=\"5\" width=\"31\" customWidth=\"1\"/>")
                .append("<col min=\"6\" max=\"13\" width=\"13\" customWidth=\"1\"/>")
                .append("<col min=\"14\" max=\"14\" width=\"22\" customWidth=\"1\"/>")
                .append("</cols><sheetData>");

        int row = 1;
        xml.append(rowStart(row, 28)).append(stringCell("A", row,
                "Resultados da aplicação", 1)).append("</row>");
        row++;
        row = metadata(xml, row, "Aplicação",
                report.getApplication().getApplicationId());
        row = metadata(xml, row, "Prova",
                report.getAssessment().getTitle());
        row = metadata(xml, row, "Versão",
                String.valueOf(report.getAssessment().getVersion()));
        row = metadata(xml, row, "Disciplina",
                report.getAssessment().getSubject());
        row = metadata(xml, row, "Turma", report.getFrozenClassName());
        row = metadata(xml, row, "Ano letivo",
                String.valueOf(report.getAssessment().getSchoolYear()));
        row = metadata(xml, row, "Gerado em",
                OmrAssessmentResultExportFormatter.dateTime(
                        report.getGeneratedAtEpochMillis()));
        row = metadata(xml, row, "Participantes",
                String.valueOf(report.getParticipantCount()));
        row = metadata(xml, row, "Corrigidos",
                String.valueOf(report.getCorrectedCount()));
        row = metadata(xml, row, "Pendentes",
                String.valueOf(report.getPendingCount()));
        row = metadata(xml, row, "Inconsistências",
                String.valueOf(report.getMissingHistoryCount()));
        row = metadata(xml, row, "Média (%)",
                OmrAssessmentResultExportFormatter.decimal(
                        report.getAveragePercentage()));
        xml.append(rowStart(row, 8)).append("</row>");
        row++;

        String[] headers = {
                "Posição", "Matrícula", "Aluno", "ID do aluno",
                "Situação", "Revisão", "Pontos", "Total", "Percentual",
                "Acertos", "Erros", "Em branco", "Para revisão",
                "Registrado em"
        };
        xml.append(rowStart(row, 24));
        for (int index = 0; index < headers.length; index++) {
            xml.append(stringCell(column(index + 1), row, headers[index], 3));
        }
        xml.append("</row>");
        int headerRow = row;
        row++;

        for (OmrAssessmentResultReport.Row resultRow : report.getRows()) {
            OmrAssessmentParticipant participant = resultRow.getParticipant();
            int statusStyle = resultRow.getStatus()
                    == OmrAssessmentResultReport.Status.CORRECTED ? 5
                    : resultRow.getStatus()
                    == OmrAssessmentResultReport.Status.PENDING ? 6 : 7;
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
                                    resultRow.getRecordedAtEpochMillis()), 2))
                    .append("</row>");
            row++;
        }

        int finalRow = Math.max(headerRow, row - 1);
        xml.append("</sheetData>")
                .append("<autoFilter ref=\"A").append(headerRow)
                .append(":N").append(finalRow).append("\"/>")
                .append("<mergeCells count=\"1\"><mergeCell ref=\"A1:N1\"/></mergeCells>")
                .append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>")
                .append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
                .append("</worksheet>");
        return xml.toString();
    }

    private static int metadata(
            StringBuilder xml,
            int row,
            String label,
            String value
    ) {
        xml.append(rowStart(row, 20))
                .append(stringCell("A", row, label, 9))
                .append(stringCell("B", row, value, 2))
                .append("</row>");
        return row + 1;
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

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
                + "</Types>";
    }

    private static String rootRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Resultados\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "<calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>";
    }

    private static String workbookRelationships() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String appProperties() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">"
                + "<Application>Leitor Gabarito OMR</Application>"
                + "<TitlesOfParts><vt:vector size=\"1\" baseType=\"lpstr\"><vt:lpstr>Resultados</vt:lpstr></vt:vector></TitlesOfParts>"
                + "</Properties>";
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
                + "<fills count=\"6\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F2937\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF2563EB\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCFCE7\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFFEF3C7\"/></patternFill></fill></fills>"
                + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"><color rgb=\"FFD1D5DB\"/></left><right style=\"thin\"><color rgb=\"FFD1D5DB\"/></right><top style=\"thin\"><color rgb=\"FFD1D5DB\"/></top><bottom style=\"thin\"><color rgb=\"FFD1D5DB\"/></bottom><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"10\">"
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
                + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }
}
