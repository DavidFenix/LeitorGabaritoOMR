package com.example.leitorgabaritoomr.domain.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.leitorgabaritoomr.domain.student.OmrStudentIdentity;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class OmrAssessmentParticipantTest {

    @Test
    public void preservesAndNormalizesParticipantData() {
        OmrAssessmentParticipant participant =
                new OmrAssessmentParticipant(
                        " application-001 ",
                        " student-001 ",
                        " 0007-a ",
                        " Ana Beatriz ",
                        " 9º A ",
                        7
                );

        assertEquals(
                "application-001",
                participant.getApplicationId()
        );
        assertEquals(
                "student-001",
                participant.getStudentId()
        );
        assertEquals("0007-A", participant.getRegistration());
        assertEquals(
                "Ana Beatriz",
                participant.getStudentName()
        );
        assertEquals("9º A", participant.getClassName());
        assertEquals(7, participant.getRosterPosition());
    }

    @Test
    public void identityUsesApplicationAndStudentOnly() {
        OmrAssessmentParticipant original = participant();

        OmrAssessmentParticipant changedSnapshot =
                new OmrAssessmentParticipant(
                        "application-001",
                        "student-001",
                        "999999",
                        "Nome atualizado",
                        "9º B",
                        19
                );

        assertEquals(original, changedSnapshot);
        assertEquals(
                original.hashCode(),
                changedSnapshot.hashCode()
        );
    }

    @Test
    public void differentApplicationMeansDifferentIdentity() {
        OmrAssessmentParticipant other =
                new OmrAssessmentParticipant(
                        "application-002",
                        "student-001",
                        "000123",
                        "Ana Beatriz",
                        "9º A",
                        7
                );

        assertNotEquals(participant(), other);
    }

    @Test
    public void differentStudentMeansDifferentIdentity() {
        OmrAssessmentParticipant other =
                new OmrAssessmentParticipant(
                        "application-001",
                        "student-002",
                        "000123",
                        "Ana Beatriz",
                        "9º A",
                        7
                );

        assertNotEquals(participant(), other);
    }

    @Test
    public void identifiesItsApplication() {
        OmrAssessmentParticipant participant = participant();

        assertTrue(participant.belongsToApplication(
                " application-001 "
        ));
        assertFalse(participant.belongsToApplication(
                "application-002"
        ));
        assertFalse(participant.belongsToApplication(null));
    }

    @Test
    public void identifiesItsStudent() {
        OmrAssessmentParticipant participant = participant();

        assertTrue(participant.belongsToStudent(
                " student-001 "
        ));
        assertFalse(participant.belongsToStudent(
                "student-002"
        ));
        assertFalse(participant.belongsToStudent(null));
    }

    @Test
    public void createsExistingHistoricalStudentIdentity() {
        OmrAssessmentParticipant participant = participant();

        OmrStudentIdentity identity =
                participant.toStudentIdentity();

        assertEquals(
                participant.getStudentId(),
                identity.getStudentId()
        );
        assertEquals(
                participant.getRegistration(),
                identity.getRegistration()
        );
        assertEquals(
                participant.getStudentName(),
                identity.getName()
        );
        assertEquals(
                participant.getClassName(),
                identity.getClassName()
        );
    }

    @Test
    public void rejectsInvalidApplicationId() {
        expectInvalidField(null, "student-001", "000123",
                "Ana Beatriz", "9º A", 7);
        expectInvalidField(" ", "student-001", "000123",
                "Ana Beatriz", "9º A", 7);
    }

    @Test
    public void rejectsInvalidStudentId() {
        expectInvalidField("application-001", null, "000123",
                "Ana Beatriz", "9º A", 7);
        expectInvalidField("application-001", "", "000123",
                "Ana Beatriz", "9º A", 7);
    }

    @Test
    public void rejectsInvalidRegistration() {
        expectInvalidField("application-001", "student-001", null,
                "Ana Beatriz", "9º A", 7);
        expectInvalidField("application-001", "student-001", " ",
                "Ana Beatriz", "9º A", 7);
    }

    @Test
    public void rejectsInvalidStudentName() {
        expectInvalidField("application-001", "student-001", "000123",
                null, "9º A", 7);
        expectInvalidField("application-001", "student-001", "000123",
                "", "9º A", 7);
    }

    @Test
    public void rejectsInvalidClassName() {
        expectInvalidField("application-001", "student-001", "000123",
                "Ana Beatriz", null, 7);
        expectInvalidField("application-001", "student-001", "000123",
                "Ana Beatriz", " ", 7);
    }

    @Test
    public void rejectsNonPositiveRosterPosition() {
        expectInvalidField("application-001", "student-001", "000123",
                "Ana Beatriz", "9º A", 0);
        expectInvalidField("application-001", "student-001", "000123",
                "Ana Beatriz", "9º A", -1);
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrAssessmentParticipant original = participant();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrAssessmentParticipant restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrAssessmentParticipant)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getRegistration(),
                restored.getRegistration()
        );
        assertEquals(
                original.getStudentName(),
                restored.getStudentName()
        );
        assertEquals(
                original.getClassName(),
                restored.getClassName()
        );
        assertEquals(
                original.getRosterPosition(),
                restored.getRosterPosition()
        );
    }

    private OmrAssessmentParticipant participant() {
        return new OmrAssessmentParticipant(
                "application-001",
                "student-001",
                "000123",
                "Ana Beatriz",
                "9º A",
                7
        );
    }

    private void expectInvalidField(
            String applicationId,
            String studentId,
            String registration,
            String studentName,
            String className,
            int rosterPosition
    ) {
        expectIllegalArgument(() ->
                new OmrAssessmentParticipant(
                        applicationId,
                        studentId,
                        registration,
                        studentName,
                        className,
                        rosterPosition
                )
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
