package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentBlock;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentScoreInput;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentScoringPolicy;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentStructure;
import com.example.leitorgabaritoomr.domain.assessment.OmrProportionalScoringPolicy;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OmrAssessmentStructureBinaryCodecsTest {

    private static final int POLICY_MAGIC = 0x4F4D5259;
    private static final int BLOCK_MAGIC = 0x4F4D5242;
    private static final int STRUCTURE_MAGIC = 0x4F4D5255;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrAssessmentScoringPolicyBinaryCodec policyCodec =
            new OmrAssessmentScoringPolicyBinaryCodec();
    private final OmrAssessmentBlockBinaryCodec blockCodec =
            new OmrAssessmentBlockBinaryCodec();
    private final OmrAssessmentStructureBinaryCodec structureCodec =
            new OmrAssessmentStructureBinaryCodec();

    @Test
    public void policyRoundTripPreservesCompleteConfiguration() {
        OmrProportionalScoringPolicy original = customPolicy();

        OmrAssessmentScoringPolicy restored = policyCodec.decode(
                policyCodec.encode(original)
        );

        assertEquals(original, restored);
        OmrProportionalScoringPolicy proportional =
                (OmrProportionalScoringPolicy) restored;
        assertEquals(original.getBasis(), proportional.getBasis());
        assertEquals(
                original.getMaximumScore(),
                proportional.getMaximumScore()
        );
        assertEquals(
                original.getDecimalPlaces(),
                proportional.getDecimalPlaces()
        );
        assertEquals(
                original.getRoundingMode(),
                proportional.getRoundingMode()
        );
    }

    @Test
    public void blockRoundTripPreservesOptionalAndRequiredData() {
        OmrAssessmentBlock original = firstBlock();

        OmrAssessmentBlock restored = blockCodec.decode(
                blockCodec.encode(original)
        );

        assertEquals(original, restored);
        assertEquals("Ciencias da Natureza", restored.getAreaName());
        assertEquals("QUI", restored.getComponentCode());
        assertEquals("Quimica", restored.getComponentName());
    }

    @Test
    public void blockRoundTripPreservesAbsentOptionalData() {
        OmrAssessmentBlock original = new OmrAssessmentBlock(
                "geral",
                1,
                null,
                null,
                "Resultado geral",
                1,
                5,
                OmrProportionalScoringPolicy
                        .weightedPointsToTenV1()
        );

        OmrAssessmentBlock restored = blockCodec.decode(
                blockCodec.encode(original)
        );

        assertEquals(original, restored);
        assertFalse(restored.hasAreaName());
        assertFalse(restored.hasComponentCode());
    }

    @Test
    public void structureRoundTripPreservesAllBlocksAndPolicies() {
        OmrAssessmentStructure original = structure();

        OmrAssessmentStructure restored = structureCodec.decode(
                structureCodec.encode(original)
        );

        assertEquals(original, restored);
        assertEquals(3, restored.getBlockCount());
        assertEquals(
                "Fisica",
                restored.getBlocks().get(1).getComponentName()
        );
        assertEquals(
                customPolicy(),
                restored.getBlocks().get(2).getScoringPolicy()
        );
    }

    @Test
    public void sameValuesProduceDeterministicPayloads() {
        assertTrue(Arrays.equals(
                policyCodec.encode(customPolicy()),
                policyCodec.encode(customPolicy())
        ));
        assertTrue(Arrays.equals(
                blockCodec.encode(firstBlock()),
                blockCodec.encode(firstBlock())
        ));
        assertTrue(Arrays.equals(
                structureCodec.encode(structure()),
                structureCodec.encode(structure())
        ));
    }

    @Test
    public void payloadsContainIndependentVersionedEnvelopes() {
        assertEnvelope(
                policyCodec.encode(customPolicy()),
                POLICY_MAGIC
        );
        assertEnvelope(
                blockCodec.encode(firstBlock()),
                BLOCK_MAGIC
        );
        assertEnvelope(
                structureCodec.encode(structure()),
                STRUCTURE_MAGIC
        );
    }

    @Test
    public void differentStructuresProduceDifferentPayloads() {
        byte[] first = structureCodec.encode(structure());
        byte[] second = structureCodec.encode(
                OmrAssessmentStructure.legacySingleBlock(
                        "assessment-001",
                        1,
                        45
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void codecsRejectNullDuringEncoding() {
        expectIllegalArgument(() -> policyCodec.encode(null));
        expectIllegalArgument(() -> blockCodec.encode(null));
        expectIllegalArgument(() -> structureCodec.encode(null));
    }

    @Test
    public void policyCodecRejectsUnknownImplementation() {
        OmrAssessmentScoringPolicy unknown =
                new OmrAssessmentScoringPolicy() {
                    @Override
                    public String getPolicyId() {
                        return "unknown";
                    }

                    @Override
                    public int getVersion() {
                        return 1;
                    }

                    @Override
                    public BigDecimal getMaximumScore() {
                        return BigDecimal.TEN;
                    }

                    @Override
                    public BigDecimal calculate(
                            OmrAssessmentScoreInput input
                    ) {
                        return BigDecimal.ZERO;
                    }
                };

        expectIllegalArgument(() -> policyCodec.encode(unknown));
    }

    @Test
    public void codecsRejectOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'X');

        OmrProportionalScoringPolicy policy =
                new OmrProportionalScoringPolicy(
                        new String(characters),
                        1,
                        OmrProportionalScoringPolicy.Basis
                                .CORRECT_QUESTION_COUNT,
                        BigDecimal.TEN,
                        1,
                        RoundingMode.HALF_UP
                );

        expectIllegalArgument(() -> policyCodec.encode(policy));
    }

    @Test
    public void codecsRejectNullEmptyAndOversizedPayloads() {
        byte[] oversized = new byte[
                HEADER_BYTE_COUNT + (1024 * 1024) + 1
                ];

        for (PayloadDecoder decoder : decoders()) {
            expectIllegalArgument(() -> decoder.decode(null));
            expectIllegalArgument(() -> decoder.decode(new byte[0]));
            expectIllegalArgument(() -> decoder.decode(oversized));
        }
    }

    @Test
    public void codecsRejectWrongMagic() {
        for (PayloadPair pair : validPayloads()) {
            byte[] payload = pair.payload.clone();
            ByteBuffer.wrap(payload).putInt(0, 0x12345678);
            expectIllegalArgument(() -> pair.decoder.decode(payload));
        }
    }

    @Test
    public void codecsRejectUnsupportedOuterFormatVersion() {
        for (PayloadPair pair : validPayloads()) {
            byte[] payload = pair.payload.clone();
            ByteBuffer.wrap(payload).putInt(4, 99);

            try {
                pair.decoder.decode(payload);
                fail("Era esperada uma IllegalArgumentException.");
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
                assertTrue(expected.getMessage().startsWith(
                        "Versao de"
                ));
            }
        }
    }

    @Test
    public void codecsRejectInvalidChecksums() {
        for (PayloadPair pair : validPayloads()) {
            byte[] payload = pair.payload.clone();
            payload[payload.length - 1] ^= 0x01;
            expectIllegalArgument(() -> pair.decoder.decode(payload));
        }
    }

    @Test
    public void codecsRejectTruncatedAndExtendedPayloads() {
        for (PayloadPair pair : validPayloads()) {
            byte[] truncated = Arrays.copyOf(
                    pair.payload,
                    pair.payload.length - 1
            );
            byte[] extended = Arrays.copyOf(
                    pair.payload,
                    pair.payload.length + 1
            );
            extended[extended.length - 1] = 0x01;

            expectIllegalArgument(() ->
                    pair.decoder.decode(truncated)
            );
            expectIllegalArgument(() ->
                    pair.decoder.decode(extended)
            );
        }
    }

    @Test
    public void codecsDoNotAcceptEachOthersPayloads() {
        byte[] policyPayload = policyCodec.encode(customPolicy());
        byte[] blockPayload = blockCodec.encode(firstBlock());
        byte[] structurePayload = structureCodec.encode(structure());

        expectIllegalArgument(() -> blockCodec.decode(policyPayload));
        expectIllegalArgument(() ->
                structureCodec.decode(blockPayload)
        );
        expectIllegalArgument(() ->
                policyCodec.decode(structurePayload)
        );
    }

    private OmrAssessmentStructure structure() {
        return new OmrAssessmentStructure(
                "assessment-001",
                1,
                45,
                Arrays.asList(
                        firstBlock(),
                        new OmrAssessmentBlock(
                                "fisica",
                                2,
                                "Ciencias da Natureza",
                                "FIS",
                                "Fisica",
                                16,
                                15,
                                OmrProportionalScoringPolicy
                                        .correctQuestionsToTenV1()
                        ),
                        new OmrAssessmentBlock(
                                "biologia",
                                3,
                                "Ciencias da Natureza",
                                "BIO",
                                "Biologia",
                                31,
                                15,
                                customPolicy()
                        )
                )
        );
    }

    private OmrAssessmentBlock firstBlock() {
        return new OmrAssessmentBlock(
                "quimica",
                1,
                "Ciencias da Natureza",
                "QUI",
                "Quimica",
                1,
                15,
                OmrProportionalScoringPolicy
                        .correctQuestionsToTenV1()
        );
    }

    private OmrProportionalScoringPolicy customPolicy() {
        return new OmrProportionalScoringPolicy(
                "school-proportional",
                2,
                OmrProportionalScoringPolicy.Basis
                        .CORRECT_QUESTION_COUNT,
                new BigDecimal("20.0"),
                2,
                RoundingMode.HALF_EVEN
        );
    }

    private void assertEnvelope(byte[] payload, int expectedMagic) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        assertEquals(expectedMagic, buffer.getInt());
        assertEquals(FORMAT_VERSION, buffer.getInt());

        int bodySize = buffer.getInt();
        long checksum = buffer.getLong();

        assertTrue(bodySize > 0);
        assertEquals(payload.length - HEADER_BYTE_COUNT, bodySize);
        assertTrue(checksum >= 0L);
    }

    private PayloadDecoder[] decoders() {
        return new PayloadDecoder[]{
                policyCodec::decode,
                blockCodec::decode,
                structureCodec::decode
        };
    }

    private PayloadPair[] validPayloads() {
        return new PayloadPair[]{
                new PayloadPair(
                        policyCodec.encode(customPolicy()),
                        policyCodec::decode
                ),
                new PayloadPair(
                        blockCodec.encode(firstBlock()),
                        blockCodec::decode
                ),
                new PayloadPair(
                        structureCodec.encode(structure()),
                        structureCodec::decode
                )
        };
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }

    private interface PayloadDecoder {
        Object decode(byte[] payload);
    }

    private static final class PayloadPair {
        private final byte[] payload;
        private final PayloadDecoder decoder;

        private PayloadPair(
                byte[] payload,
                PayloadDecoder decoder
        ) {
            this.payload = payload;
            this.decoder = decoder;
        }
    }
}
