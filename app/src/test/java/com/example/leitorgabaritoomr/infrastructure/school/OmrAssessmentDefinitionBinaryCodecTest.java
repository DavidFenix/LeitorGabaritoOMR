package com.example.leitorgabaritoomr.infrastructure.school;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.assessment.OmrAssessmentDefinition;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class OmrAssessmentDefinitionBinaryCodecTest {

    private static final int MAGIC = 0x4F4D5241;
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_BYTE_COUNT = 20;

    private final OmrAssessmentDefinitionBinaryCodec codec =
            new OmrAssessmentDefinitionBinaryCodec();

    @Test
    public void roundTripPreservesCompleteAssessment() {
        OmrAssessmentDefinition original = assessment(
                "assessment-001",
                3,
                "Avaliação de Matemática – Frações",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                4
        );

        OmrAssessmentDefinition restored =
                codec.decode(codec.encode(original));

        assertCompleteAssessment(original, restored);
    }

    @Test
    public void sameAssessmentProducesDeterministicPayload() {
        OmrAssessmentDefinition assessment = assessment();

        assertTrue(Arrays.equals(
                codec.encode(assessment),
                codec.encode(assessment)
        ));
    }

    @Test
    public void differentVersionsProduceDifferentPayloads() {
        byte[] first = codec.encode(assessment());

        byte[] second = codec.encode(assessment(
                "assessment-001",
                2,
                "Prova de Matemática",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                3
        ));

        assertFalse(Arrays.equals(first, second));
    }

    @Test
    public void encodedPayloadContainsEnvelopeAndBody() {
        byte[] payload = codec.encode(assessment());
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
    public void rejectsNullAssessmentDuringEncoding() {
        expectIllegalArgument(() -> codec.encode(null));
    }

    @Test
    public void rejectsAssessmentWithOversizedText() {
        char[] characters = new char[(64 * 1024) + 1];
        Arrays.fill(characters, 'A');

        OmrAssessmentDefinition oversized = assessment(
                "assessment-001",
                1,
                new String(characters),
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                1
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
                    "Versão de prova armazenada"
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
        return codec.encode(assessment());
    }

    private OmrAssessmentDefinition assessment() {
        return assessment(
                "assessment-001",
                1,
                "Prova de Matemática",
                "Matemática",
                "9º ano",
                "2º bimestre",
                2026,
                "answer-key-001",
                2
        );
    }

    private OmrAssessmentDefinition assessment(
            String assessmentId,
            int version,
            String title,
            String subject,
            String gradeLevel,
            String academicPeriod,
            int schoolYear,
            String answerKeyId,
            int answerKeyVersion
    ) {
        return new OmrAssessmentDefinition(
                assessmentId,
                version,
                title,
                subject,
                gradeLevel,
                academicPeriod,
                schoolYear,
                answerKeyId,
                answerKeyVersion
        );
    }

    private void assertCompleteAssessment(
            OmrAssessmentDefinition expected,
            OmrAssessmentDefinition actual
    ) {
        assertEquals(expected, actual);
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getSubject(), actual.getSubject());
        assertEquals(
                expected.getGradeLevel(),
                actual.getGradeLevel()
        );
        assertEquals(
                expected.getAcademicPeriod(),
                actual.getAcademicPeriod()
        );
        assertEquals(
                expected.getSchoolYear(),
                actual.getSchoolYear()
        );
        assertEquals(
                expected.getAnswerKeyId(),
                actual.getAnswerKeyId()
        );
        assertEquals(
                expected.getAnswerKeyVersion(),
                actual.getAnswerKeyVersion()
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
