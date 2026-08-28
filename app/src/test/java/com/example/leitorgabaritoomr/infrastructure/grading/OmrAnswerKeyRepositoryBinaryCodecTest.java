package com.example.leitorgabaritoomr.infrastructure.grading;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class OmrAnswerKeyRepositoryBinaryCodecTest {

    private static final double DELTA = 0.000001;

    private final OmrAnswerKeyRepositoryBinaryCodec codec =
            new OmrAnswerKeyRepositoryBinaryCodec();

    @Test
    public void emptyRepositoryRoundTripIsSupported() {
        byte[] payload = codec.encode(
                Collections.emptyList(),
                null
        );

        OmrAnswerKeyRepositoryBinaryCodec.Snapshot snapshot =
                codec.decode(payload);

        assertEquals(0, snapshot.getAnswerKeyCount());
        assertTrue(snapshot.getAnswerKeys().isEmpty());
        assertFalse(snapshot.hasActiveAnswerKey());
        assertNull(snapshot.getActiveAnswerKeyOrNull());
    }

    @Test
    public void roundTripPreservesOrderContentAndActiveSelection() {
        OmrAnswerKeyDefinition newest = answerKey(
                "newest",
                2,
                "Mais recente",
                "A"
        );

        OmrAnswerKeyDefinition active = answerKey(
                "active",
                4,
                "Gabarito ativo",
                "B"
        );

        OmrAnswerKeyDefinition oldest = answerKey(
                "oldest",
                1,
                "Mais antigo",
                "C"
        );

        List<OmrAnswerKeyDefinition> ordered = Arrays.asList(
                newest,
                active,
                oldest
        );

        OmrAnswerKeyRepositoryBinaryCodec.Snapshot snapshot =
                codec.decode(
                        codec.encode(ordered, active)
                );

        assertEquals(3, snapshot.getAnswerKeyCount());
        assertTrue(snapshot.hasActiveAnswerKey());

        assertAnswerKey(
                newest,
                snapshot.getAnswerKeys().get(0)
        );

        assertAnswerKey(
                active,
                snapshot.getAnswerKeys().get(1)
        );

        assertAnswerKey(
                oldest,
                snapshot.getAnswerKeys().get(2)
        );

        assertSame(
                snapshot.getAnswerKeys().get(1),
                snapshot.getActiveAnswerKeyOrNull()
        );
    }

    @Test
    public void equivalentIdentityCanSelectActiveAnswerKey() {
        OmrAnswerKeyDefinition stored = answerKey(
                "same-identity",
                3,
                "Conteúdo armazenado",
                "D"
        );

        OmrAnswerKeyDefinition equivalentIdentity = answerKey(
                "same-identity",
                3,
                "Outra instância",
                "A"
        );

        OmrAnswerKeyRepositoryBinaryCodec.Snapshot snapshot =
                codec.decode(
                        codec.encode(
                                Collections.singletonList(stored),
                                equivalentIdentity
                        )
                );

        assertNotNull(snapshot.getActiveAnswerKeyOrNull());
        assertEquals(
                "Conteúdo armazenado",
                snapshot.getActiveAnswerKeyOrNull().getName()
        );
    }

    @Test
    public void decodedListIsImmutable() {
        OmrAnswerKeyRepositoryBinaryCodec.Snapshot snapshot =
                codec.decode(
                        codec.encode(
                                Collections.singletonList(
                                        answerKey(
                                                "immutable",
                                                1,
                                                "Imutável",
                                                "A"
                                        )
                                ),
                                null
                        )
                );

        try {
            snapshot.getAnswerKeys().clear();
            fail("Era esperada UnsupportedOperationException.");

        } catch (UnsupportedOperationException expected) {
            // Comportamento esperado.
        }
    }

    @Test
    public void encodingIsDeterministic() {
        List<OmrAnswerKeyDefinition> answerKeys = Arrays.asList(
                answerKey("one", 1, "Um", "A"),
                answerKey("two", 2, "Dois", "B")
        );

        assertArrayEquals(
                codec.encode(answerKeys, answerKeys.get(0)),
                codec.encode(answerKeys, answerKeys.get(0))
        );
    }

    @Test
    public void nullListIsRejected() {
        expectIllegalArgument(
                () -> codec.encode(null, null)
        );
    }

    @Test
    public void activeAnswerKeyOutsideCollectionIsRejected() {
        OmrAnswerKeyDefinition stored = answerKey(
                "stored",
                1,
                "Armazenado",
                "A"
        );

        OmrAnswerKeyDefinition external = answerKey(
                "external",
                1,
                "Externo",
                "B"
        );

        expectIllegalArgument(() ->
                codec.encode(
                        Collections.singletonList(stored),
                        external
                )
        );
    }

    @Test
    public void repeatedIdentityIsRejected() {
        OmrAnswerKeyDefinition first = answerKey(
                "repeated",
                2,
                "Primeiro",
                "A"
        );

        OmrAnswerKeyDefinition repeated = answerKey(
                "repeated",
                2,
                "Segundo",
                "D"
        );

        expectIllegalArgument(() ->
                codec.encode(
                        Arrays.asList(first, repeated),
                        null
                )
        );
    }

    @Test
    public void invalidSignatureIsRejected() {
        byte[] payload = validPayload();
        payload[0] ^= 0x01;

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void unsupportedVersionIsRejected() {
        byte[] payload = validPayload();
        writeInt(payload, 4, 2);

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void invalidActiveIndexIsRejected() {
        byte[] payload = validPayload();
        writeInt(payload, 12, 99);

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void truncatedAndTrailingPayloadsAreRejected() {
        byte[] complete = validPayload();

        byte[] truncated = Arrays.copyOf(
                complete,
                complete.length - 1
        );

        expectIllegalArgument(
                () -> codec.decode(truncated)
        );

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
    public void corruptedNestedAnswerKeyIsRejected() {
        byte[] payload = validPayload();

        int firstAnswerKeyStart = 20;
        payload[firstAnswerKeyStart] ^= 0x01;

        expectIllegalArgument(() -> codec.decode(payload));
    }

    private byte[] validPayload() {
        OmrAnswerKeyDefinition answerKey = answerKey(
                "valid",
                1,
                "Válido",
                "A"
        );

        return codec.encode(
                Collections.singletonList(answerKey),
                answerKey
        );
    }

    private static OmrAnswerKeyDefinition answerKey(
            String id,
            int version,
            String name,
            String firstAnswerLabel
    ) {
        OmrAnswerKeyEntry first =
                OmrAnswerKeyEntry.singleAnswer(
                        "Q01",
                        "Q01-" + firstAnswerLabel,
                        1.25
                );

        OmrAnswerKeyEntry second =
                new OmrAnswerKeyEntry(
                        "Q02",
                        Arrays.asList(
                                "Q02-B",
                                "Q02-D"
                        ),
                        2.5
                );

        return new OmrAnswerKeyDefinition(
                id,
                version,
                name,
                "avalie-ce-development",
                1,
                Arrays.asList(first, second)
        );
    }

    private static void assertAnswerKey(
            OmrAnswerKeyDefinition expected,
            OmrAnswerKeyDefinition actual
    ) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getVersion(), actual.getVersion());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getLayoutId(), actual.getLayoutId());
        assertEquals(
                expected.getLayoutVersion(),
                actual.getLayoutVersion()
        );
        assertEquals(
                expected.getQuestionCount(),
                actual.getQuestionCount()
        );
        assertEquals(
                expected.getTotalWeight(),
                actual.getTotalWeight(),
                DELTA
        );

        for (int index = 0;
             index < expected.getQuestionCount();
             index++) {

            OmrAnswerKeyEntry expectedEntry =
                    expected.getEntries().get(index);

            OmrAnswerKeyEntry actualEntry =
                    actual.getEntries().get(index);

            assertEquals(
                    expectedEntry.getQuestionId(),
                    actualEntry.getQuestionId()
            );
            assertEquals(
                    expectedEntry.getAcceptedOptionIds(),
                    actualEntry.getAcceptedOptionIds()
            );
            assertEquals(
                    expectedEntry.getWeight(),
                    actualEntry.getWeight(),
                    DELTA
            );
        }
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
