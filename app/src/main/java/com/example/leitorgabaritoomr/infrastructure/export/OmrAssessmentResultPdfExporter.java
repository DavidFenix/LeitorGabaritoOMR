package com.example.leitorgabaritoomr.infrastructure.export;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.leitorgabaritoomr.application.assessment.OmrAssessmentResultReport;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlockResult;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/** Exporta um relatório paginado em PDF A4 paisagem. */
public final class OmrAssessmentResultPdfExporter {

    private static final int PAGE_WIDTH = 842;
    private static final int PAGE_HEIGHT = 595;
    private static final float LEFT = 28f;
    private static final float TABLE_TOP = 142f;
    private static final float ROW_HEIGHT = 22f;
    private static final int ROWS_PER_PAGE = 18;

    public void write(
            OmrAssessmentResultReport report,
            OutputStream output
    ) throws IOException {
        if (report == null || output == null) {
            throw new IllegalArgumentException(
                    "O relatório e o destino são obrigatórios."
            );
        }

        PdfDocument document = new PdfDocument();
        try {
            List<OmrAssessmentResultReport.Row> rows = report.getRows();
            OmrAssessmentResultExportTable table =
                    OmrAssessmentResultExportTable.from(report);
            int rosterPageCount = Math.max(
                    1,
                    (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE
            );
            int pageCount = rosterPageCount
                    * (1 + table.getBlockCount());
            for (int pageNumber = 1;
                 pageNumber <= rosterPageCount;
                 pageNumber++) {
                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH,
                        PAGE_HEIGHT,
                        pageNumber
                ).create();
                PdfDocument.Page page = document.startPage(info);
                drawGeneralPage(
                        page.getCanvas(),
                        report,
                        pageNumber,
                        pageCount,
                        (pageNumber - 1) * ROWS_PER_PAGE,
                        Math.min(pageNumber * ROWS_PER_PAGE, rows.size())
                );
                document.finishPage(page);
            }
            int pageNumber = rosterPageCount + 1;
            for (OmrAssessmentResultExportTable.BlockColumn blockColumn
                    : table.getBlockColumns()) {
                for (int blockPage = 0;
                     blockPage < rosterPageCount;
                     blockPage++) {
                    PdfDocument.PageInfo info =
                            new PdfDocument.PageInfo.Builder(
                                    PAGE_WIDTH,
                                    PAGE_HEIGHT,
                                    pageNumber
                            ).create();
                    PdfDocument.Page page = document.startPage(info);
                    drawComponentPage(
                            page.getCanvas(),
                            report,
                            table,
                            blockColumn,
                            pageNumber,
                            pageCount,
                            blockPage * ROWS_PER_PAGE,
                            Math.min(
                                    (blockPage + 1) * ROWS_PER_PAGE,
                                    rows.size()
                            )
                    );
                    document.finishPage(page);
                    pageNumber++;
                }
            }
            document.writeTo(output);
            output.flush();
        } finally {
            document.close();
        }
    }

