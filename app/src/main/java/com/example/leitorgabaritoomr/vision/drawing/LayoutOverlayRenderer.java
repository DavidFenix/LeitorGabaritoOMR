package com.example.leitorgabaritoomr.vision.drawing;

import com.example.leitorgabaritoomr.vision.layout.OmrBlockDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrLayoutDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrOptionDefinition;
import com.example.leitorgabaritoomr.vision.layout.OmrQuestionDefinition;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Desenha sobre a região normalizada as posições em que o
 * layout espera encontrar as bolhas.
 *
 * Cada bloco recebe uma cor diferente.
 */
public final class LayoutOverlayRenderer {

    private static final Scalar[] BLOCK_COLORS = {
            new Scalar(0, 255, 0, 255),
            new Scalar(0, 255, 255, 255),
            new Scalar(255, 0, 255, 255),
            new Scalar(255, 165, 0, 255)
    };

    private static final Scalar CENTER_COLOR =
            new Scalar(255, 255, 255, 255);

    public void draw(
            Mat normalizedRegion,
            OmrLayoutDefinition layout
    ) {
        if (normalizedRegion == null
                || normalizedRegion.empty()
                || layout == null) {

            return;
        }

        int blockIndex = 0;

        for (OmrBlockDefinition block
                : layout.getBlocks()) {

            Scalar blockColor =
                    BLOCK_COLORS[
                            blockIndex
                                    % BLOCK_COLORS.length
                            ];

            drawBlock(
                    normalizedRegion,
                    block,
                    blockColor
            );

            blockIndex++;
        }
    }

    private void drawBlock(
            Mat image,
            OmrBlockDefinition block,
            Scalar color
    ) {
        for (OmrQuestionDefinition question
                : block.getQuestions()) {

            for (OmrOptionDefinition option
                    : question.getOptions()) {

                drawOptionRegion(
                        image,
                        option,
                        color
                );
            }
        }
    }

    private void drawOptionRegion(
            Mat image,
            OmrOptionDefinition option,
            Scalar color
    ) {
        int left = normalizedXToPixel(
                option.getLeft(),
                image.cols()
        );

        int top = normalizedYToPixel(
                option.getTop(),
                image.rows()
        );

        int right = normalizedXToPixel(
                option.getRight(),
                image.cols()
        );

        int bottom = normalizedYToPixel(
                option.getBottom(),
                image.rows()
        );

        Point topLeft =
                new Point(left, top);

        Point bottomRight =
                new Point(right, bottom);

        Imgproc.rectangle(
                image,
                topLeft,
                bottomRight,
                color,
                2
        );

        int centerX = normalizedXToPixel(
                option.getCenter().getX(),
                image.cols()
        );

        int centerY = normalizedYToPixel(
                option.getCenter().getY(),
                image.rows()
        );

        Imgproc.circle(
                image,
                new Point(centerX, centerY),
                2,
                CENTER_COLOR,
                -1
        );
    }

    private int normalizedXToPixel(
            double normalizedX,
            int width
    ) {
        int pixel = (int) Math.round(
                normalizedX * (width - 1.0)
        );

        return clamp(
                pixel,
                0,
                width - 1
        );
    }

    private int normalizedYToPixel(
            double normalizedY,
            int height
    ) {
        int pixel = (int) Math.round(
                normalizedY * (height - 1.0)
        );

        return clamp(
                pixel,
                0,
                height - 1
        );
    }

    private int clamp(
            int value,
            int minimum,
            int maximum
    ) {
        return Math.max(
                minimum,
                Math.min(value, maximum)
        );
    }
}