package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.student.OmrStudentProfile;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OmrStudentProfileBinaryCodecTest {

    private static final int MAGIC = 0x4F4D5253;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrStudentProfileBinaryCodec codec =
            new OmrStudentProfileBinaryCodec();

    @Test
    public void roundTripPreservesCompleteNormalizedStudent() {
        OmrStudentProfile original = new OmrStudentProfile(
                " student-001 ",
                " 0007-a/26 ",
                " Ana Beatriz da Silva – 9º A "
        );

        OmrStudentProfile restored =
                codec.decode(codec.encode(original));

        assertCompleteStudent(original, restored);
        assertEquals("student-001", restored.getStudentId());
        assertEquals("0007-A/26", restored.getRegistration());
    }

    @Test
    public void sameStudentProducesDeterministicPayload() {
        OmrStudentProfile student = student();

        assertTrue(Arrays.equals(
                codec.encode(student),
                codec.encode(student)
        ));
    }

    @Test
    public void differentStudentDataProducesDifferentPayload() {
        byte[] first = codec.encode(student());

        byte[] second = codec.encode(
                new OmrStudentProfile(
                        "student-001",
                        "000124",
                        "Ana Beatriz Costa"
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void encodedPayloadContainsEnvelopeAndBody() {
        byte[] payload = codec.encode(student());
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
    public void rejectsNullStudentDuringEncoding() {
        expectIllegalArgument(() -> codec.encode(null));
    }

    @Test
    public void rejectsStudentWithOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'A');

        OmrStudentProfile oversized =
                new OmrStudentProfile(
                        "student-001",
                        "000123",
                        new String(characters)
                );

        expectIllegalArgument(() -> codec.encode(oversized));
    }

    @Test
    public void rejectsNullEmptyAndOversizedPayloads() {
        expectIllegalArgument(() -> codec.decode(null));
        expectIllegalArgument(() -> codec.decode(new byte[0]));

        expectIllegalArgument(() -> codec.decode(
                new byte[
                        HEADER_BYTE_COUNT
                                + (1024 * 1024)
                                + 1
                        ]
        ));
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
                    "Versão de aluno armazenada"
            ));
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

    private byte[] validPayload() {
        return codec.encode(student());
    }

    private OmrStudentProfile student() {
        return new OmrStudentProfile(
                "student-001",
                "000123",
                "Ana Beatriz"
        );
    }

    private void assertCompleteStudent(
            OmrStudentProfile expected,
            OmrStudentProfile actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getRegistration(),
                actual.getRegistration()
        );
        assertEquals(expected.getName(), actual.getName());
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