    private static void drawGeneralPage(
            Canvas canvas,
            OmrAssessmentResultReport report,
            int pageNumber,
            int pageCount,
            int from,
            int to
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(31, 41, 55));
        canvas.drawRect(0, 0, PAGE_WIDTH, 76, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(20f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Resultados da aplicação", LEFT, 31f, paint);
        paint.setTextSize(11f);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(shorten(
                report.getAssessment().getTitle() + " • "
                        + report.getAssessment().getSubject() + " • prova v"
                        + report.getAssessment().getVersion() + " • "
                        + report.getFrozenClassName(), 105
        ), LEFT, 53f, paint);

        paint.setColor(Color.rgb(17, 24, 39));
        paint.setTextSize(10f);
        canvas.drawText(
                "Aplicação: " + report.getApplication().getApplicationId(),
                LEFT, 94f, paint
        );
        canvas.drawText(
                String.format(
                        Locale.ROOT,
                        "Participantes: %d   Corrigidos: %d   Pendentes: %d"
                                + "   Inconsistências: %d   Média: %.2f%%",
                        report.getParticipantCount(),
                        report.getCorrectedCount(),
                        report.getPendingCount(),
                        report.getDataIntegrityIssueCount(),
                        report.getAveragePercentage()
                ),
                LEFT, 113f, paint
        );
        canvas.drawText(
                "Gerado em: "
                        + OmrAssessmentResultExportFormatter.dateTime(
                        report.getGeneratedAtEpochMillis()),
                LEFT, 129f, paint
        );

        float[] widths = {38f, 88f, 215f, 60f, 72f, 68f, 160f};
        String[] headers = {
                "Pos.", "Matrícula", "Aluno", "Revisão",
                "Nota", "%", "Situação"
        };
        paint.setColor(Color.rgb(37, 99, 235));
        canvas.drawRect(
                LEFT, TABLE_TOP, PAGE_WIDTH - LEFT,
                TABLE_TOP + ROW_HEIGHT, paint
        );
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(9f);
        float x = LEFT;
        for (int index = 0; index < headers.length; index++) {
            canvas.drawText(headers[index], x + 4f,
                    TABLE_TOP + 15f, paint);
            x += widths[index];
        }

        paint.setTypeface(Typeface.DEFAULT);
        for (int rowIndex = from; rowIndex < to; rowIndex++) {
            OmrAssessmentResultReport.Row resultRow =
                    report.getRows().get(rowIndex);
            OmrAssessmentParticipant participant =
                    resultRow.getParticipant();
            float top = TABLE_TOP + ROW_HEIGHT
                    + (rowIndex - from) * ROW_HEIGHT;
            paint.setColor((rowIndex - from) % 2 == 0
                    ? Color.rgb(249, 250, 251)
                    : Color.WHITE);
            canvas.drawRect(
                    LEFT, top, PAGE_WIDTH - LEFT,
                    top + ROW_HEIGHT, paint
            );
            String[] values = {
                    String.valueOf(participant.getRosterPosition()),
                    shorten(participant.getRegistration(), 16),
                    shorten(participant.getStudentName(), 35),
                    resultRow.getRevision() == 0
                            ? "—" : String.valueOf(resultRow.getRevision()),
                    resultRow.isCorrected()
                            ? String.format(Locale.ROOT, "%.2f/%.2f",
                            resultRow.getAwardedPoints(),
                            resultRow.getPossiblePoints()) : "—",
                    resultRow.isCorrected()
                            ? String.format(Locale.ROOT, "%.2f",
                            resultRow.getAwardedPercentage()) : "—",
                    shorten(OmrAssessmentResultExportFormatter.status(
                            resultRow), 29)
            };
            paint.setColor(Color.rgb(31, 41, 55));
            paint.setTextSize(8.5f);
            x = LEFT;
            for (int index = 0; index < values.length; index++) {
                canvas.drawText(values[index], x + 4f, top + 15f, paint);
                x += widths[index];
            }
        }

        paint.setColor(Color.rgb(107, 114, 128));
        paint.setTextSize(9f);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(
                "Página " + pageNumber + " de " + pageCount,
                PAGE_WIDTH - LEFT,
                PAGE_HEIGHT - 18f,
                paint
        );
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static void drawComponentPage(
            Canvas canvas,
            OmrAssessmentResultReport report,
            OmrAssessmentResultExportTable table,
            OmrAssessmentResultExportTable.BlockColumn blockColumn,
            int pageNumber,
            int pageCount,
            int from,
            int to
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(31, 41, 55));
        canvas.drawRect(0, 0, PAGE_WIDTH, 76, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(20f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Resultados por componente", LEFT, 31f, paint);
        paint.setTextSize(11f);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(shorten(
                report.getAssessment().getTitle() + " • prova v"
                        + report.getAssessment().getVersion() + " • "
                        + report.getFrozenClassName(),
                105
        ), LEFT, 53f, paint);

        OmrAssessmentBlock block = blockColumn.getBlock();
        paint.setColor(Color.rgb(17, 24, 39));
        paint.setTextSize(11f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(
                shorten(blockColumn.getLabel(), 105),
                LEFT,
                94f,
                paint
        );
        paint.setTextSize(9.5f);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(
                "Questões " + blockColumn.getQuestionRange()
                        + " • Política "
                        + blockColumn.getPolicyIdentity()
                        + " • Nota máxima "
                        + localized(block.getScoringPolicy()
                        .getMaximumScore().toPlainString()),
                LEFT,
                113f,
                paint
        );
        canvas.drawText(
                "Aplicação: " + report.getApplication().getApplicationId()
                        + " • Gerado em: "
                        + OmrAssessmentResultExportFormatter.dateTime(
                        report.getGeneratedAtEpochMillis()),
                LEFT,
                129f,
                paint
        );

        float[] widths = {38f, 88f, 235f, 72f, 82f, 186f};
        String[] headers = {
                "Pos.", "Matrícula", "Aluno", "Acertos",
                "Nota", "Situação"
        };
        paint.setColor(Color.rgb(37, 99, 235));
        canvas.drawRect(
                LEFT,
                TABLE_TOP,
                PAGE_WIDTH - LEFT,
                TABLE_TOP + ROW_HEIGHT,
                paint
        );
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(9f);
        float x = LEFT;
        for (int index = 0; index < headers.length; index++) {
            canvas.drawText(
                    headers[index],
                    x + 4f,
                    TABLE_TOP + 15f,
                    paint
            );
            x += widths[index];
        }

        paint.setTypeface(Typeface.DEFAULT);
        for (int rowIndex = from; rowIndex < to; rowIndex++) {
            OmrAssessmentResultReport.Row resultRow =
                    report.getRows().get(rowIndex);
            OmrAssessmentParticipant participant =
                    resultRow.getParticipant();
            OmrAssessmentBlockResult blockResult = table.findResult(
                    resultRow,
                    blockColumn
            );
            float top = TABLE_TOP + ROW_HEIGHT
                    + (rowIndex - from) * ROW_HEIGHT;
            paint.setColor((rowIndex - from) % 2 == 0
                    ? Color.rgb(249, 250, 251)
                    : Color.WHITE);
            canvas.drawRect(
                    LEFT,
                    top,
                    PAGE_WIDTH - LEFT,
                    top + ROW_HEIGHT,
                    paint
            );
            String[] values = {
                    String.valueOf(participant.getRosterPosition()),
                    shorten(participant.getRegistration(), 16),
                    shorten(participant.getStudentName(), 38),
                    blockResult == null
                            ? "—"
                            : blockResult.getCorrectCount()
                            + "/" + blockResult.getQuestionCount(),
                    blockResult == null
                            ? "—"
                            : localized(blockResult.getCalculatedScore()
                            .toPlainString()) + "/"
                            + localized(blockResult.getMaximumScore()
                            .toPlainString()),
                    shorten(componentStatus(resultRow, blockResult), 34)
            };
            paint.setColor(Color.rgb(31, 41, 55));
            paint.setTextSize(8.5f);
            x = LEFT;
            for (int index = 0; index < values.length; index++) {
                canvas.drawText(values[index], x + 4f, top + 15f, paint);
                x += widths[index];
            }
        }

        paint.setColor(Color.rgb(107, 114, 128));
        paint.setTextSize(9f);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(
                "Página " + pageNumber + " de " + pageCount,
                PAGE_WIDTH - LEFT,
                PAGE_HEIGHT - 18f,
                paint
        );
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static String componentStatus(
            OmrAssessmentResultReport.Row row,
            OmrAssessmentBlockResult result
    ) {
        if (result == null) {
            return OmrAssessmentResultExportFormatter.status(row);
        }
        if (result.requiresReview()) {
            return "Provisório - requer revisão";
        }
        return result.getCalculatedScore().signum() == 0
                ? "Final - nota zero"
                : "Final";
    }

    private static String localized(String value) {
        return value == null ? "" : value.replace('.', ',');
    }

    private static String shorten(String value, int maxCharacters) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxCharacters) {
            return value;
        }
        return value.substring(0, Math.max(1, maxCharacters - 1)) + "…";
    }
}
