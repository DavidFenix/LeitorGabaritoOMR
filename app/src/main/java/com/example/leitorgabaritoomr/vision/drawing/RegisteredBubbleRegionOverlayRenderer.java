package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.registration.BubbleBlockRegistration;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegion;
import com.example.leitorgabaritoomr.vision.registration.RegisteredBubbleRegionSet;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;
import java.util.Locale;

/**
 * Desenha as regioes finais que serao entregues ao medidor.
 *
 * Este renderizador nao recebe layout, alvos esperados ou
 * transformacoes separadas. Portanto, ele nao possui informacao
 * suficiente para recalcular ou corrigir coordenadas.
 *
 * Amarelo = poligono final da bolha.
 * Ciano   = centro final armazenado na regiao.
 * Magenta = primeiro canto do primeiro alvo de cada bloco.
 * Vermelho = regiao cortada pelo limite da imagem.
 *
 * O poligono amarelo e exatamente o mesmo que sera consultado pelo
 * medidor atraves de RegisteredBubbleRegion.containsPixelCenter().
 */
public final class RegisteredBubbleRegionOverlayRenderer {

    private static final Scalar REGION_COLOR =
            new Scalar(255.0, 255.0, 0.0, 255.0);

    private static final Scalar CENTER_COLOR =
            new Scalar(0.0, 255.0, 255.0, 255.0);

    private static final Scalar BLOCK_ANCHOR_COLOR =
            new Scalar(255.0, 0.0, 255.0, 255.0);

    private static final Scalar CLIPPED_REGION_COLOR =
            new Scalar(255.0, 0.0, 0.0, 255.0);

    private static final Scalar SUMMARY_COLOR =
            new Scalar(0.0, 255.0, 0.0, 255.0);

    private static final Scalar FAILURE_COLOR =
            new Scalar(255.0, 0.0, 0.0, 255.0);

    public void draw(
            Mat normalizedRegion,
            RegisteredBubbleRegionSet regionSet
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()) {

            return;
        }

        String validationError =
                validateInput(
                        normalizedRegion,
                        regionSet
                );

        if (validationError != null) {
            drawFailure(
                    normalizedRegion,
                    validationError
            );

            return;
        }

        drawRegions(
                normalizedRegion,
                regionSet
        );

        drawBlockLabels(
                normalizedRegion,
                regionSet
        );

        drawSummary(
                normalizedRegion,
                regionSet
        );
    }

    private String validateInput(
            Mat image,
            RegisteredBubbleRegionSet regionSet
    ) {
        if (regionSet == null) {
            return "regioes finais indisponiveis";
        }

        if (!regionSet.isComplete()) {
            return "conjunto de regioes incompleto";
        }

        if (image.cols() != regionSet.getImageWidth()
                || image.rows()
                != regionSet.getImageHeight()) {

            return "imagem e regioes possuem tamanhos diferentes";
        }

        return null;
    }

    private void drawRegions(
            Mat image,
            RegisteredBubbleRegionSet regionSet
    ) {
        for (RegisteredBubbleRegion region
                : regionSet.getRegions()) {

            Scalar color =
                    region.isClippedByImage()
                            ? CLIPPED_REGION_COLOR
                            : REGION_COLOR;

            drawPolygon(
                    image,
                    region,
                    color,
                    region.isClippedByImage()
                            ? 2
                            : 1
            );

            Imgproc.circle(
                    image,
                    new Point(
                            region.getCenterX(),
                            region.getCenterY()
                    ),
                    2,
                    CENTER_COLOR,
                    -1
            );
        }
    }

    /**
     * Usa diretamente os quatro cantos armazenados. Nao aplica
     * arredondamento, escala, deslocamento nem consulta o layout.
     */
    private void drawPolygon(
            Mat image,
            RegisteredBubbleRegion region,
            Scalar color,
            int thickness
    ) {
        for (int cornerIndex = 0;
             cornerIndex < region.getCornerCount();
             cornerIndex++) {

            int nextCornerIndex =
                    (cornerIndex + 1)
                            % region.getCornerCount();

            Imgproc.line(
                    image,
                    pointAt(region, cornerIndex),
                    pointAt(region, nextCornerIndex),
                    color,
                    thickness
            );
        }
    }

    private Point pointAt(
            RegisteredBubbleRegion region,
            int cornerIndex
    ) {
        return new Point(
                region.getCornerX(cornerIndex),
                region.getCornerY(cornerIndex)
        );
    }

    private void drawBlockLabels(
            Mat image,
            RegisteredBubbleRegionSet regionSet
    ) {
        for (BubbleBlockRegistration registration
                : regionSet
                .getRegistrationResult()
                .getBlockRegistrations()) {

            List<RegisteredBubbleRegion> blockRegions =
                    regionSet.getRegionsForBlock(
                            registration.getBlockIndex()
                    );

            if (blockRegions.isEmpty()) {
                continue;
            }

            RegisteredBubbleRegion firstRegion =
                    blockRegions.get(0);

            Point labelAnchor =
                    findBlockLabelAnchor(
                            blockRegions
                    );

            String text = String.format(
                    Locale.US,
                    "B%d regioes=%d tam=%.1fx%.1f",
                    registration.getBlockIndex() + 1,
                    blockRegions.size(),
                    firstRegion.getNominalWidth(),
                    firstRegion.getNominalHeight()
            );

            Imgproc.putText(
                    image,
                    text,
                    labelAnchor,
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.30,
                    REGION_COLOR,
                    1
            );

            Imgproc.circle(
                    image,
                    pointAt(
                            firstRegion,
                            RegisteredBubbleRegion.TOP_LEFT
                    ),
                    3,
                    BLOCK_ANCHOR_COLOR,
                    -1
            );
        }
    }

    private Point findBlockLabelAnchor(
            List<RegisteredBubbleRegion> regions
    ) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;

        for (RegisteredBubbleRegion region : regions) {
            for (int cornerIndex = 0;
                 cornerIndex < region.getCornerCount();
                 cornerIndex++) {

                minimumX = Math.min(
                        minimumX,
                        region.getCornerX(cornerIndex)
                );

                minimumY = Math.min(
                        minimumY,
                        region.getCornerY(cornerIndex)
                );
            }
        }

        return new Point(
                Math.max(2.0, minimumX),
                Math.max(12.0, minimumY - 8.0)
        );
    }

    private void drawSummary(
            Mat image,
            RegisteredBubbleRegionSet regionSet
    ) {
        String text = String.format(
                Locale.US,
                "regioes finais | %d/%d | blocos=%d"
                        + " | cortadas=%d | tam=%.1fx%.1f"
                        + " | conf=%.3f",
                regionSet.getRegionCount(),
                regionSet
                        .getRegistrationResult()
                        .getTargetCount(),
                regionSet.getBlockCount(),
                regionSet.getClippedRegionCount(),
                regionSet.getMeanNominalWidth(),
                regionSet.getMeanNominalHeight(),
                regionSet
                        .getRegistrationResult()
                        .getSheetConfidence()
        );

        Imgproc.putText(
                image,
                text,
                new Point(
                        12.0,
                        Math.max(
                                24.0,
                                image.rows() - 16.0
                        )
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.43,
                regionSet.hasClippedRegions()
                        ? FAILURE_COLOR
                        : SUMMARY_COLOR,
                2
        );
    }

    private void drawFailure(
            Mat image,
            String message
    ) {
        Imgproc.putText(
                image,
                message,
                new Point(
                        12.0,
                        Math.max(
                                24.0,
                                image.rows() - 16.0
                        )
                ),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.48,
                FAILURE_COLOR,
                2
        );
    }
}
