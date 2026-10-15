package com.example.leitorgabaritoomr.infrastructure.school;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Codec versionado da estrutura completa de uma versao de prova. */
public final class OmrAssessmentStructureBinaryCodec {

    private static final int MAGIC = 0x4F4D5255;
    private static final int MAX_BLOCK_COUNT = 10_000;
    private static final String INVALID_MESSAGE =
            "Os dados persistidos da estrutura da prova sao invalidos.";

    private final OmrAssessmentBlockBinaryCodec blockCodec;

    public OmrAssessmentStructureBinaryCodec() {
        blockCodec = new OmrAssessmentBlockBinaryCodec();
    }

    public byte[] encode(OmrAssessmentStructure structure) {
        if (structure == null) {
            throw new IllegalArgumentException(
                    "A estrutura da prova e obrigatoria."
            );
        }

        byte[] body = OmrAssessmentPersistenceBinaryCodecSupport
                .encodeBody(
                        output -> {
                            OmrAssessmentPersistenceBinaryCodecSupport
                                    .writeText(
                                            output,
                                            structure.getAssessmentId()
                                    );
                            output.writeInt(
                                    structure.getAssessmentVersion()
                            );
                            output.writeInt(structure.getQuestionCount());
                            output.writeInt(structure.getBlockCount());

                            for (OmrAssessmentBlock block
                                    : structure.getBlocks()) {
                                OmrAssessmentPersistenceBinaryCodecSupport
                                        .writeNestedPayload(
                                                output,
                                                blockCodec.encode(block)
                                        );
                            }
                        },
                        "Nao foi possivel codificar a estrutura da prova."
                );

        return OmrAssessmentPersistenceBinaryCodecSupport.envelope(
                MAGIC,
                body,
                INVALID_MESSAGE,
                "Nao foi possivel codificar a estrutura da prova."
        );
    }

    public OmrAssessmentStructure decode(byte[] payload) {
        byte[] body = OmrAssessmentPersistenceBinaryCodecSupport
                .decodeEnvelope(
                        payload,
                        MAGIC,
                        "Versao de estrutura da prova armazenada nao suportada: ",
                        INVALID_MESSAGE
                );

        try (DataInputStream input =
                     OmrAssessmentPersistenceBinaryCodecSupport
                             .inputOf(body)) {

            String assessmentId =
                    OmrAssessmentPersistenceBinaryCodecSupport
                            .readText(input, INVALID_MESSAGE);
            int assessmentVersion =
                    OmrAssessmentPersistenceBinaryCodecSupport
                            .readPositiveInt(input, INVALID_MESSAGE);
            int questionCount =
                    OmrAssessmentPersistenceBinaryCodecSupport
                            .readPositiveInt(input, INVALID_MESSAGE);
            int blockCount =
                    OmrAssessmentPersistenceBinaryCodecSupport
                            .readPositiveInt(input, INVALID_MESSAGE);

            if (blockCount > MAX_BLOCK_COUNT
                    || blockCount > questionCount) {
                throw invalid(null);
            }

            List<OmrAssessmentBlock> blocks =
                    new ArrayList<>(blockCount);

            for (int index = 0; index < blockCount; index++) {
                blocks.add(blockCodec.decode(
                        OmrAssessmentPersistenceBinaryCodecSupport
                                .readNestedPayload(
                                        input,
                                        INVALID_MESSAGE
                                )
                ));
            }

            OmrAssessmentPersistenceBinaryCodecSupport.requireEnd(
                    input,
                    INVALID_MESSAGE
            );

            return new OmrAssessmentStructure(
                    assessmentId,
                    assessmentVersion,
                    questionCount,
                    blocks
            );

        } catch (IOException | RuntimeException exception) {
            throw invalid(exception);
        }
    }

    private static IllegalArgumentException invalid(Throwable cause) {
        return OmrAssessmentPersistenceBinaryCodecSupport.invalid(
                INVALID_MESSAGE,
                cause
        );
    }
}
