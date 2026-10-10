package com.example.leitorgabaritoomr.domain.student;

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

public final class OmrStudentProfileTest {

    @Test
    public void preservesAndNormalizesStudentData() {
        OmrStudentProfile student = new OmrStudentProfile(
                " student-001 ",
                " 0007-a ",
                " Ana Beatriz "
        );

        assertEquals("student-001", student.getStudentId());
        assertEquals("0007-A", student.getRegistration());
        assertEquals("Ana Beatriz", student.getName());
    }

    @Test
    public void registrationPreservesLeadingZerosAndSymbols() {
        OmrStudentProfile student = new OmrStudentProfile(
                "student-001",
                "0007-a/26",
                "Ana Beatriz"
        );

        assertEquals("0007-A/26", student.getRegistration());
    }

    @Test
    public void identityUsesOnlyStableStudentId() {
        OmrStudentProfile original = student();

        OmrStudentProfile changedData =
                new OmrStudentProfile(
                        "student-001",
                        "999999",
                        "Ana B. Costa"
                );

        assertEquals(original, changedData);
        assertEquals(
                original.hashCode(),
                changedData.hashCode()
        );
    }

    @Test
    public void differentStudentIdMeansDifferentIdentity() {
        OmrStudentProfile other = new OmrStudentProfile(
                "student-002",
                "000123",
                "Ana Beatriz"
        );

        assertNotEquals(student(), other);
    }

    @Test
    public void findsStudentByNormalizedId() {
        OmrStudentProfile student = student();

        assertTrue(student.hasStudentId(" student-001 "));
        assertFalse(student.hasStudentId("student-002"));
        assertFalse(student.hasStudentId(null));
    }

    @Test
    public void findsRegistrationIgnoringExternalSpacesAndCase() {
        OmrStudentProfile student = new OmrStudentProfile(
                "student-001",
                "0007-A",
                "Ana Beatriz"
        );

        assertTrue(student.hasRegistration(" 0007-a "));
        assertFalse(student.hasRegistration("0008-A"));
        assertFalse(student.hasRegistration(" "));
        assertFalse(student.hasRegistration(null));
    }

    @Test
    public void createsHistoricalSnapshotForClass() {
        OmrStudentProfile student = student();

        OmrStudentIdentity snapshot =
                student.snapshotForClass(" 9º A ");

        assertEquals(
                student.getStudentId(),
                snapshot.getStudentId()
        );
        assertEquals(
                student.getRegistration(),
                snapshot.getRegistration()
        );
        assertEquals(student.getName(), snapshot.getName());
        assertEquals("9º A", snapshot.getClassName());
    }

    @Test
    public void rejectsInvalidStudentId() {
        expectIllegalArgument(() -> new OmrStudentProfile(
                null,
                "000123",
                "Ana Beatriz"
        ));

        expectIllegalArgument(() -> new OmrStudentProfile(
                " ",
                "000123",
                "Ana Beatriz"
        ));
    }

    @Test
    public void rejectsInvalidRegistration() {
        expectIllegalArgument(() -> new OmrStudentProfile(
                "student-001",
                null,
                "Ana Beatriz"
        ));

        expectIllegalArgument(() -> new OmrStudentProfile(
                "student-001",
                "",
                "Ana Beatriz"
        ));
    }

    @Test
    public void rejectsInvalidName() {
        expectIllegalArgument(() -> new OmrStudentProfile(
                "student-001",
                "000123",
                null
        ));

        expectIllegalArgument(() -> new OmrStudentProfile(
                "student-001",
                "000123",
                " "
        ));
    }

    @Test
    public void rejectsInvalidClassWhenCreatingSnapshot() {
        OmrStudentProfile student = student();

        expectIllegalArgument(() ->
                student.snapshotForClass(null)
        );
        expectIllegalArgument(() ->
                student.snapshotForClass(" ")
        );
    }

    @Test
    public void remainsEquivalentAfterSerialization()
            throws Exception {

        OmrStudentProfile original = student();

        ByteArrayOutputStream byteOutput =
                new ByteArrayOutputStream();

        try (ObjectOutputStream output =
                     new ObjectOutputStream(byteOutput)) {

            output.writeObject(original);
        }

        OmrStudentProfile restored;

        try (ObjectInputStream input =
                     new ObjectInputStream(
                             new ByteArrayInputStream(
                                     byteOutput.toByteArray()
                             )
                     )) {

            restored = (OmrStudentProfile)
                    input.readObject();
        }

        assertEquals(original, restored);
        assertEquals(
                original.getRegistration(),
                restored.getRegistration()
        );
        assertEquals(original.getName(), restored.getName());
    }

    private OmrStudentProfile student() {
        return new OmrStudentProfile(
                "student-001",
                "000123",
                "Ana Beatriz"
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
