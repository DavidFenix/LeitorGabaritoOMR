package com.example.leitorgabaritoomr.vision.debug;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public final class VisionDebugController
        implements VisionDebugSink {

    /*
     * Opção exclusiva do Laboratório OMR.
     *
     * Não representa captura definitiva nem regra
     * do motor de leitura.
     */
    private volatile boolean autoFreezeOnStableEnabled = true;

    private static final Scalar LABEL_BACKGROUND =
            new Scalar(0, 0, 0, 210);

    private static final Scalar LABEL_FOREGROUND =
            new Scalar(255, 255, 255, 255);

    private volatile VisionStage selectedStage =
            VisionStage.ORIGINAL;

    private volatile boolean frozen = false;

    /*
     * Guarda somente a etapa selecionada.
     *
     * Quando frozen=true, esse Mat permanece intacto
     * até o usuário continuar o processamento.
     */
    private final Mat selectedFrame =
            new Mat();

    private boolean selectedFrameAvailable = false;

    public synchronized boolean freeze() {

        if (!selectedFrameAvailable
                || selectedFrame.empty()) {

            return false;
        }

        frozen = true;

        return true;
    }

    public boolean isAutoFreezeOnStableEnabled() {
        return autoFreezeOnStableEnabled;
    }

    public void setAutoFreezeOnStableEnabled(
            boolean enabled
    ) {

        autoFreezeOnStableEnabled = enabled;
    }

    public synchronized void beginFrame() {

        if (frozen) {
            return;
        }

        selectedFrameAvailable = false;
    }

    @Override
    public synchronized void publish(
            VisionStage stage,
            Mat image
    ) {

        if (frozen) {
            return;
        }

        if (stage == null
                || image == null
                || image.empty()) {

            return;
        }

        if (stage != selectedStage) {
            return;
        }

        /*
         * Padroniza a imagem exibida em RGBA.
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

    public synchronized boolean renderSelectedStage(
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

    public synchronized boolean toggleFreeze() {

        /*
         * Não é possível pausar antes de existir
         * uma imagem válida para ser conservada.
         */
        if (!selectedFrameAvailable
                || selectedFrame.empty()) {

            frozen = false;

            return false;
        }

        frozen = !frozen;

        return frozen;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public synchronized VisionStage selectNext() {

        /*
         * Como guardamos somente uma etapa, não permitimos
         * mudar de etapa enquanto a imagem está congelada.
         */
        if (frozen) {
            return selectedStage;
        }

        VisionStage[] stages =
                VisionStage.values();

        int nextIndex =
                (selectedStage.ordinal() + 1)
                        % stages.length;

        selectedStage =
                stages[nextIndex];

        return selectedStage;
    }

    public synchronized VisionStage selectPrevious() {

        if (frozen) {
            return selectedStage;
        }

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

    public synchronized void release() {

        frozen = false;
        selectedFrameAvailable = false;

        selectedFrame.release();
    }

    private void drawStageLabel(
            Mat rgbaOutput
    ) {

        String label =
                selectedStage.getDisplayName();

        if (frozen) {
            label += " [PAUSED]";
        }

        int left = 18;
        int top = 18;

        int right =
                Math.min(
                        rgbaOutput.cols() - 18,
                        frozen ? 560 : 430
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
