package com.example.leitorgabaritoomr.vision.debug;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public final class VisionDebugController
        implements VisionDebugSink {

    private static final Scalar LABEL_BACKGROUND =
            new Scalar(0, 0, 0, 210);

    private static final Scalar LABEL_FOREGROUND =
            new Scalar(255, 255, 255, 255);

    private volatile VisionStage selectedStage =
            VisionStage.ORIGINAL;

    /*
     * Contém somente a etapa selecionada do frame atual.
     * Não guardamos todas as etapas durante o vídeo.
     */
    private final Mat selectedFrame =
            new Mat();

    private boolean selectedFrameAvailable = false;

    public void beginFrame() {
        selectedFrameAvailable = false;
    }

    @Override
    public void publish(
            VisionStage stage,
            Mat image
    ) {

        if (stage == null
                || image == null
                || image.empty()) {

            return;
        }

        if (stage != selectedStage) {
            return;
        }

        /*
         * Convertemos todas as visualizações para RGBA,
         * que é o formato usado pelo JavaCameraView.
         */
        if (image.channels() == 4) {

            image.copyTo(selectedFrame);

        } else if (image.channels() == 3) {

            Imgproc.cvtColor(
                    image,
                    selectedFrame,
                    Imgproc.COLOR_RGB2RGBA
            );

        } else if (image.channels() == 1) {

            Imgproc.cvtColor(
                    image,
                    selectedFrame,
                    Imgproc.COLOR_GRAY2RGBA
            );

        } else {

            selectedFrameAvailable = false;
            return;
        }

        selectedFrameAvailable = true;
    }

    public boolean renderSelectedStage(
            Mat rgbaOutput
    ) {

        if (rgbaOutput == null
                || rgbaOutput.empty()
                || !selectedFrameAvailable
                || selectedFrame.empty()) {

            return false;
        }

        selectedFrame.copyTo(rgbaOutput);

        drawStageLabel(rgbaOutput);

        return true;
    }

    public VisionStage selectNext() {

        VisionStage[] stages =
                VisionStage.values();

        int nextIndex =
                (selectedStage.ordinal() + 1)
                        % stages.length;

        selectedStage =
                stages[nextIndex];

        return selectedStage;
    }

    public VisionStage selectPrevious() {

        VisionStage[] stages =
                VisionStage.values();

        int previousIndex =
                selectedStage.ordinal() - 1;

        if (previousIndex < 0) {
            previousIndex = stages.length - 1;
        }

        selectedStage =
                stages[previousIndex];

        return selectedStage;
    }

    public VisionStage getSelectedStage() {
        return selectedStage;
    }

    public void release() {

        selectedFrame.release();
        selectedFrameAvailable = false;
    }

    private void drawStageLabel(
            Mat rgbaOutput
    ) {

        String label =
                selectedStage.getDisplayName();

        int left = 18;
        int top = 18;
        int right =
                Math.min(
                        rgbaOutput.cols() - 18,
                        430
                );

        int bottom = 68;

        Imgproc.rectangle(
                rgbaOutput,
                new Point(left, top),
                new Point(right, bottom),
                LABEL_BACKGROUND,
                -1
        );

        Imgproc.putText(
                rgbaOutput,
                label,
                new Point(32, 52),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.75,
                LABEL_FOREGROUND,
                2
        );
    }
}