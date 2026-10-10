package com.example.leitorgabaritoomr.domain.assessment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public final class OmrAssessmentAttemptTest {

    private static final long RECORDED_AT =
            1_799_100_000_000L;

    @Test
    public void preservesAndNormalizesAttemptData() {
        OmrAssessmentAttempt attempt =
                new OmrAssessmentAttempt(
                        " attempt-001 ",
                        " application-001 ",
                        " student-001 ",
                        2,
                        " history-002 ",
                        RECORDED_AT
                );

        assertEquals("attempt-001", attempt.getAttemptId());
        assertEquals(
                "application-001",
                attempt.getApplicationId()
        );
        assertEquals("student-001", attempt.getStudentId());
        assertEquals(2, attempt.getRevision());
        assertEquals(
                "history-002",
                attempt.getHistoryRecordId()
        );
        assertEquals(
                RECORDED_AT,
                attempt.getRecordedAtEpochMillis()
        );
    }

    @Test
    public void identityUsesAttemptIdOnly() {
        OmrAssessmentAttempt original = attempt();

        OmrAssessmentAttempt changedReference =
                new OmrAssessmentAttempt(
                        "attempt-001",
                        "application-999",
                        "student-999",
                        9,
                        "history-999",
                        RECORDED_AT + 1L
                );

        assertEquals(original, changedReference);
        assertEquals(
                original.hashCode(),
                changedReference.hashCode()
        );
    }

    @Test
    public void differentAttemptIdMeansDifferentIdentity() {
        OmrAssessmentAttempt other =
                new OmrAssessmentAttempt(
                        "attempt-002",
                        "application-001",
                        "student-001",
                        1,
                        "history-001",
                        RECORDED_AT
                );

        assertNotEquals(attempt(), other);
    }

    @Test
    public void identifiesItsOwnAttemptId() {
        OmrAssessmentAttempt attempt = attempt();

        assertTrue(attempt.hasAttemptId(" attempt-001 "));
        assertFalse(attempt.hasAttemptId("attempt-002"));
        assertFalse(attempt.hasAttemptId(null));
    }

    @Test
    public void identifiesItsApplication() {
        OmrAssessmentAttempt attempt = attempt();

        assertTrue(attempt.belongsToApplication(
                " application-001 "
        ));
        assertFalse(attempt.belongsToApplication(
                "application-002"
        ));
        assertFalse(attempt.belongsToApplication(null));
    }

    @Test
    public void identifiesItsStudent() {
        OmrAssessmentAttempt attempt = attempt();

        assertTrue(attempt.belongsToStudent(
                " student-001 "
        ));
        assertFalse(attempt.belongsToStudent(
                "student-002"
        ));
        assertFalse(attempt.belongsToStudent(null));
    }

    @Test
    public void identifiesItsParticipant() {
        OmrAssessmentAttempt attempt = attempt();

        assertTrue(attempt.belongsToParticipant(
                "application-001",
                "student-001"
        ));
        assertFalse(attempt.belongsToParticipant(
                "application-002",
                "student-001"
        ));
        assertFalse(attempt.belongsToParticipant(
                "application-001",
                "student-002"
        ));
    }

    @Test
    public void identifiesReferencedHistoryRecord() {
        OmrAssessmentAttempt attempt = attempt();

        assertTrue(attempt.referencesHistoryRecord(
                " history-001 "
        ));
        assertFalse(attempt.referencesHistoryRecord(
                "history-002"
        ));
        assertFalse(attempt.referencesHistoryRecord(null));
    }

    @Test
    public void rejectsInvalidAttemptId() {
        expectInvalidField(null, "application-001",
                "student-001", 1, "history-001", RECORDED_AT);
        expectInvalidField(" ", "application-001",
                "student-001", 1, "history-001", RECORDED_AT);
    }

    @Test
    public void rejectsInvalidApplicationId() {
        expectInvalidField("attempt-001", null,
                "student-001", 1, "history-001", RECORDED_AT);
        expectInvalidField("attempt-001", "",
                "student-001", 1, "history-001", RECORDED_AT);
    }

    @Test
    public void rejectsInvalidStudentId() {
        expectInvalidField("attempt-001", "application-001",
                null, 1, "history-001", RECORDED_AT);
        expectInvalidField("attempt-001", "application-001",
                " ", 1, "history-001", RECORDED_AT);
    }

    @Test
    public void rejectsNonPositiveRevision() {
        expectInvalidField("attempt-001", "application-001",
                "student-001", 0, "history-001", RECORDED_AT);
        expectInvalidField("attempt-001", "application-001",
                "student-001", -1, "history-001", RECORDED_AT);
    }

    @Test
    public void rejectsInvalidHistoryRecordId() {
        expectInvalidField("attempt-001", "application-001",
                "student-001", 1, null, RECORDED_AT);
        expectInvalidField("attempt-001", "application-001",
                "student-001", 1, " ", RECORDED_AT);
    }

    @Test
    public void rejectsNonPositiveRecordedAt() {
        expectInvalidField("attempt-001", "application-001",
                "student-001", 1, "history-001", 0L);
        expectInvalidField("attempt-001", "application-001",
                "student-001", 1, "history-001", -1L);
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrAssessmentAttempt original = attempt();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrAssessmentAttempt restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrAssessmentAttempt)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getApplicationId(),
                restored.getApplicationId()
        );
        assertEquals(
                original.getStudentId(),
                restored.getStudentId()
        );
        assertEquals(
                original.getRevision(),
                restored.getRevision()
        );
        assertEquals(
                original.getHistoryRecordId(),
                restored.getHistoryRecordId()
        );
        assertEquals(
                original.getRecordedAtEpochMillis(),
                restored.getRecordedAtEpochMillis()
        );
    }

    private OmrAssessmentAttempt attempt() {
        return new OmrAssessmentAttempt(
                "attempt-001",
                "application-001",
                "student-001",
                1,
                "history-001",
                RECORDED_AT
        );
    }

    private void expectInvalidField(
            String attemptId,
            String applicationId,
            String studentId,
            int revision,
            String historyRecordId,
            long recordedAtEpochMillis
    ) {
        expectIllegalArgument(() ->
                new OmrAssessmentAttempt(
                        attemptId,
                        applicationId,
                        studentId,
                        revision,
                        historyRecordId,
                        recordedAtEpochMillis
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
