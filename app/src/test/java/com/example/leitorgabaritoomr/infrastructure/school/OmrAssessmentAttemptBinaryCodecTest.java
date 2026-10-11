package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentAttempt;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OmrAssessmentAttemptBinaryCodecTest {

    private static final int ATTEMPT_MAGIC = 0x4F4D5254;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrAssessmentAttemptBinaryCodec codec =
            new OmrAssessmentAttemptBinaryCodec();

    @Test
    public void roundTripPreservesCompleteNormalizedAttempt() {
        OmrAssessmentAttempt original =
                new OmrAssessmentAttempt(
                        " attempt-001 ",
                        " application-001 ",
                        " student-001 ",
                        3,
                        " history-001 ",
                        1_800_000_000_000L
                );

        OmrAssessmentAttempt restored = codec.decode(
                codec.encode(original)
        );

        assertCompleteAttempt(original, restored);
        assertEquals("attempt-001", restored.getAttemptId());
        assertEquals("history-001", restored.getHistoryRecordId());
    }

    @Test
    public void sameAttemptProducesDeterministicPayload() {
        assertTrue(Arrays.equals(
                codec.encode(attempt()),
                codec.encode(attempt())
        ));
    }

    @Test
    public void differentAttemptDataProducesDifferentPayload() {
        byte[] first = codec.encode(attempt());
        byte[] second = codec.encode(
                new OmrAssessmentAttempt(
                        "attempt-001",
                        "application-001",
                        "student-001",
                        2,
                        "history-002",
                        2_001L
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void payloadContainsVersionedEnvelopeAndBody() {
        byte[] payload = codec.encode(attempt());
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        assertEquals(ATTEMPT_MAGIC, buffer.getInt());
        assertEquals(FORMAT_VERSION, buffer.getInt());

        int bodySize = buffer.getInt();
        long checksum = buffer.getLong();

        assertTrue(bodySize > 0);
        assertEquals(
                payload.length - HEADER_BYTE_COUNT,
                bodySize
        );
        assertTrue(checksum >= 0L);
    }

    @Test
    public void rejectsNullDuringEncoding() {
        expectIllegalArgument(() -> codec.encode(null));
    }

    @Test
    public void rejectsOversizedTextDuringEncoding() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'T');

        OmrAssessmentAttempt oversized =
                new OmrAssessmentAttempt(
                        new String(characters),
                        "application-001",
                        "student-001",
                        1,
                        "history-001",
                        1L
                );

        expectIllegalArgument(() -> codec.encode(oversized));
    }

    @Test
    public void rejectsNullEmptyAndOversizedPayloads() {
        byte[] oversized = new byte[
                HEADER_BYTE_COUNT + (1024 * 1024) + 1
                ];

        expectIllegalArgument(() -> codec.decode(null));
        expectIllegalArgument(() -> codec.decode(new byte[0]));
        expectIllegalArgument(() -> codec.decode(oversized));
    }

    @Test
    public void rejectsWrongMagic() {
        byte[] payload = validPayload();
        ByteBuffer.wrap(payload).putInt(0, 0x12345678);

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void rejectsUnsupportedFormatVersion() {
        byte[] payload = validPayload();
        ByteBuffer.wrap(payload).putInt(4, 99);

        try {
            codec.decode(payload);
            fail("Era esperada uma IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
            assertTrue(expected.getMessage().startsWith(
                    "Versão de tentativa armazenada"
            ));
        }
    }

    @Test
    public void rejectsInvalidChecksum() {
        byte[] payload = validPayload();
        payload[payload.length - 1] ^= 0x01;

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void rejectsTruncatedPayload() {
        byte[] payload = validPayload();

        expectIllegalArgument(() -> codec.decode(
                Arrays.copyOf(payload, payload.length - 1)
        ));
    }

    @Test
    public void rejectsPayloadWithTrailingBytes() {
        byte[] valid = validPayload();
        byte[] payload = Arrays.copyOf(valid, valid.length + 1);
        payload[payload.length - 1] = 0x01;

        expectIllegalArgument(() -> codec.decode(payload));
    }

    private byte[] validPayload() {
        return codec.encode(attempt());
    }

    private OmrAssessmentAttempt attempt() {
        return new OmrAssessmentAttempt(
                "attempt-001",
                "application-001",
                "student-001",
                1,
                "history-001",
                2_000L
        );
    }

    private void assertCompleteAttempt(
            OmrAssessmentAttempt expected,
            OmrAssessmentAttempt actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getApplicationId(), actual.getApplicationId());
        assertEquals(expected.getStudentId(), actual.getStudentId());
        assertEquals(expected.getRevision(), actual.getRevision());
        assertEquals(
                expected.getHistoryRecordId(),
                actual.getHistoryRecordId()
        );
        assertEquals(
                expected.getRecordedAtEpochMillis(),
                actual.getRecordedAtEpochMillis()
        );
    }

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
