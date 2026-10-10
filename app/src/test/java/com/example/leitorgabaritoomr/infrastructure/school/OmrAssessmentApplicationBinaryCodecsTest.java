package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentApplication;
import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentParticipant;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OmrAssessmentApplicationBinaryCodecsTest {

    private static final int APPLICATION_MAGIC = 0x4F4D5249;
    private static final int PARTICIPANT_MAGIC = 0x4F4D5250;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrAssessmentApplicationBinaryCodec applicationCodec =
            new OmrAssessmentApplicationBinaryCodec();
    private final OmrAssessmentParticipantBinaryCodec participantCodec =
            new OmrAssessmentParticipantBinaryCodec();

    @Test
    public void applicationRoundTripPreservesCompleteNormalizedData() {
        OmrAssessmentApplication original =
                new OmrAssessmentApplication(
                        " application-001 ",
                        " assessment-001 ",
                        3,
                        " class-001 ",
                        1_800_000_000_000L
                );

        OmrAssessmentApplication restored = applicationCodec.decode(
                applicationCodec.encode(original)
        );

        assertCompleteApplication(original, restored);
        assertEquals("application-001", restored.getApplicationId());
        assertEquals("class-001", restored.getClassGroupId());
    }

    @Test
    public void participantRoundTripPreservesCompleteNormalizedData() {
        OmrAssessmentParticipant original =
                new OmrAssessmentParticipant(
                        " application-001 ",
                        " student-007 ",
                        " mat-007 ",
                        " Maria da Silva ",
                        " 9º A ",
                        19
                );

        OmrAssessmentParticipant restored = participantCodec.decode(
                participantCodec.encode(original)
        );

        assertCompleteParticipant(original, restored);
        assertEquals("MAT-007", restored.getRegistration());
        assertEquals("Maria da Silva", restored.getStudentName());
    }

    @Test
    public void sameApplicationProducesDeterministicPayload() {
        assertTrue(Arrays.equals(
                applicationCodec.encode(application()),
                applicationCodec.encode(application())
        ));
    }

    @Test
    public void sameParticipantProducesDeterministicPayload() {
        assertTrue(Arrays.equals(
                participantCodec.encode(participant()),
                participantCodec.encode(participant())
        ));
    }

    @Test
    public void differentApplicationDataProducesDifferentPayload() {
        byte[] first = applicationCodec.encode(application());
        byte[] second = applicationCodec.encode(
                new OmrAssessmentApplication(
                        "application-001",
                        "assessment-001",
                        2,
                        "class-001",
                        1_800_000_000_001L
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void differentParticipantPositionProducesDifferentPayload() {
        byte[] first = participantCodec.encode(participant());
        byte[] second = participantCodec.encode(
                new OmrAssessmentParticipant(
                        "application-001",
                        "student-001",
                        "MAT-001",
                        "Ana Souza",
                        "9º A",
                        18
                )
        );

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void applicationPayloadContainsEnvelopeAndBody() {
        assertEnvelope(
                applicationCodec.encode(application()),
                APPLICATION_MAGIC
        );
    }

    @Test
    public void participantPayloadContainsEnvelopeAndBody() {
        assertEnvelope(
                participantCodec.encode(participant()),
                PARTICIPANT_MAGIC
        );
    }

    @Test
    public void codecsRejectNullDuringEncoding() {
        expectIllegalArgument(() -> applicationCodec.encode(null));
        expectIllegalArgument(() -> participantCodec.encode(null));
    }

    @Test
    public void applicationCodecRejectsOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'A');

        OmrAssessmentApplication oversized =
                new OmrAssessmentApplication(
                        new String(characters),
                        "assessment-001",
                        1,
                        "class-001",
                        1L
                );

        expectIllegalArgument(() ->
                applicationCodec.encode(oversized)
        );
    }

    @Test
    public void participantCodecRejectsOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'P');

        OmrAssessmentParticipant oversized =
                new OmrAssessmentParticipant(
                        "application-001",
                        "student-001",
                        "MAT-001",
                        new String(characters),
                        "9º A",
                        1
                );

        expectIllegalArgument(() ->
                participantCodec.encode(oversized)
        );
    }

    @Test
    public void codecsRejectNullEmptyAndOversizedPayloads() {
        byte[] oversized = new byte[
                HEADER_BYTE_COUNT + (1024 * 1024) + 1
                ];

        expectIllegalArgument(() -> applicationCodec.decode(null));
        expectIllegalArgument(() ->
                applicationCodec.decode(new byte[0])
        );
        expectIllegalArgument(() ->
                applicationCodec.decode(oversized)
        );

        expectIllegalArgument(() -> participantCodec.decode(null));
        expectIllegalArgument(() ->
                participantCodec.decode(new byte[0])
        );
        expectIllegalArgument(() ->
                participantCodec.decode(oversized)
        );
    }

    @Test
    public void codecsRejectWrongMagic() {
        byte[] applicationPayload = validApplicationPayload();
        byte[] participantPayload = validParticipantPayload();

        ByteBuffer.wrap(applicationPayload).putInt(0, 0x12345678);
        ByteBuffer.wrap(participantPayload).putInt(0, 0x12345678);

        expectIllegalArgument(() ->
                applicationCodec.decode(applicationPayload)
        );
        expectIllegalArgument(() ->
                participantCodec.decode(participantPayload)
        );
    }

    @Test
    public void codecsRejectUnsupportedFormatVersions() {
        byte[] applicationPayload = validApplicationPayload();
        byte[] participantPayload = validParticipantPayload();

        ByteBuffer.wrap(applicationPayload).putInt(4, 99);
        ByteBuffer.wrap(participantPayload).putInt(4, 99);

        expectUnsupportedVersion(
                () -> applicationCodec.decode(applicationPayload),
                "Versão de aplicação armazenada"
        );
        expectUnsupportedVersion(
                () -> participantCodec.decode(participantPayload),
                "Versão de participante armazenada"
        );
    }

    @Test
    public void codecsRejectInvalidChecksums() {
        byte[] applicationPayload = validApplicationPayload();
        byte[] participantPayload = validParticipantPayload();

        applicationPayload[applicationPayload.length - 1] ^= 0x01;
        participantPayload[participantPayload.length - 1] ^= 0x01;

        expectIllegalArgument(() ->
                applicationCodec.decode(applicationPayload)
        );
        expectIllegalArgument(() ->
                participantCodec.decode(participantPayload)
        );
    }

    @Test
    public void codecsRejectTruncatedPayloads() {
        byte[] applicationPayload = validApplicationPayload();
        byte[] participantPayload = validParticipantPayload();

        expectIllegalArgument(() -> applicationCodec.decode(
                Arrays.copyOf(
                        applicationPayload,
                        applicationPayload.length - 1
                )
        ));
        expectIllegalArgument(() -> participantCodec.decode(
                Arrays.copyOf(
                        participantPayload,
                        participantPayload.length - 1
                )
        ));
    }

    @Test
    public void codecsRejectPayloadsWithTrailingBytes() {
        byte[] validApplicationBytes = validApplicationPayload();
        byte[] validParticipantBytes = validParticipantPayload();

        byte[] applicationPayload = Arrays.copyOf(
                validApplicationBytes,
                validApplicationBytes.length + 1
        );
        byte[] participantPayload = Arrays.copyOf(
                validParticipantBytes,
                validParticipantBytes.length + 1
        );

        applicationPayload[applicationPayload.length - 1] = 0x01;
        participantPayload[participantPayload.length - 1] = 0x01;

        expectIllegalArgument(() ->
                applicationCodec.decode(applicationPayload)
        );
        expectIllegalArgument(() ->
                participantCodec.decode(participantPayload)
        );
    }

    @Test
    public void codecsDoNotAcceptEachOthersPayload() {
        expectIllegalArgument(() -> applicationCodec.decode(
                validParticipantPayload()
        ));
        expectIllegalArgument(() -> participantCodec.decode(
                validApplicationPayload()
        ));
    }

    private void assertEnvelope(byte[] payload, int expectedMagic) {
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

    private byte[] validApplicationPayload() {
        return applicationCodec.encode(application());
    }

    private byte[] validParticipantPayload() {
        return participantCodec.encode(participant());
    }

    private OmrAssessmentApplication application() {
        return new OmrAssessmentApplication(
                "application-001",
                "assessment-001",
                2,
                "class-001",
                1_800_000_000_000L
        );
    }

    private OmrAssessmentParticipant participant() {
        return new OmrAssessmentParticipant(
                "application-001",
                "student-001",
                "MAT-001",
                "Ana Souza",
                "9º A",
                7
        );
    }

    private void assertCompleteApplication(
            OmrAssessmentApplication expected,
            OmrAssessmentApplication actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getAssessmentId(),
                actual.getAssessmentId()
        );
        assertEquals(
                expected.getAssessmentVersion(),
                actual.getAssessmentVersion()
        );
        assertEquals(
                expected.getClassGroupId(),
                actual.getClassGroupId()
        );
        assertEquals(
                expected.getCreatedAtEpochMillis(),
                actual.getCreatedAtEpochMillis()
        );
    }

    private void assertCompleteParticipant(
            OmrAssessmentParticipant expected,
            OmrAssessmentParticipant actual
    ) {
        assertNotNull(actual);
        assertEquals(expected, actual);
        assertEquals(
                expected.getRegistration(),
                actual.getRegistration()
        );
        assertEquals(
                expected.getStudentName(),
                actual.getStudentName()
        );
        assertEquals(
                expected.getClassName(),
                actual.getClassName()
        );
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

    private void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Era esperada uma IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            // Resultado esperado.
        }
    }
}
