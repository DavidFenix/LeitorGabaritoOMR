package com.example.leitorgabaritoomr.infrastructure.school;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;

import java.io.DataInputStream;
import java.io.IOException;

/** Codec versionado de um bloco curricular e sua politica de nota. */
public final class OmrAssessmentBlockBinaryCodec {

    private static final int MAGIC = 0x4F4D5242;
    private static final String INVALID_MESSAGE =
            "Os dados persistidos do bloco da prova sao invalidos.";

    private final OmrAssessmentScoringPolicyBinaryCodec policyCodec;

    public OmrAssessmentBlockBinaryCodec() {
        policyCodec = new OmrAssessmentScoringPolicyBinaryCodec();
    }

    public byte[] encode(OmrAssessmentBlock block) {
        if (block == null) {
            throw new IllegalArgumentException(
                    "O bloco da prova e obrigatorio."
            );
        }

        byte[] policyPayload = policyCodec.encode(
                block.getScoringPolicy()
        );

        byte[] body = OmrAssessmentPersistenceBinaryCodecSupport
                .encodeBody(
                        output -> {
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(output, block.getBlockId());
                            output.writeInt(block.getOrder());
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeOptionalText(
                                            output,
                                            block.getAreaName()
                                    );
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeOptionalText(
                                            output,
                                            block.getComponentCode()
                                    );
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            block.getComponentName()
                                    );
                            output.writeInt(
                                    block.getFirstQuestionPosition()
                            );
                            output.writeInt(block.getQuestionCount());
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeNestedPayload(
                                            output,
                                            policyPayload
                                    );
                        },
                        "Nao foi possivel codificar o bloco da prova."
                );

        return OmrAssessmentPersistenceBinaryCodecSupport.envelope(
                MAGIC,
                body,
                INVALID_MESSAGE,
                "Nao foi possivel codificar o bloco da prova."
        );
    }

    public OmrAssessmentBlock decode(byte[] payload) {
        byte[] body = OmrAssessmentPersistenceBinaryCodecSupport
                .decodeEnvelope(
                        payload,
                        MAGIC,
                        "Versao de bloco da prova armazenado nao suportada: ",
                        INVALID_MESSAGE
                );

        try (DataInputStream input =
                     OmrAssessmentPersistenceBinaryCodecSupport
                             .inputOf(body)) {

            OmrAssessmentBlock block = new OmrAssessmentBlock(
                    readText(input),
                    readPositiveInt(input),
                    OmrAssessmentPersistenceBinaryCodecSupport
                            .readOptionalText(input, INVALID_MESSAGE),
                    OmrAssessmentPersistenceBinaryCodecSupport
                            .readOptionalText(input, INVALID_MESSAGE),
                    readText(input),
                    readPositiveInt(input),
                    readPositiveInt(input),
                    policyCodec.decode(
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .readNestedPayload(
                                            input,
                                            INVALID_MESSAGE
                                    )
                    )
            );

            OmrAssessmentPersistenceBinaryCodecSupport.requireEnd(
                    input,
                    INVALID_MESSAGE
            );

            return block;

        } catch (IOException | RuntimeException exception) {
            throw invalid(exception);
        }
    }

    private static String readText(DataInputStream input)
            throws IOException {
        return OmrAssessmentPersistenceBinaryCodecSupport.readText(
                input,
                INVALID_MESSAGE
        );
    }

    private static int readPositiveInt(DataInputStream input)
            throws IOException {
        return OmrAssessmentPersistenceBinaryCodecSupport
                .readPositiveInt(input, INVALID_MESSAGE);
    }

    private static IllegalArgumentException invalid(Throwable cause) {
        return OmrAssessmentPersistenceBinaryCodecSupport.invalid(
                INVALID_MESSAGE,
                cause
        );
    }
}
