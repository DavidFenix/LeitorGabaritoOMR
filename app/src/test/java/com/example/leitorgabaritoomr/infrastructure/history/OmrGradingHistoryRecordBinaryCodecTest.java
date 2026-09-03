package com.example.leitorgabaritoomr.infrastructure.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyDefinition;
import com.example.leitorgabaritoomr.domain.grading.OmrAnswerKeyEntry;
import com.example.leitorgabaritoomr.domain.grading.OmrGradingResult;
import com.example.leitorgabaritoomr.domain.grading.OmrReadingGrader;
import com.example.leitorgabaritoomr.domain.history.OmrGradingHistoryRecord;
import com.example.leitorgabaritoomr.domain.reading.OmrQuestionResult;
import com.example.leitorgabaritoomr.domain.reading.OmrReadingResult;
import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.CRC32;

public final class OmrGradingHistoryRecordBinaryCodecTest {

    private static final int MAGIC = 0x4F4D5248;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrGradingHistoryRecordBinaryCodec codec =
            new OmrGradingHistoryRecordBinaryCodec();

    @Test
    public void roundTripPreservesCompleteHistoryRecord() {
        OmrGradingHistoryRecord original = record(
                "history-001",
                "reading-001",
                "student-001"
        );

        byte[] payload = codec.encode(original);
        OmrGradingHistoryRecord restored =
                codec.decode(payload);

        assertEquals(original, restored);
        assertEquals(
                original.getStoredAtEpochMillis(),
                restored.getStoredAtEpochMillis()
        );
        assertEquals(
                original.getStudent().getStudentId(),
                restored.getStudent().getStudentId()
        );
        assertEquals(
                original.getStudent().getRegistration(),
                restored.getStudent().getRegistration()
        );
        assertEquals(
                original.getStudent().getName(),
                restored.getStudent().getName()
        );
        assertEquals(
                original.getStudent().getClassName(),
                restored.getStudent().getClassName()
        );
        assertEquals(
                original.getGradingResult(),
                restored.getGradingResult()
        );
        assertEquals(
                original.getAwardedPercentage(),
                restored.getAwardedPercentage(),
                0.000001
        );
    }

    @Test
    public void differentRecordsProduceDifferentPayloads() {
        byte[] first = codec.encode(
                record(
                        "history-001",
                        "reading-001",
                        "student-001"
                )
        );

        byte[] second = codec.encode(
                record(
                        "history-002",
                        "reading-002",
                        "student-001"
                )
        );

        assertFalse(Arrays.equals(first, second));
        assertNotEquals(
                codec.decode(first),
                codec.decode(second)
        );
    }

    @Test
    public void encodedPayloadContainsEnvelopeAndBody() {
        byte[] payload = codec.encode(
                record(
                        "history-001",
                        "reading-001",
                        "student-001"
                )
        );

        ByteBuffer buffer = ByteBuffer.wrap(payload);

        assertEquals(MAGIC, buffer.getInt());
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
    public void rejectsNullRecordDuringEncoding() {
        expectIllegalArgument(() -> codec.encode(null));
    }

    @Test
    public void rejectsNullEmptyAndOversizedPayloads() {
        expectIllegalArgument(() -> codec.decode(null));
        expectIllegalArgument(() -> codec.decode(new byte[0]));

        expectIllegalArgument(() ->
                codec.decode(
                        new byte[
                                HEADER_BYTE_COUNT
                                        + (2 * 1024 * 1024)
                                        + 1
                                ]
                )
        );
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
            assertTrue(
                    expected.getMessage().startsWith(
                            "Versao do registro historico"
                    )
            );
        }
    }

    @Test
    public void rejectsPayloadWithInvalidChecksum() {
        byte[] payload = validPayload();
        payload[payload.length - 1] ^= 0x01;

        expectIllegalArgument(() -> codec.decode(payload));
    }

    @Test
    public void rejectsTruncatedPayload() {
        byte[] payload = validPayload();
        byte[] truncated = Arrays.copyOf(
                payload,
                payload.length - 1
        );

        expectIllegalArgument(() -> codec.decode(truncated));
    }

    @Test
    public void rejectsPayloadWithTrailingByte() {
        byte[] payload = validPayload();
        byte[] extended = Arrays.copyOf(
                payload,
                payload.length + 1
        );

        extended[extended.length - 1] = 0x01;

        expectIllegalArgument(() -> codec.decode(extended));
    }

    @Test
    public void rejectsEnvelopeContainingUnexpectedObject()
            throws Exception {

        byte[] serializedObject = serializeObject(
                "isto nao e um registro historico"
        );

        byte[] payload = envelope(serializedObject);

        expectIllegalArgument(() -> codec.decode(payload));
    }

    private byte[] validPayload() {
        return codec.encode(
                record(
                        "history-001",
                        "reading-001",
                        "student-001"
                )
        );
    }

    private byte[] serializeObject(
            Object object
    ) throws Exception {
        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(object);
            output.flush();
        }

        return byteOutput.toByteArray();
    }

    private byte[] envelope(
            byte[] serializedObject
    ) throws Exception {
        CRC32 checksum = new CRC32();
        checksum.update(serializedObject);

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (DataOutputStream output =
                     new DataOutputStream(byteOutput)) {

            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(serializedObject.length);
            output.writeLong(checksum.getValue());
            output.write(serializedObject);
            output.flush();
        }

        return byteOutput.toByteArray();
    }

    private OmrGradingHistoryRecord record(
            String historyRecordId,
            String readingId,
            String studentId
    ) {
        OmrStudentIdentity student =
                new OmrStudentIdentity(
                        studentId,
                        "000123",
                        "Ana Beatriz",
                        "9 A"
                );

        OmrReadingResult reading = new OmrReadingResult(
                readingId,
                1_800_000_000_000L,
                "layout-controlled",
                1,
                "Layout controlado",
                Arrays.asList(
                        singleMark(1, "Q01", "A", 0.96),
                        singleMark(2, "Q02", "C", 0.91)
                )
        );

        OmrAnswerKeyDefinition answerKey =
                new OmrAnswerKeyDefinition(
                        "answer-key-001",
                        2,
                        "Avaliacao de Matematica",
                        "layout-controlled",
                        1,
                        Arrays.asList(
                                entry("Q01", "A", 2.0),
                                entry("Q02", "B", 1.0)
                        )
                );

        OmrGradingResult gradingResult =
                new OmrReadingGrader().grade(
                        reading,
                        answerKey
                );

        return new OmrGradingHistoryRecord(
                historyRecordId,
                1_800_000_000_500L,
                student,
                gradingResult
        );
    }

    private OmrAnswerKeyEntry entry(
            String questionId,
            String optionId,
            double weight
    ) {
        return OmrAnswerKeyEntry.singleAnswer(
                questionId,
                optionId,
                weight
        );
    }

    private OmrQuestionResult singleMark(
            int position,
            String questionId,
            String optionId,
            double confidence
    ) {
        return new OmrQuestionResult(
                position,
                questionId,
                OmrQuestionResult.Status.SINGLE_MARK,
                Collections.singletonList(
                        new OmrQuestionResult.Option(
                                optionId,
                                optionId
                        )
                ),
                confidence
        );
    }

    private void expectIllegalArgument(
            Runnable action
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
