package com.example.leitorgabaritoomr.vision.debug;

import org.opencv.core.Mat;

public interface VisionDebugSink {

    VisionDebugSink NONE =
            (stage, image) -> {
                // Intencionalmente vazio.
            };

    void publish(
            VisionStage stage,
            Mat image
    );
}