package com.example.leitorgabaritoomr.infrastructure.grading;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class OmrAnswerKeyBinaryCodecTest {

    private static final double DELTA = 0.000001;

    private final OmrAnswerKeyBinaryCodec codec =
            new OmrAnswerKeyBinaryCodec();

    @Test
    public void roundTripPreservesCompleteAnswerKey() {
        OmrAnswerKeyDefinition original =
                createAnswerKey();

        byte[] encoded = codec.encode(original);
        OmrAnswerKeyDefinition decoded = codec.decode(encoded);

        assertTrue(encoded.length > 0);
        assertNotSame(original, decoded);

        assertEquals(original.getId(), decoded.getId());
        assertEquals(original.getVersion(), decoded.getVersion());
        assertEquals(original.getName(), decoded.getName());
        assertEquals(original.getLayoutId(), decoded.getLayoutId());
        assertEquals(
                original.getLayoutVersion(),
                decoded.getLayoutVersion()
        );
        assertEquals(
                original.getQuestionCount(),
                decoded.getQuestionCount()
        );
        assertEquals(
                original.getTotalWeight(),
                decoded.getTotalWeight(),
                DELTA
        );

        for (int index = 0;
             index < original.getQuestionCount();
             index++) {

            OmrAnswerKeyEntry expected =
                    original.getEntries().get(index);

            OmrAnswerKeyEntry actual =
                    decoded.getEntries().get(index);

            assertEquals(
                    expected.getQuestionId(),
                    actual.getQuestionId()
            );
            assertEquals(
                    expected.getAcceptedOptionIds(),
                    actual.getAcceptedOptionIds()
            );
            assertEquals(
                    expected.getWeight(),
                    actual.getWeight(),
                    DELTA
            );
        }
    }

    @Test
    public void encodingIsDeterministic() {
        OmrAnswerKeyDefinition answerKey =
                createAnswerKey();

        assertArrayEquals(
                codec.encode(answerKey),
                codec.encode(answerKey)
        );
    }

    @Test
    public void nullAnswerKeyIsRejected() {
        expectIllegalArgument(() -> codec.encode(null));
    }

    @Test
    public void nullPayloadIsRejected() {
        expectIllegalArgument(() -> codec.decode(null));
    }

    @Test
    public void emptyPayloadIsRejected() {
        expectIllegalArgument(
                () -> codec.decode(new byte[0])
        );
    }

    @Test
    public void invalidSignatureIsRejected() {
        byte[] payload = codec.encode(createAnswerKey());
        payload[0] ^= 0x01;

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void unsupportedStorageVersionIsRejected() {
        byte[] payload = codec.encode(createAnswerKey());
        writeInt(payload, 4, 2);

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void truncatedPayloadIsRejected() {
        byte[] complete = codec.encode(createAnswerKey());

        byte[] truncated = Arrays.copyOf(
                complete,
                complete.length - 1
        );

        expectIllegalArgument(() -> codec.decode(truncated));
    }

    @Test
    public void trailingDataIsRejected() {
        byte[] complete = codec.encode(createAnswerKey());

        byte[] withTrailingData = Arrays.copyOf(
                complete,
                complete.length + 1
        );

        withTrailingData[withTrailingData.length - 1] = 0x01;

        expectIllegalArgument(
                () -> codec.decode(withTrailingData)
        );
    }

    @Test
    public void invalidTextLengthIsRejected() {
        byte[] payload = codec.encode(createAnswerKey());

        writeInt(payload, 8, 0);

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void nonFinitePersistedWeightIsRejected() {
        byte[] payload = codec.encode(createAnswerKey());

        int weightOffset = findFirstWeightOffset(payload);

        ByteBuffer.wrap(payload)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(
                        weightOffset,
                        Double.doubleToLongBits(Double.NaN)
                );

        expectIllegalArgument(() -> codec.decode(payload));
    }

    private static OmrAnswerKeyDefinition createAnswerKey() {
        OmrAnswerKeyEntry first =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "Q01-A",
                        1.0
                );

        OmrAnswerKeyEntry second =
                new OmrAnswerKeyEntry(
                        "bloco-β-Q02",
                        Arrays.asList(
                                "Q02-B",
                                "Q02-D"
                        ),
                        2.5
                );

        OmrAnswerKeyEntry third =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q03",
                        "alternativa-Ç",
                        0.75
                );

        return new OmrAnswerKeyDefinition(
                "gabarito-oficial-2026",
                3,
                "Gabarito São José – 2026",
                "layout-avaliação-52",
                4,
                Arrays.asList(
                        first,
                        second,
                        third
                )
        );
    }

    private static int findFirstWeightOffset(
            byte[] payload
    ) {
        ByteBuffer buffer = ByteBuffer.wrap(payload)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.getInt();
        buffer.getInt();

        skipText(buffer);
        buffer.getInt();
        skipText(buffer);
        skipText(buffer);
        buffer.getInt();
        buffer.getInt();
        skipText(buffer);

        return buffer.position();
    }

    private static void skipText(
            ByteBuffer buffer
    ) {
        int byteCount = buffer.getInt();

        buffer.position(
                buffer.position() + byteCount
        );
    }

    private static void writeInt(
            byte[] payload,
            int offset,
            int value
    ) {
        ByteBuffer.wrap(payload)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(offset, value);
    }

    private static void expectIllegalArgument(
            Runnable operation
    ) {
        try {
            operation.run();
            fail("Era esperada IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            // Comportamento esperado.
        }
    }
}
