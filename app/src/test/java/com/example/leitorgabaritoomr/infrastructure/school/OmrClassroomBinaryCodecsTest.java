package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.classroom.OmrClassEnrollment;
import com.example.leitorgabaritoomr.domain.classroom.OmrClassGroup;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OmrClassroomBinaryCodecsTest {

    private static final int CLASS_GROUP_MAGIC = 0x4F4D5243;
    private static final int ENROLLMENT_MAGIC = 0x4F4D5245;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrClassGroupBinaryCodec classGroupCodec =
            new OmrClassGroupBinaryCodec();
    private final OmrClassEnrollmentBinaryCodec enrollmentCodec =
            new OmrClassEnrollmentBinaryCodec();

    @Test
    public void classGroupRoundTripPreservesCompleteNormalizedData() {
        OmrClassGroup original = new OmrClassGroup(
                " class-001 ",
                " 9º A – Manhã ",
                " Ensino Fundamental – Anos Finais ",
                2026
        );

        OmrClassGroup restored = classGroupCodec.decode(
                classGroupCodec.encode(original)
        );

        assertCompleteClassGroup(original, restored);
        assertEquals("class-001", restored.getClassGroupId());
        assertEquals("9º A – Manhã", restored.getName());
    }

    @Test
    public void enrollmentRoundTripPreservesCompleteNormalizedData() {
        OmrClassEnrollment original =
                new OmrClassEnrollment(
                        " class-001 ",
                        " student-007 ",
                        19
                );

        OmrClassEnrollment restored = enrollmentCodec.decode(
                enrollmentCodec.encode(original)
        );

        assertCompleteEnrollment(original, restored);
        assertEquals("class-001", restored.getClassGroupId());
        assertEquals("student-007", restored.getStudentId());
    }

    @Test
    public void sameClassGroupProducesDeterministicPayload() {
        OmrClassGroup classGroup = classGroup();

        assertTrue(Arrays.equals(
                classGroupCodec.encode(classGroup),
                classGroupCodec.encode(classGroup)
        ));
    }

    @Test
    public void sameEnrollmentProducesDeterministicPayload() {
        OmrClassEnrollment enrollment = enrollment();

        assertTrue(Arrays.equals(
                enrollmentCodec.encode(enrollment),
                enrollmentCodec.encode(enrollment)
        ));
    }

    @Test
    public void differentClassGroupDataProducesDifferentPayload() {
        byte[] first = classGroupCodec.encode(classGroup());
        byte[] second = classGroupCodec.encode(
                new OmrClassGroup(
                        "class-001",
                        "9º B",
                        "9º ano",
                        2027
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void differentEnrollmentPositionProducesDifferentPayload() {
        byte[] first = enrollmentCodec.encode(enrollment());
        byte[] second = enrollmentCodec.encode(
                new OmrClassEnrollment(
                        "class-001",
                        "student-001",
                        18
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void classGroupPayloadContainsEnvelopeAndBody() {
        assertEnvelope(
                classGroupCodec.encode(classGroup()),
                CLASS_GROUP_MAGIC
        );
    }

    @Test
    public void enrollmentPayloadContainsEnvelopeAndBody() {
        assertEnvelope(
                enrollmentCodec.encode(enrollment()),
                ENROLLMENT_MAGIC
        );
    }

    @Test
    public void codecsRejectNullDuringEncoding() {
        expectIllegalArgument(() ->
                classGroupCodec.encode(null)
        );
        expectIllegalArgument(() ->
                enrollmentCodec.encode(null)
        );
    }

    @Test
    public void classGroupCodecRejectsOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'A');

        OmrClassGroup oversized = new OmrClassGroup(
                "class-001",
                new String(characters),
                "9º ano",
                2026
        );

        expectIllegalArgument(() ->
                classGroupCodec.encode(oversized)
        );
    }

    @Test
    public void enrollmentCodecRejectsOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'S');

        OmrClassEnrollment oversized =
                new OmrClassEnrollment(
                        "class-001",
                        new String(characters),
                        7
                );

        expectIllegalArgument(() ->
                enrollmentCodec.encode(oversized)
        );
    }

    @Test
    public void codecsRejectNullEmptyAndOversizedPayloads() {
        byte[] oversized = new byte[
                HEADER_BYTE_COUNT + (1024 * 1024) + 1
                ];

        expectIllegalArgument(() ->
                classGroupCodec.decode(null)
        );
        expectIllegalArgument(() ->
                classGroupCodec.decode(new byte[0])
        );
        expectIllegalArgument(() ->
                classGroupCodec.decode(oversized)
        );

        expectIllegalArgument(() ->
                enrollmentCodec.decode(null)
        );
        expectIllegalArgument(() ->
                enrollmentCodec.decode(new byte[0])
        );
        expectIllegalArgument(() ->
                enrollmentCodec.decode(oversized)
        );
    }

    @Test
    public void codecsRejectWrongMagic() {
        byte[] classPayload = validClassGroupPayload();
        byte[] enrollmentPayload = validEnrollmentPayload();

        ByteBuffer.wrap(classPayload).putInt(0, 0x12345678);
        ByteBuffer.wrap(enrollmentPayload).putInt(
                0,
                0x12345678
        );

        expectIllegalArgument(() ->
                classGroupCodec.decode(classPayload)
        );
        expectIllegalArgument(() ->
                enrollmentCodec.decode(enrollmentPayload)
        );
    }

    @Test
    public void codecsRejectUnsupportedFormatVersions() {
        byte[] classPayload = validClassGroupPayload();
        byte[] enrollmentPayload = validEnrollmentPayload();

        ByteBuffer.wrap(classPayload).putInt(4, 99);
        ByteBuffer.wrap(enrollmentPayload).putInt(4, 99);

        expectUnsupportedVersion(
                () -> classGroupCodec.decode(classPayload),
                "Versão de turma armazenada"
        );
        expectUnsupportedVersion(
                () -> enrollmentCodec.decode(enrollmentPayload),
                "Versão de matrícula armazenada"
        );
    }

    @Test
    public void codecsRejectInvalidChecksums() {
        byte[] classPayload = validClassGroupPayload();
        byte[] enrollmentPayload = validEnrollmentPayload();

        classPayload[classPayload.length - 1] ^= 0x01;
        enrollmentPayload[enrollmentPayload.length - 1]
                ^= 0x01;

        expectIllegalArgument(() ->
                classGroupCodec.decode(classPayload)
        );
        expectIllegalArgument(() ->
                enrollmentCodec.decode(enrollmentPayload)
        );
    }

    @Test
    public void codecsRejectTruncatedPayloads() {
        byte[] classPayload = validClassGroupPayload();
        byte[] enrollmentPayload = validEnrollmentPayload();

        expectIllegalArgument(() -> classGroupCodec.decode(
                Arrays.copyOf(
                        classPayload,
                        classPayload.length - 1
                )
        ));
        expectIllegalArgument(() -> enrollmentCodec.decode(
                Arrays.copyOf(
                        enrollmentPayload,
                        enrollmentPayload.length - 1
                )
        ));
    }

    @Test
    public void codecsRejectPayloadsWithTrailingBytes() {
        byte[] validClassPayload = validClassGroupPayload();
        byte[] validEnrollmentBytes = validEnrollmentPayload();

        byte[] classPayload = Arrays.copyOf(
                validClassPayload,
                validClassPayload.length + 1
        );
        byte[] enrollmentPayload = Arrays.copyOf(
                validEnrollmentBytes,
                validEnrollmentBytes.length + 1
        );

        classPayload[classPayload.length - 1] = 0x01;
        enrollmentPayload[enrollmentPayload.length - 1] = 0x01;

        expectIllegalArgument(() ->
                classGroupCodec.decode(classPayload)
        );
        expectIllegalArgument(() ->
                enrollmentCodec.decode(enrollmentPayload)
        );
    }

    @Test
    public void codecsDoNotAcceptEachOthersPayload() {
        expectIllegalArgument(() -> classGroupCodec.decode(
                validEnrollmentPayload()
        ));
        expectIllegalArgument(() -> enrollmentCodec.decode(
                validClassGroupPayload()
        ));
    }

    private void assertEnvelope(
            byte[] payload,
            int expectedMagic
    ) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        assertEquals(expectedMagic, buffer.getInt());
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

    private byte[] validClassGroupPayload() {
        return classGroupCodec.encode(classGroup());
    }

    private byte[] validEnrollmentPayload() {
        return enrollmentCodec.encode(enrollment());
    }

    private OmrClassGroup classGroup() {
        return new OmrClassGroup(
                "class-001",
                "9º A",
                "9º ano",
                2026
        );
    }

    private OmrClassEnrollment enrollment() {
        return new OmrClassEnrollment(
                "class-001",
                "student-001",
                7
        );
    }

    private void assertCompleteClassGroup(
            OmrClassGroup expected,
            OmrClassGroup actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(expected.getName(), actual.getName());
        assertEquals(
                expected.getGradeLevel(),
                actual.getGradeLevel()
        );
        assertEquals(
                expected.getSchoolYear(),
                actual.getSchoolYear()
        );
    }

    private void assertCompleteEnrollment(
            OmrClassEnrollment expected,
            OmrClassEnrollment actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getRosterPosition(),
                actual.getRosterPosition()
        );
    }

    private void expectUnsupportedVersion(
            Runnable action,
            String expectedMessagePrefix
    ) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");

        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
            assertTrue(expected.getMessage().startsWith(
                    expectedMessagePrefix
            ));
        }
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
