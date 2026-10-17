package com.example.leitorgabaritoomr.infrastructure.export;

import com.example.leitorgabaritoomr.application.grading.OmrAnswerKeyPngZipPlan;
import com.example.leitorgabaritoomr.application.layout.OmrPublishedLayoutResolver;
import com.example.leitorgabaritoomr.vision.layout.template.OmrSheetTemplateSpec;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gera um ZIP de cartões-resposta PNG a partir de um plano já validado.
 *
 * Cada cartão é gerado e gravado antes de iniciar o seguinte. Assim, o lote
 * nunca mantém todos os bitmaps ou PNGs na memória. O OutputStream recebido
 * pertence ao chamador e não é fechado por este componente.
 */
public final class OmrAnswerKeyPngZipExporter {

    public static final String MIME_TYPE = "application/zip";
    public static final String DEFAULT_ARCHIVE_FILE_NAME =
            "cartoes-resposta-omr.zip";

    private final OmrPublishedLayoutResolver layoutResolver;
    private final OmrSheetSvgGenerator svgGenerator;
    private final OmrSheetPngRenderer pngRenderer;

    public OmrAnswerKeyPngZipExporter() {
        this(
                new OmrPublishedLayoutResolver(),
                new OmrSheetSvgGenerator(),
                new OmrSheetPngRenderer()
        );
    }

    OmrAnswerKeyPngZipExporter(
            OmrPublishedLayoutResolver layoutResolver,
            OmrSheetSvgGenerator svgGenerator,
            OmrSheetPngRenderer pngRenderer
    ) {
        if (layoutResolver == null
                || svgGenerator == null
                || pngRenderer == null) {
            throw new IllegalArgumentException(
                    "Os componentes da exportação ZIP são obrigatórios."
            );
        }
        this.layoutResolver = layoutResolver;
        this.svgGenerator = svgGenerator;
        this.pngRenderer = pngRenderer;
    }

    public ExportSummary write(
            OmrAnswerKeyPngZipPlan plan,
            OutputStream destination
    ) throws IOException {
        if (plan == null || !plan.isReadyToExport()) {
            throw new IllegalArgumentException(
                    "O plano precisa estar válido para gerar o ZIP."
            );
        }
        if (destination == null) {
            throw new IllegalArgumentException(
                    "O destino do ZIP é obrigatório."
            );
        }

        List<String> entryNames = new ArrayList<>(
                plan.getCardCount()
        );

        try (ZipOutputStream zip = new ZipOutputStream(
                new NonClosingOutputStream(destination)
        )) {
            for (OmrAnswerKeyPngZipPlan.Card card : plan.getCards()) {
                ensureNotInterrupted();

                OmrSheetTemplateSpec spec = layoutResolver
                        .resolveTemplateForAnswerKey(
                                card.getAnswerKeyDefinition()
                        );

                OmrSheetTextCustomization customization =
                        OmrSheetTextCustomization.fromOverrides(
                                card.getHeaderOverride(),
                                card.getInstructionOverride(),
                                card.getFooterOverride()
                        );

                OmrSheetSvgDocument svg = svgGenerator.generate(
                        spec,
                        customization
                );

                ZipEntry entry = new ZipEntry(
                        card.getOutputFileName()
                );
                entry.setTime(0L);
                zip.putNextEntry(entry);
                try {
                    pngRenderer.write(svg, zip);
                } finally {
                    zip.closeEntry();
                }

                entryNames.add(card.getOutputFileName());
            }

            zip.finish();
            zip.flush();
        }

        destination.flush();
        return new ExportSummary(entryNames);
    }

    private static void ensureNotInterrupted()
            throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException(
                    "A geração do lote de cartões foi interrompida."
            );
        }
    }

    public static final class ExportSummary {
        private final List<String> entryNames;

        private ExportSummary(List<String> entryNames) {
            this.entryNames = Collections.unmodifiableList(
                    new ArrayList<>(entryNames)
            );
        }

        public int getExportedCount() {
            return entryNames.size();
        }

        public List<String> getEntryNames() {
            return entryNames;
        }
    }

    private static final class NonClosingOutputStream
            extends FilterOutputStream {

        private NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
